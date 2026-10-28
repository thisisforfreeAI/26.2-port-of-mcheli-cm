package io.github.mcheliceport.wingman.handler;

import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.content.weapon.WeaponInfo;
import io.github.mcheliceport.entity.NpcBattles;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.ground.GroundVehicleEntity;
import io.github.mcheliceport.entity.gunner.GunnerEntity;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.entity.plane.PlaneEntity;
import io.github.mcheliceport.weapon.WeaponBase;
import io.github.mcheliceport.weapon.WeaponParam;
import io.github.mcheliceport.weapon.WeaponSet;
import io.github.mcheliceport.weather.FlightWeather;
import io.github.mcheliceport.wingman.ai.AiSkill;
import io.github.mcheliceport.wingman.ai.FireControl;
import io.github.mcheliceport.wingman.ai.PilotAi;
import io.github.mcheliceport.wingman.ai.TerrainAvoidance;
import io.github.mcheliceport.wingman.config.WingmanConfig;
import io.github.mcheliceport.wingman.mission.AutonomousState;
import io.github.mcheliceport.wingman.wingman.WingmanEntry;
import io.github.mcheliceport.wingman.wingman.WingmanRegistry;
import io.github.mcheliceport.wingman.wingman.WingmanState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.jspecify.annotations.Nullable;

/**
 * Upstream's {@code WingmanTickHandler}: each server level tick steers the level's wingmen, as
 * upstream, by setting throttle, yaw, pitch and roll within rate limits before the aircraft move
 * ({@link LevelTickEvent.Pre}) and lets the aircraft's own flight model fly them, towards their
 * formation slot, their attack path or the target their mission sets. After they move
 * ({@link LevelTickEvent.Post}) it fires, pushes a wingman that has fallen far behind its slot,
 * moves a taxiing aircraft along the ground and sets the landing gear. {@link AutonomousFlightHandler}
 * runs right after it in both phases, as upstream registered it.
 */
public final class WingmanTickHandler {

    private static final double ARRIVAL_THRESHOLD = 5.0;
    /** Within this distance of its slot a wingman waits while the leader stands still. */
    private static final double HOLD_THRESHOLD = 25.0;
    /** The leader counts as stopped below this speed (blocks per tick). */
    private static final double LEADER_STOP_SPEED = 0.4;
    /** Upstream raised this from 200 to reach targets from its 300-block orbits. */
    private static final double AUTO_ATTACK_RANGE = 400.0;
    /** After a heavy weapon fires, the wingman flies straight on for this many ticks. */
    private static final int POST_FIRE_ESCAPE_TICKS = 40;

    private static final float MAX_YAW_RATE_PLANE = 3.0F;
    private static final float MAX_YAW_RATE_HELI = 4.0F;
    private static final float MAX_PITCH_RATE = 3.0F;
    private static final float MAX_ROLL_RATE = 5.0F;
    private static final float MAX_PITCH_UP = 25.0F;
    private static final float MAX_PITCH_DOWN = -20.0F;
    /** How far a plane noses down on a gun or rocket pass (pitch is positive down). */
    private static final float MAX_DIVE = 35.0F;
    /** A plane banks this many degrees for each degree a tick it turns, at most {@link #MAX_BANK}. */
    private static final float BANK_PER_YAW = 7.0F;
    private static final float MAX_BANK = 50.0F;
    /** Each tick an AI plane's sideways drift is turned this far towards its nose. */
    private static final double SLIP_DAMPING = 0.35;
    /** A plane whose leader has stopped circles it at this radius, this high over it, at about this throttle. */
    private static final double CIRCLE_RADIUS = 60.0;
    private static final double CIRCLE_HEIGHT = 20.0;
    private static final double CIRCLE_THROTTLE = 0.7;
    /** A pilot chooses its weapon again after this many ticks. */
    private static final int WEAPON_HOLD_TICKS = 40;
    /** A missile is fired only at a target within this many degrees of its mount and this far away. */
    private static final double MISSILE_CONE = 45.0;
    private static final double MISSILE_MIN_RANGE = 25.0;

    /** From this distance to the slot the wingman flies at full throttle. */
    private static final double THROTTLE_BLEND_FAR = 80.0;
    /** Within this distance it matches the leader's throttle. */
    private static final double THROTTLE_BLEND_NEAR = 12.0;
    /** The slot boost reaches this multiple of the leader's speed. */
    private static final double BOOST_LEADER_MULT = 2.0;
    private static final double BOOST_DIST_ON = 80.0;
    private static final double BOOST_DIST_OFF = 20.0;

    private static final Map<UUID, Integer> FIRE_COOLDOWNS = new HashMap<>();
    private static final Map<UUID, Integer> POST_FIRE_ESCAPE = new HashMap<>();
    private static final Set<UUID> ENEMY_LOGGED = new HashSet<>();
    private static int boostDebugTick;

    private WingmanTickHandler() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(WingmanTickHandler::onLevelTickPre);
        NeoForge.EVENT_BUS.addListener(WingmanTickHandler::onLevelTickPost);
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) ->
                WingmanConfig.loadFromServerConfig());
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> {
            WingmanRegistry.clear();
            MissionChunkLoader.clear();
            FIRE_COOLDOWNS.clear();
            POST_FIRE_ESCAPE.clear();
            ENEMY_LOGGED.clear();
            WingmanConfig.reset();
        });
    }

    // ---- before the aircraft move ----

    private static void onLevelTickPre(LevelTickEvent.Pre event) {
        if (event.getLevel() instanceof ServerLevel level) {
            MissionChunkLoader.tick(level);
            tickStart(level);
            AutonomousFlightHandler.tickStart(level);
        }
    }

    /** Upstream {@code onWorldTickStart}: engine and attitude for each wingman in the level. */
    public static void tickStart(ServerLevel level) {
        for (Map.Entry<UUID, WingmanEntry> e : WingmanRegistry.snapshot().entrySet()) {
            if (!(level.getEntity(e.getKey()) instanceof AircraftEntity wingman) || wingman.isRemoved()) {
                continue;
            }
            WingmanEntry entry = e.getValue();
            PilotAi.tick(wingman, level.getGameTime());
            if (PilotAi.isCrippled(wingman)) {
                breakOff(level, wingman, entry);
            }
            if (wingman instanceof PlaneEntity plane && plane.partNozzle != null) {
                controlVtol(plane, entry);
            }
            if (entry.attackMode != WingmanEntry.ATK_NONE) {
                updatePass(level, wingman, entry);
                Vec3 movTarget = wingman instanceof HeliEntity ? computeMoveTarget(level, wingman, entry) : null;
                applyThrottle(wingman, movTarget != null ? heliAltitudeThrottle(wingman, movTarget.y) : 1.0);
                steerToTarget(level, wingman, entry);
            } else if (entry.isAutonomous()) {
                PilotAi.endPass(wingman);
                tickAutonomousStart(level, wingman, entry);
            } else if (entry.leader != null) {
                PilotAi.endPass(wingman);
                Vec3 fp = formationPos(entry.leader, entry.formationSlot);
                double distToSlot = fp.distanceTo(wingman.position());
                if (circlesLeader(level, wingman, entry)) {
                    // More throttle below the circle's height, as a pilot holds it.
                    double below = entry.leader.getY() + CIRCLE_HEIGHT - wingman.getY();
                    applyThrottle(wingman, Mth.clamp(CIRCLE_THROTTLE + below * 0.05, 0.5, 1.0));
                    steerToTarget(level, wingman, entry);
                } else if (isLeaderStopped(entry.leader) && distToSlot < HOLD_THRESHOLD) {
                    holdStop(wingman, entry.leader);
                } else {
                    if (wingman instanceof HeliEntity) {
                        Vec3 mt = computeMoveTarget(level, wingman, entry);
                        applyThrottle(wingman, heliAltitudeThrottle(wingman, mt != null ? mt.y : fp.y));
                    } else {
                        maintainEngineAdaptive(wingman, entry.leader, distToSlot);
                    }
                    steerToTarget(level, wingman, entry);
                }
            }
            if (PilotAi.isPullingUp(wingman) && !(wingman instanceof HeliEntity)) {
                applyThrottle(wingman, 1.0);
            }
        }
        if (level.getGameTime() % 200 == 0) {
            PilotAi.retain(WingmanRegistry.snapshot().keySet());
        }
    }

    /**
     * M10d: a wingman with an engine or a rotor disabled stops attacking and goes back to its
     * leader; on an order, it flies home.
     */
    private static void breakOff(ServerLevel level, AircraftEntity wingman, WingmanEntry entry) {
        if (entry.attackMode != WingmanEntry.ATK_NONE) {
            entry.attackMode = WingmanEntry.ATK_NONE;
            entry.manualTargetId = null;
            entry.currentAutoTarget = null;
            McheliCePort.LOGGER.info("[Wingman] {} breaks off - damaged", AutonomousFlightHandler.shortId(wingman));
        }
        if (entry.order != null && (entry.autoState == AutonomousState.TRANSIT_TO
                || entry.autoState == AutonomousState.ON_STATION || entry.autoState == AutonomousState.STRIKE_PASS)) {
            AutonomousFlightHandler.triggerRtb(level, wingman, entry, "damaged");
        }
    }

    /**
     * Upstream's VTOL switch: the plane's nozzles go down when its mission wants hover mode and
     * forward again after, switched once per change so the toggle does not flip back and forth.
     */
    private static void controlVtol(PlaneEntity plane, WingmanEntry entry) {
        entry.vtolNozzleMode = plane.getVtolMode();
        boolean needsVtol = entry.isAutonomous() && entry.vtolHoverMode;
        if (needsVtol && !entry.vtolOnSent) {
            if (plane.getVtolMode() == 0) {
                plane.switchVtol(true);
                McheliCePort.LOGGER.info("[VTOL] {} VTOL=ON (hover mode)", AutonomousFlightHandler.shortId(plane));
            }
            entry.vtolOnSent = true;
        } else if (!needsVtol && entry.vtolOnSent) {
            if (plane.getVtolMode() != 0) {
                plane.switchVtol(false);
                McheliCePort.LOGGER.info("[VTOL] {} VTOL=OFF (fixed-wing mode)", AutonomousFlightHandler.shortId(plane));
            }
            entry.vtolOnSent = false;
        }
    }

    /**
     * Upstream's autonomous branch of {@code onWorldTickStart}: lining up holds the aircraft still
     * and turns it; taxiing aims the motion at the next marker and records where it started, for
     * the push after it moves; take-off, climb and landing steer while the mission sets the throttle,
     * level in hover mode; parked or starting it holds still; otherwise it steers towards the target
     * at full throttle, half within 20 blocks.
     */
    private static void tickAutonomousStart(ServerLevel level, AircraftEntity wingman, WingmanEntry entry) {
        if (entry.autoState == AutonomousState.ALIGN) {
            applyThrottle(wingman, 0.0);
            wingman.setDeltaMovement(Vec3.ZERO);
            steerToTarget(level, wingman, entry);
            wingman.setPitch(0.0F);
            wingman.setRoll(0.0F);
        } else if (isTaxiingState(entry.autoState)) {
            applyThrottle(wingman, 0.0);
            entry.taxiPreX = wingman.getX();
            entry.taxiPreZ = wingman.getZ();
            double tdx = entry.autoTargetX - wingman.getX();
            double tdz = entry.autoTargetZ - wingman.getZ();
            double tDist = Math.sqrt(tdx * tdx + tdz * tdz);
            if (tDist > 0.5) {
                wingman.setDeltaMovement(tdx / tDist * TAXI_SPEED, 0, tdz / tDist * TAXI_SPEED);
                wingman.setYaw((float) Math.toDegrees(Math.atan2(-tdx, tdz)));
            } else {
                wingman.setDeltaMovement(Vec3.ZERO);
            }
            wingman.setPitch(0.0F);
            wingman.setRoll(0.0F);
        } else if (isAfhThrottleState(entry.autoState)) {
            steerToTarget(level, wingman, entry);
            if (entry.autoState == AutonomousState.TAKEOFF_ROLL || entry.autoState == AutonomousState.CLIMB) {
                applyThrottle(wingman, 1.0);
            }
            if (entry.vtolHoverMode) {
                wingman.setPitch(0.0F);
            }
        } else if (entry.autoState == AutonomousState.NONE || entry.autoState == AutonomousState.PARKED) {
            applyThrottle(wingman, 0.0);
            wingman.setDeltaMovement(Vec3.ZERO);
        } else {
            if (wingman instanceof HeliEntity) {
                Vec3 mt = computeMoveTarget(level, wingman, entry);
                applyThrottle(wingman, heliAltitudeThrottle(wingman, mt != null ? mt.y : entry.autoTargetY));
            } else {
                double dist = Math.sqrt(Math.pow(entry.autoTargetX - wingman.getX(), 2)
                        + Math.pow(entry.autoTargetY - wingman.getY(), 2) + Math.pow(entry.autoTargetZ - wingman.getZ(), 2));
                applyThrottle(wingman, dist > 20 ? 1.0 : 0.5);
            }
            steerToTarget(level, wingman, entry);
        }
    }

    /**
     * Upstream {@code steerToTarget}: turns towards the move target (or, attacking, the aim target)
     * by at most the rate limits. Following, the wingman blends towards the leader's heading and
     * roll as it nears its slot.
     */
    private static void steerToTarget(ServerLevel level, AircraftEntity wingman, WingmanEntry entry) {
        UUID wid = wingman.getUUID();
        int escTicks = POST_FIRE_ESCAPE.getOrDefault(wid, 0);
        if (escTicks > 0) {
            POST_FIRE_ESCAPE.put(wid, escTicks - 1);
            return;
        }
        Vec3 movTarget = computeMoveTarget(level, wingman, entry);
        Vec3 aimTarget = PilotAi.isBreaking(wingman) ? null : computeAimTarget(level, wingman, entry);
        Vec3 aimRef = aimTarget != null ? aimTarget : movTarget;

        boolean heli = wingman instanceof HeliEntity;
        // A tank's pitch and roll follow the ground; it aims with its turret, through lastRiderPitch.
        boolean ground = wingman instanceof GroundVehicleEntity;
        boolean inAttack = entry.attackMode != WingmanEntry.ATK_NONE;
        float maxYawRate = inAttack ? (heli ? 8.0F : 5.0F) : (heli ? MAX_YAW_RATE_HELI : MAX_YAW_RATE_PLANE);
        float maxPitchRate = inAttack ? 5.0F : MAX_PITCH_RATE;
        float currentYaw = wingman.getYaw();
        float currentPitch = wingman.getPitch();

        if (aimRef != null) {
            double dx = aimRef.x - wingman.getX();
            double dy = aimRef.y - wingman.getY();
            double dz = aimRef.z - wingman.getZ();
            double hDist = Math.sqrt(dx * dx + dz * dz);
            // Within a block horizontally the heading is undefined, so it is left alone.
            float yawStep = 0.0F;
            if (hDist >= 1.0) {
                float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                if (!inAttack && !entry.isAutonomous() && entry.leader != null) {
                    float alpha = (float) Math.min(1.0, hDist / 30.0);
                    float leaderYaw = entry.leader.getYRot();
                    targetYaw = leaderYaw + Mth.wrapDegrees(targetYaw - leaderYaw) * alpha;
                }
                yawStep = Mth.clamp(Mth.wrapDegrees(targetYaw - currentYaw), -maxYawRate, maxYawRate);
                wingman.setYaw(currentYaw + yawStep);
                wingman.lastRiderYaw = targetYaw;
            }
            boolean banks = !heli && !ground && flies(wingman, entry) && !wingman.onGround();
            if (!ground && !inAttack && !entry.isAutonomous() && entry.leader != null) {
                float leaderRoll = entry.leader instanceof AircraftEntity la ? la.getRoll() : 0.0F;
                float targetRoll = leaderRoll;
                // A plane away from its slot banks into its own turn, and takes the leader's roll as it closes.
                if (banks) {
                    float bank = Mth.clamp(yawStep * BANK_PER_YAW, -MAX_BANK, MAX_BANK);
                    targetRoll = leaderRoll + (bank - leaderRoll) * (float) Math.min(1.0, hDist / 30.0);
                }
                float currentRoll = wingman.getRoll();
                float rollStep = Mth.clamp(Mth.wrapDegrees(targetRoll - currentRoll), -MAX_ROLL_RATE, MAX_ROLL_RATE);
                wingman.setRoll(currentRoll + rollStep);
            } else if (banks || entry.isAutonomous() && !heli && !ground && !entry.vtolHoverMode
                    && entry.autoState != AutonomousState.TAKEOFF_ROLL && entry.autoState != AutonomousState.ALIGN) {
                // A plane in the air banks into its turns, 7 degrees for each degree a tick of turn,
                // attacking as well as on a mission, so it does not skid round flat.
                float targetRoll = Mth.clamp(yawStep * BANK_PER_YAW, -MAX_BANK, MAX_BANK);
                float curRoll = wingman.getRoll();
                wingman.setRoll(curRoll + Mth.clamp(Mth.wrapDegrees(targetRoll - curRoll), -MAX_ROLL_RATE, MAX_ROLL_RATE));
            }
            // Fixed guns aim with the nose; other weapons keep the aircraft level and aim through lastRiderPitch.
            // Pulling off a pass, a plane flies to its move target instead.
            boolean fixedGun = inAttack && aimTarget != null && isFixedGunWeapon(attackType(entry));
            float targetPitch;
            if (ground) {
                targetPitch = currentPitch;
            } else if (fixedGun && !heli && PilotAi.isPullingUp(wingman)) {
                // Low over the ground ahead, a diving plane pulls up before it fires.
                targetPitch = MAX_PITCH_DOWN;
            } else if (fixedGun) {
                targetPitch = (float) -Math.toDegrees(Math.atan2(dy, Math.max(hDist, 1.0)));
                targetPitch = Mth.clamp(targetPitch, MAX_PITCH_DOWN, heli ? MAX_PITCH_UP : MAX_DIVE);
            } else {
                double movDy = movTarget != null ? movTarget.y - wingman.getY() : dy;
                if (heli) {
                    // A helicopter flies forward nose down, half a degree a block from its move
                    // target, less 25 degrees a block per tick it already closes, so it slows before
                    // the target instead of overshooting; its throttle holds the height (KNOWN_ISSUES 97).
                    Vec3 to = movTarget != null ? movTarget : aimRef;
                    double tx = to.x - wingman.getX();
                    double tz = to.z - wingman.getZ();
                    double moveDist = Math.sqrt(tx * tx + tz * tz);
                    Vec3 vel = wingman.getDeltaMovement();
                    double closing = moveDist > 0.01 ? (vel.x * tx + vel.z * tz) / moveDist : 0.0;
                    targetPitch = Mth.clamp((float) (moveDist * 0.5 - closing * 25.0), -15.0F, 20.0F);
                    // Climbing away from terrain, it stops pushing its nose down into it.
                    if (PilotAi.isPullingUp(wingman)) {
                        targetPitch = Math.min(targetPitch, 0.0F);
                    }
                } else if (PilotAi.isPullingUp(wingman)) {
                    // A plane below its clearance over the ground ahead climbs as steeply as it may.
                    targetPitch = MAX_PITCH_DOWN;
                } else {
                    targetPitch = Mth.clamp((float) -Math.toDegrees(Math.atan2(movDy, Math.max(hDist, 1.0))),
                            MAX_PITCH_DOWN, MAX_PITCH_UP);
                }
            }
            float pitchStep = Mth.clamp(targetPitch - currentPitch, -maxPitchRate, maxPitchRate);
            wingman.setPitch(currentPitch + pitchStep);
            wingman.lastRiderPitch = (float) -Math.toDegrees(Math.atan2(dy, Math.max(hDist, 1.0)));
        }
        applySiblingRepulsion(level, wingman, entry, currentYaw, maxYawRate);
    }

    /**
     * Upstream {@code applySiblingRepulsion}: a wingman closer than the side distance (else the rear
     * distance) to another of the leader's wingmen turns away from it by up to half its yaw rate.
     */
    private static void applySiblingRepulsion(ServerLevel level, AircraftEntity wingman, WingmanEntry entry,
            float currentYaw, float maxYawRate) {
        double minDist = WingmanConfig.formationSideDist > 0 ? WingmanConfig.formationSideDist
                : WingmanConfig.formationRearDist;
        if (minDist <= 0) {
            return;
        }
        double repX = 0;
        double repZ = 0;
        for (Entity sibling : siblings(level, wingman.getUUID(), entry.leader)) {
            double sdx = wingman.getX() - sibling.getX();
            double sdz = wingman.getZ() - sibling.getZ();
            double dist = Math.sqrt(sdx * sdx + sdz * sdz);
            if (dist < minDist && dist > 0.01) {
                double strength = (minDist - dist) / minDist;
                repX += sdx / dist * strength;
                repZ += sdz / dist * strength;
            }
        }
        if (repX == 0 && repZ == 0) {
            return;
        }
        float repYaw = (float) Math.toDegrees(Math.atan2(-repX, repZ));
        float repStep = Mth.clamp(Mth.wrapDegrees(repYaw - currentYaw), -maxYawRate * 0.5F, maxYawRate * 0.5F);
        wingman.setYaw(currentYaw + repStep);
    }

    private static boolean isFixedGunWeapon(@Nullable String type) {
        if (type == null) {
            return false;
        }
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "gun", "machinegun", "machinegun1", "machinegun2", "cannon", "cas", "rocket", "mkrocket", "buckshot",
                    "railgun", "laser" -> true;
            default -> false;
        };
    }

    /** The weapon type the pilot attacks with: the one it chose for its target, else its order's. */
    static @Nullable String attackType(WingmanEntry entry) {
        return entry.aiWeaponType != null ? entry.aiWeaponType : entry.weaponType;
    }

    /** A fixed-wing aircraft: not a helicopter or a ground vehicle. */
    private static boolean isPlane(Entity e) {
        return !(e instanceof HeliEntity) && !(e instanceof GroundVehicleEntity);
    }

    /** Whether {@code e} is more than 4 blocks over the ground under it. */
    private static boolean airborne(ServerLevel level, Entity e) {
        return !e.onGround() && e.getY() - TerrainAvoidance.of(level).top(e.getBlockX(), e.getBlockZ()) > 4.0;
    }

    /** A plane in the air whose leader has stopped (landed or hovering) circles over it. */
    private static boolean circlesLeader(ServerLevel level, AircraftEntity wingman, WingmanEntry entry) {
        return entry.leader != null && entry.attackMode == WingmanEntry.ATK_NONE && !entry.isAutonomous()
                && entry.state == WingmanState.FOLLOWING && isPlane(wingman) && isLeaderStopped(entry.leader)
                && airborne(level, wingman);
    }

    /**
     * A port addition: a plane's gun or rocket pass at its target ({@link PilotAi#updatePass}). It
     * pulls off closer than its turn needs, or low in a dive over the ground, and comes round again
     * from further out; an air target it passes close by.
     */
    private static void updatePass(ServerLevel level, AircraftEntity wingman, WingmanEntry entry) {
        Entity target = isPlane(wingman) ? resolveTarget(level, wingman, entry) : null;
        if (target == null || target.isRemoved() || !FireControl.role(attackType(entry)).ballistic()
                || entry.order != null && entry.order.orbitAttack) {
            PilotAi.endPass(wingman);
            return;
        }
        boolean air = airborne(level, target);
        double dist = wingman.distanceTo(target);
        double speed = wingman.getDeltaMovement().length();
        double overGround = wingman.getY() - TerrainAvoidance.of(level).top(wingman.getBlockX(), wingman.getBlockZ());
        boolean tooLow = !air && wingman.getPitch() > 5.0F && overGround < 12.0 + speed * 8.0;
        double minRange = air ? 12.0 + speed * 4.0 : 25.0 + speed * 10.0;
        double reattack = air ? 60.0 : Math.max(130.0, standoffForWeapon(attackType(entry)) * 2.0);
        PilotAi.updatePass(wingman, dist, minRange, reattack, tooLow);
    }

    // ---- after the aircraft move ----

    private static void onLevelTickPost(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            tickEnd(level);
            AutonomousFlightHandler.tickEnd(level);
        }
    }

    /** Upstream {@code onWorldTickEnd}: firing, the slot boost, the landing gear and the taxi push. */
    public static void tickEnd(ServerLevel level) {
        for (Map.Entry<UUID, WingmanEntry> e : WingmanRegistry.snapshot().entrySet()) {
            UUID id = e.getKey();
            WingmanEntry entry = e.getValue();
            if (!entry.isAutonomous() && !entry.parkingHold && (entry.leader == null || entry.leader.isRemoved())) {
                WingmanRegistry.remove(id);
                McheliCePort.LOGGER.info("[Wingman] {} unregistered - leader gone", id);
                continue;
            }
            // A wingman whose leader lost its pilot stops attacking, so it does not chase targets off alone.
            if (!entry.isAutonomous() && entry.leader != null && entry.attackMode != WingmanEntry.ATK_NONE
                    && !(pilotOf(entry.leader) instanceof Player)) {
                entry.attackMode = WingmanEntry.ATK_NONE;
                entry.manualTargetId = null;
                McheliCePort.LOGGER.info("[Wingman] {} auto-hold - leader has no rider", id);
            }
            if (!(level.getEntity(id) instanceof AircraftEntity wingman) || wingman.isRemoved()) {
                continue;
            }
            entry.diagTick++;
            if (entry.attackMode != WingmanEntry.ATK_NONE && !PilotAi.isBreaking(wingman)) {
                fireAtTarget(level, wingman, entry);
            } else {
                entry.aiWeapon = -1;
                entry.aiWeaponType = null;
                entry.aiWeaponTarget = null;
            }
            if (isPlane(wingman) && flies(wingman, entry) && !wingman.onGround()) {
                dampSideslip(wingman);
            }
            if (entry.attackMode == WingmanEntry.ATK_NONE && !entry.isAutonomous() && entry.leader != null
                    && !isLeaderStopped(entry.leader)) {
                Vec3 fp = formationPos(entry.leader, entry.formationSlot);
                double fdx = fp.x - wingman.getX();
                double fdz = fp.z - wingman.getZ();
                applySlotBoost(wingman, fdx, fdz, Math.sqrt(fdx * fdx + fdz * fdz),
                        entry.leader.getDeltaMovement().length());
            }
            if (entry.leader != null && !entry.isAutonomous()) {
                syncGear(wingman, entry.leader);
            } else if (entry.isAutonomous()) {
                autoGear(wingman, entry.autoState);
            }
            if (entry.isAutonomous() && isTaxiingState(entry.autoState)) {
                taxiPushPosition(wingman, entry);
            }
        }
    }

    /**
     * Upstream's firing in {@code onWorldTickEnd}, with the port's fire control: the pilot chooses
     * the weapon that suits its target, and fires only with a solution. A gun or rocket is laid on
     * where the target will be when the round gets there, allowing for its drop, and fires once the
     * nose (or the weapon's mount) holds that point within the angle the target fills; a bomb is
     * released where its fall meets the target; a missile is launched at a target within its
     * seeker's cone. A ground vehicle keeps upstream's checks.
     */
    private static void fireAtTarget(ServerLevel level, AircraftEntity wingman, WingmanEntry entry) {
        UUID id = wingman.getUUID();
        int cd = FIRE_COOLDOWNS.getOrDefault(id, 0);
        if (cd > 0) {
            FIRE_COOLDOWNS.put(id, cd - 1);
        }
        Entity target = resolveTarget(level, wingman, entry);
        if (target == null || target.isRemoved()) {
            return;
        }
        if (entry.diagTick % 100 == 1) {
            McheliCePort.LOGGER.info("[Wingman/ATK] {} targeting: name='{}' class={} pos=({},{},{}) dist={} weapon={}",
                    wingman.getUUID().toString().substring(0, 8), target.getName().getString(),
                    target.getClass().getSimpleName(), (int) target.getX(), (int) target.getY(), (int) target.getZ(),
                    (int) wingman.distanceTo(target), attackType(entry));
        }
        if (wingman instanceof GroundVehicleEntity) {
            fireUpstream(wingman, target, entry, cd);
            return;
        }
        chooseWeapon(level, wingman, entry, target);
        Aim aim = aim(level, wingman, entry, target, true);
        if (aim != null && aim.fire() && cd <= 0) {
            tryFire(wingman, entry, target, aim.yaw(), aim.pitch(), attackType(entry), true);
        }
    }

    /**
     * Upstream's checks, for a ground vehicle: within the weapon's range a bomb drops within 80
     * blocks horizontally, a fixed gun fires within 20 degrees of the target and a missile at any heading.
     */
    private static void fireUpstream(AircraftEntity wingman, Entity target, WingmanEntry entry, int cd) {
        double dx = target.getX() - wingman.getX();
        double dy = target.getY() + 1.5 - wingman.getY();
        double dz = target.getZ() - wingman.getZ();
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        float hd = (float) Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.max(hd, 1.0F)));
        if (dist > fireRangeForWeapon(entry.weaponType) || cd > 0) {
            return;
        }
        boolean canFire = true;
        if (isBombWeapon(entry.weaponType)) {
            canFire = hd <= 80.0F;
        } else if (isFixedGunWeapon(entry.weaponType)) {
            canFire = Math.abs(Mth.wrapDegrees(yaw - wingman.getYaw())) < 20.0F;
        }
        if (canFire) {
            tryFire(wingman, entry, target, yaw, pitch, entry.weaponType, false);
        }
    }

    /**
     * Where to point and whether to fire.
     *
     * @param steer where the aircraft points its nose: the lead point of a gun or rocket, where a
     *              bomb's target will be when it lands, a missile's target
     * @param yaw   the shot's direction, within its mount
     */
    record Aim(Vec3 steer, float yaw, float pitch, boolean fire) {
    }

    /**
     * The pilot's firing solution against {@code target} with its chosen weapon ({@link FireControl}).
     * With {@code firing} (after the aircraft move) it updates what it tracks of the target.
     */
    private static @Nullable Aim aim(ServerLevel level, AircraftEntity ac, WingmanEntry entry, Entity target,
            boolean firing) {
        AiSkill skill = PilotAi.skill();
        String type = attackType(entry);
        FireControl.Role role = FireControl.role(type);
        WeaponSet ws = entry.aiWeapon >= 0 && entry.aiWeapon < ac.getWeaponNum() ? ac.getWeapon(entry.aiWeapon) : null;
        WeaponBase wb = ws != null ? ws.getCurrentWeapon() : null;
        WeaponInfo info = ws != null ? ws.getInfo() : null;
        Vec3 muzzle = wb != null ? ac.position().add(wb.getShotPos(ac)) : ac.position();
        Vec3 centre = target.getBoundingBox().getCenter();
        PilotAi.Track track = PilotAi.track(ac, target, level.getGameTime(), firing);
        Vec3 tv = track.velocity().scale(skill.leadFactor());
        Vec3 ta = skill.predictsTurns() ? track.acceleration() : Vec3.ZERO;
        double dist = muzzle.distanceTo(centre);
        boolean inRange = dist <= fireRangeForWeapon(type);
        float noseYaw = ac.getYaw();
        float nosePitch = ac.getPitch();
        Vec3 los = centre.subtract(muzzle);
        if (role.ballistic() && info != null) {
            FireControl.Ballistics b = ballistics(info);
            FireControl.Solution sol = FireControl.intercept(muzzle, ac.getDeltaMovement(), centre, tv, ta, b);
            if (sol == null) {
                return new Aim(centre, FireControl.yaw(los), FireControl.pitch(los), false);
            }
            // Guns off the centreline (in the wings) are laid in towards the target too, as a
            // fighter's guns are harmonised, else from 3 blocks out they pass it by at short range.
            Vec3 offset = muzzle.subtract(ac.position());
            double side = offset.subtract(sol.dir().scale(offset.dot(sol.dir()))).length();
            float convergence = (float) Math.toDegrees(Math.atan2(side, Math.max(dist, 20.0)));
            float[] shot = mount(ac, entry.aiWeapon, skill.gimbal() + convergence).clamp(noseYaw, nosePitch,
                    FireControl.yaw(sol.dir()), FireControl.pitch(sol.dir()));
            double off = FireControl.angle(FireControl.look(shot[0], shot[1]), sol.dir());
            double radius = hitRadius(target, info);
            boolean fire = inRange && sol.miss() <= radius + 0.5
                    && off <= FireControl.tolerance(radius, dist) * skill.coneFactor();
            // The nose points along the solution from the aircraft's centre, the same for every gun.
            return new Aim(ac.position().add(sol.dir().scale(dist)), shot[0], shot[1], fire);
        }
        if (role.guided()) {
            FireControl.Mount m = mount(ac, entry.aiWeapon, (float) MISSILE_CONE);
            float[] centreDir = m.centre(noseYaw, nosePitch);
            float[] shot = m.clamp(noseYaw, nosePitch, FireControl.yaw(los), FireControl.pitch(los));
            boolean fire = inRange && dist >= MISSILE_MIN_RANGE
                    && FireControl.angle(FireControl.look(centreDir[0], centreDir[1]), los) <= MISSILE_CONE;
            return new Aim(centre, shot[0], shot[1], fire);
        }
        if (role == FireControl.Role.BOMB && info != null) {
            Vec3 ground = new Vec3(centre.x, target.getY(), centre.z);
            FireControl.Drop drop = FireControl.drop(muzzle, ac.getDeltaMovement(), ground, tv, ballistics(info));
            boolean fire = false;
            Vec3 steer = new Vec3(target.getX(), ac.getY(), target.getZ());
            if (drop != null) {
                double tol = Math.max(1.5, hitRadius(target, info));
                double last = firing ? PilotAi.swapAlong(ac, drop.along()) : Double.NaN;
                fire = inRange && drop.cross() <= tol && Math.abs(drop.along()) <= tol
                        // Passing the release point between ticks, it drops late rather than not at all.
                        || inRange && last > 0.0 && drop.along() <= 0.0 && drop.cross() <= tol * 2.5;
                steer = new Vec3(target.getX() + tv.x * drop.ticks(), ac.getY(), target.getZ() + tv.z * drop.ticks());
            }
            return new Aim(steer, noseYaw, nosePitch, fire);
        }
        // A torpedo runs within upstream's 20 degrees; with no weapon fit for the target it holds fire.
        boolean fire = role == FireControl.Role.TORPEDO && entry.aiWeapon >= 0 && inRange
                && Math.abs(Mth.wrapDegrees(FireControl.yaw(los) - noseYaw)) < 20.0F;
        return new Aim(centre, FireControl.yaw(los), FireControl.pitch(los), fire);
    }

    private static FireControl.Ballistics ballistics(WeaponInfo info) {
        return FireControl.Ballistics.of(info.type, info.acceleration, info.gravity, info.dragInAir,
                info.speedDependsAircraft, info.timeFuse);
    }

    /**
     * How close a round must pass: half the target's size, and for an explosive round part of its
     * blast, up to 3 blocks.
     */
    private static double hitRadius(Entity target, @Nullable WeaponInfo info) {
        double r = Math.max(target.getBbWidth(), target.getBbHeight()) * 0.5;
        if (info != null && info.explosion > 0) {
            r += Math.min(info.explosion * 0.5, 3.0);
        }
        return Math.max(r, 0.5);
    }

    /**
     * The mount of weapon set {@code index}, as {@code AircraftWeapons.aimWeapon} turns it: its
     * fixed direction and, for a turret or a weapon with yaw limits (about its default yaw) or pitch
     * limits, how far it turns; any weapon can be laid {@code gimbal} degrees off it.
     */
    private static FireControl.Mount mount(AircraftEntity ac, int index, float gimbal) {
        AircraftInfo info = ac.getAcInfo();
        AircraftInfo.Weapon w = info != null ? info.getWeaponById(index) : null;
        if (w == null) {
            return FireControl.Mount.fixed(0.0F, 0.0F, gimbal);
        }
        boolean yawLimits = Math.abs(w.minYaw) > 0.001F || Math.abs(w.maxYaw) > 0.001F;
        boolean yawControl = w.turret || yawLimits;
        boolean unlimited = w.turret && !yawLimits || Math.abs((int) w.minYaw) >= 360 || Math.abs((int) w.maxYaw) >= 360;
        boolean pitchLimits = Math.abs(w.minPitch) > 0.001F || Math.abs(w.maxPitch) > 0.001F;
        float minYaw = !yawControl ? 0.0F : unlimited ? -180.0F : w.minYaw;
        float maxYaw = !yawControl ? 0.0F : unlimited ? 180.0F : w.maxYaw;
        float minPitch = pitchLimits ? w.minPitch : w.turret ? info.minRotationPitch : 0.0F;
        float maxPitch = pitchLimits ? w.maxPitch : w.turret ? info.maxRotationPitch : 0.0F;
        return new FireControl.Mount(w.yaw + (yawControl ? w.defaultYaw : 0.0F), w.pitch, Math.min(minYaw, -gimbal),
                Math.max(maxYaw, gimbal), Math.min(minPitch, -gimbal), Math.max(maxPitch, gimbal));
    }

    /**
     * Chooses the weapon set to attack {@code target} with: among those its order allows (or the
     * type it was told to use, or any), the one that suits the target best ({@link FireControl#score}),
     * kept for {@link #WEAPON_HOLD_TICKS} unless the target changes.
     */
    public static void chooseWeapon(ServerLevel level, AircraftEntity ac, WingmanEntry entry, Entity target) {
        if (entry.aiWeaponHold > 0 && target.getUUID().equals(entry.aiWeaponTarget) && entry.aiWeapon < ac.getWeaponNum()) {
            entry.aiWeaponHold--;
            return;
        }
        boolean air = airborne(level, target);
        boolean armored = target instanceof AircraftEntity;
        boolean water = target.isInWater();
        double dist = ac.distanceTo(target);
        int best = -1;
        String bestType = null;
        double bestScore = 0.0;
        for (int i = 0; i < ac.getWeaponNum(); i++) {
            WeaponSet ws = ac.getWeapon(i);
            if (ws == null || ws.getInfo() == null || ws.getInfo().type == null || !allowed(entry, ws)) {
                continue;
            }
            String type = ws.getInfo().type;
            if ("targetingpod".equalsIgnoreCase(type) && !"targetingpod".equalsIgnoreCase(entry.weaponType)) {
                continue;
            }
            double score = FireControl.score(FireControl.role(type), air, armored, water, dist <= fireRangeForWeapon(type));
            if (!ws.canFire()) {
                score *= 0.9;
            }
            if (score > bestScore) {
                bestScore = score;
                best = i;
                bestType = type;
            }
        }
        if (best != entry.aiWeapon) {
            PilotAi.endPass(ac);
        }
        entry.aiWeapon = best;
        entry.aiWeaponType = bestType;
        entry.aiWeaponTarget = target.getUUID();
        entry.aiWeaponHold = WEAPON_HOLD_TICKS;
    }

    /** The weapons the pilot may use: its order's, else the type it was told to use, else any. */
    private static boolean allowed(WingmanEntry entry, WeaponSet ws) {
        if (entry.order != null && !entry.order.weapons.isEmpty()) {
            for (String w : entry.order.weapons) {
                if (matchesWeaponType(ws, w)) {
                    return true;
                }
            }
            return false;
        }
        return entry.weaponType == null || matchesWeaponType(ws, entry.weaponType);
    }

    /** Turns an AI plane's horizontal motion towards its nose, as a wing would, so it does not drift sideways. */
    private static void dampSideslip(AircraftEntity plane) {
        Vec3 m = plane.getDeltaMovement();
        double hs = Math.sqrt(m.x * m.x + m.z * m.z);
        if (hs < 0.05) {
            return;
        }
        double rad = Math.toRadians(plane.getYaw());
        double fx = -Math.sin(rad);
        double fz = Math.cos(rad);
        // Flying backwards (a VTOL backing off) is left alone.
        if (m.x * fx + m.z * fz <= 0.0) {
            return;
        }
        double nx = m.x + (fx * hs - m.x) * SLIP_DAMPING;
        double nz = m.z + (fz * hs - m.z) * SLIP_DAMPING;
        double n = Math.sqrt(nx * nx + nz * nz);
        if (n > 1.0E-6) {
            plane.setDeltaMovement(nx / n * hs, m.y, nz / n * hs);
        }
    }

    // ---- targets ----

    /**
     * Where the wingman flies: upstream's move target, or while it breaks from a missile the break
     * point, kept clear of the terrain ahead (M10d) once it flies.
     */
    private static @Nullable Vec3 computeMoveTarget(ServerLevel level, AircraftEntity wingman, WingmanEntry entry) {
        if (!flies(wingman, entry)) {
            return upstreamMoveTarget(level, wingman, entry);
        }
        Vec3 brk = PilotAi.breakTarget(wingman);
        Vec3 raw = brk != null ? brk : upstreamMoveTarget(level, wingman, entry);
        if (raw == null) {
            return null;
        }
        boolean attacking = entry.attackMode != WingmanEntry.ATK_NONE;
        double formation = Double.NaN;
        if (!attacking && !entry.isAutonomous() && entry.leader != null) {
            formation = entry.leader.getY() - TerrainAvoidance.of(level).top(entry.leader.getBlockX(), entry.leader.getBlockZ());
        }
        return PilotAi.adjust(level, wingman, raw, attacking && !isBombWeapon(attackType(entry)), formation);
    }

    /**
     * Whether the wingman is in the air for its pilot: not a ground vehicle, and on a mission not
     * on the ground or flying its take-off or landing, whose heights the mission sets.
     */
    private static boolean flies(AircraftEntity wingman, WingmanEntry entry) {
        if (wingman instanceof GroundVehicleEntity) {
            return false;
        }
        if (!entry.isAutonomous()) {
            return true;
        }
        return switch (entry.autoState) {
            case ENROUTE, ATTACK, LOITER, TRANSIT_TO, ON_STATION, STRIKE_PASS, RTB -> !entry.vtolHoverMode;
            default -> false;
        };
    }

    /** Upstream {@code computeMoveTarget}: the attack path while attacking, else the formation slot. */
    private static @Nullable Vec3 upstreamMoveTarget(ServerLevel level, AircraftEntity wingman, WingmanEntry entry) {
        if (entry.attackMode != WingmanEntry.ATK_NONE) {
            Entity target = resolveTarget(level, wingman, entry);
            if (target != null && !target.isRemoved()) {
                String type = attackType(entry);
                boolean orbitAttack = entry.order != null && entry.order.orbitAttack;
                Vec3 extend = PilotAi.extendTarget(wingman, clampAttackAltitude(target.getY() + altOffsetForWeapon(type)));
                if (extend != null) {
                    return applyLeaderClearance(extend, entry.leader);
                }
                // A plane cannot hold a missile on a target it circles: it runs in at it instead.
                if (isOrbitFireWeapon(type, orbitAttack) && (orbitAttack || !isPlane(wingman))) {
                    return computeOrbitTarget(wingman, target, entry);
                } else if (isBombWeapon(type)) {
                    return computeOverflyTarget(wingman, target, entry);
                }
                return computeApproachTarget(wingman, target, entry);
            }
        }
        if (entry.isAutonomous()) {
            Vec3 raw = new Vec3(entry.autoTargetX, entry.autoTargetY, entry.autoTargetZ);
            // Cruising, a target more than 60 degrees off the nose is replaced by a point 40 blocks
            // out 60 degrees round, so the thrust keeps the aircraft moving forward through the turn.
            if (!isTaxiingState(entry.autoState) && !isAfhThrottleState(entry.autoState)
                    && entry.autoState != AutonomousState.ALIGN && !entry.vtolHoverMode) {
                double tdx = raw.x - wingman.getX();
                double tdz = raw.z - wingman.getZ();
                if (Math.sqrt(tdx * tdx + tdz * tdz) > 20) {
                    float curYaw = wingman.getYaw();
                    float yawDiff = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(-tdx, tdz)) - curYaw);
                    if (Math.abs(yawDiff) > 60.0F) {
                        double rad = Math.toRadians(curYaw + Math.signum(yawDiff) * 60.0F);
                        return new Vec3(wingman.getX() - Math.sin(rad) * 40.0, raw.y, wingman.getZ() + Math.cos(rad) * 40.0);
                    }
                }
            }
            return raw;
        }
        if (circlesLeader(level, wingman, entry)) {
            return orbitPoint(wingman, entry.leader, CIRCLE_RADIUS, entry.leader.getY() + CIRCLE_HEIGHT, 1.0);
        }
        if (entry.state == WingmanState.FOLLOWING && entry.leader != null) {
            return formationPos(entry.leader, entry.formationSlot);
        }
        return null;
    }

    /** Upstream {@code applyLeaderClearance}: keeps an attack path out of the leader's way. */
    private static Vec3 applyLeaderClearance(Vec3 movTarget, @Nullable Entity leader) {
        if (leader == null) {
            return movTarget;
        }
        double clearance = Math.max(WingmanConfig.formationSideDist, WingmanConfig.formationRearDist);
        if (clearance <= 0) {
            return movTarget;
        }
        double ldx = movTarget.x - leader.getX();
        double ldz = movTarget.z - leader.getZ();
        double hd = Math.sqrt(ldx * ldx + ldz * ldz);
        if (hd < clearance && hd > 0.01) {
            double scale = clearance / hd;
            return new Vec3(leader.getX() + ldx * scale, movTarget.y, leader.getZ() + ldz * scale);
        }
        return movTarget;
    }

    private static @Nullable Vec3 computeAimTarget(ServerLevel level, AircraftEntity wingman, WingmanEntry entry) {
        if (entry.attackMode == WingmanEntry.ATK_NONE) {
            return null;
        }
        Entity target = resolveTarget(level, wingman, entry);
        if (target == null || target.isRemoved()) {
            return null;
        }
        if (wingman instanceof GroundVehicleEntity) {
            return new Vec3(target.getX(), target.getY() + 1.5, target.getZ());
        }
        // A plane pulling off its pass, or circling a gunship orbit, points along its move target.
        if (isPlane(wingman) && (PilotAi.isExtending(wingman) || entry.order != null && entry.order.orbitAttack)) {
            return null;
        }
        Aim aim = aim(level, wingman, entry, target, false);
        return aim != null ? aim.steer() : null;
    }

    private static double clampAttackAltitude(double y) {
        if (WingmanConfig.minAttackAltitude > 0) {
            y = Math.max(y, WingmanConfig.minAttackAltitude);
        }
        if (WingmanConfig.maxAttackAltitude > 0) {
            y = Math.min(y, WingmanConfig.maxAttackAltitude);
        }
        return y;
    }

    /**
     * Upstream {@code computeOrbitTarget}: circles the target at an order's orbit radius, else at the
     * weapon's standoff. Upstream turned its point 2 degrees a tick whatever the aircraft did, faster
     * than a plane can fly a wide orbit; the port's point is on the circle a little ahead of the
     * aircraft, so it joins the circle and flies round it. A plane whose weapon points to its right
     * circles the other way, to keep the target on that side.
     */
    public static Vec3 computeOrbitTarget(Entity wingman, Entity target, WingmanEntry entry) {
        String type = attackType(entry);
        double orbitRadius = entry.order != null && entry.order.orbitRadius > 0 ? entry.order.orbitRadius
                : standoffForWeapon(type);
        double idealY = clampAttackAltitude(target.getY() + altOffsetForWeapon(type));
        double dir = wingman instanceof AircraftEntity ac && mount(ac, entry.aiWeapon, 0.0F).yaw() > 10.0F ? -1.0 : 1.0;
        Vec3 p = orbitPoint(wingman, target, orbitRadius, idealY, dir);
        entry.orbitAngle = Math.toDegrees(Math.atan2(p.x - target.getX(), p.z - target.getZ()));
        return applyLeaderClearance(p, entry.leader);
    }

    /**
     * The point on a circle of {@code radius} round {@code centre}, at {@code y}, an arc of about
     * 40 blocks (at most 45 degrees) on from the aircraft's bearing; {@code dir} 1 goes round
     * anticlockwise seen from above, -1 clockwise.
     */
    static Vec3 orbitPoint(Entity ac, Entity centre, double radius, double y, double dir) {
        double bearing = Math.atan2(ac.getX() - centre.getX(), ac.getZ() - centre.getZ());
        double rad = bearing + dir * Math.min(Math.toRadians(45.0), 40.0 / Math.max(radius, 1.0));
        return new Vec3(centre.getX() + Math.sin(rad) * radius, y, centre.getZ() + Math.cos(rad) * radius);
    }

    /** Upstream {@code computeApproachTarget}: closes to the standoff distance, then turns away. */
    private static Vec3 computeApproachTarget(AircraftEntity wingman, Entity target, WingmanEntry entry) {
        double standoff = standoffForWeapon(attackType(entry));
        double dx = target.getX() - wingman.getX();
        double dz = target.getZ() - wingman.getZ();
        double hDist = Math.sqrt(dx * dx + dz * dz);
        double idealY = clampAttackAltitude(target.getY() + altOffsetForWeapon(attackType(entry)));
        Vec3 raw;
        if (hDist > standoff) {
            double ratio = (hDist - standoff) / hDist;
            raw = new Vec3(wingman.getX() + dx * ratio, idealY, wingman.getZ() + dz * ratio);
        } else {
            double norm = Math.max(hDist, 0.1);
            raw = new Vec3(target.getX() - dx / norm * standoff * 1.5, idealY, target.getZ() - dz / norm * standoff * 1.5);
        }
        return applyLeaderClearance(raw, entry.leader);
    }

    /**
     * Upstream {@code computeOverflyTarget}: a bomber aims 80 blocks past the target. Upstream went
     * past it along the bomber's heading, which a bomber not yet lined up never flew over; the port
     * goes past it along the line from the bomber, so the run passes over the target.
     */
    private static Vec3 computeOverflyTarget(AircraftEntity wingman, Entity target, WingmanEntry entry) {
        double idealY = clampAttackAltitude(target.getY() + altOffsetForWeapon("bomb"));
        double dx = target.getX() - wingman.getX();
        double dz = target.getZ() - wingman.getZ();
        double hd = Math.sqrt(dx * dx + dz * dz);
        double fx;
        double fz;
        if (hd > 1.0) {
            fx = dx / hd;
            fz = dz / hd;
        } else {
            double facing = Math.toRadians(wingman.getYaw());
            fx = -Math.sin(facing);
            fz = Math.cos(facing);
        }
        return applyLeaderClearance(new Vec3(target.getX() + fx * 80.0, idealY, target.getZ() + fz * 80.0),
                entry.leader);
    }

    /**
     * Upstream: guided missiles orbit the target, as does every weapon of an order with Orbit Atk set
     * (its AC-130 mode).
     */
    public static boolean isOrbitFireWeapon(@Nullable String type, boolean orbitAttack) {
        if (orbitAttack) {
            return true;
        }
        if (type == null) {
            return false;
        }
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "missile", "aamissile", "atmissile", "tvmissile", "asmissile" -> true;
            default -> false;
        };
    }

    private static boolean isBombWeapon(@Nullable String type) {
        return type != null && type.equalsIgnoreCase("bomb");
    }

    /**
     * Upstream {@code formationPos}: slots alternate right and left in ranks behind the leader, or
     * with a side distance of 0, one behind another in a column.
     */
    public static Vec3 formationPos(Entity leader, int slot) {
        double sideDist = WingmanConfig.formationSideDist;
        double altOff = WingmanConfig.formationAltOffset;
        double rearDist = WingmanConfig.formationRearDist;
        double yawRad = Math.toRadians(leader.getYRot());
        double fwdX = -Math.sin(yawRad);
        double fwdZ = Math.cos(yawRad);
        if (sideDist == 0.0) {
            int rank = slot + 1;
            return new Vec3(leader.getX() - fwdX * rearDist * rank, leader.getY() + altOff,
                    leader.getZ() - fwdZ * rearDist * rank);
        }
        double rigX = Math.cos(yawRad);
        double rigZ = Math.sin(yawRad);
        int rank = slot / 2 + 1;
        double sideSign = slot % 2 == 0 ? 1.0 : -1.0;
        return new Vec3(leader.getX() + rigX * sideDist * sideSign * rank - fwdX * rearDist * rank,
                leader.getY() + altOff, leader.getZ() + rigZ * sideDist * sideSign * rank - fwdZ * rearDist * rank);
    }

    private static @Nullable Entity resolveTarget(ServerLevel level, AircraftEntity wingman, WingmanEntry entry) {
        if (entry.attackMode == WingmanEntry.ATK_MANUAL && entry.manualTargetId != null) {
            return level.getEntity(entry.manualTargetId);
        }
        if (entry.attackMode == WingmanEntry.ATK_AUTO) {
            Set<UUID> taken = targetedUuids(wingman.getUUID(), entry.leader);
            List<Entity> siblings = siblings(level, wingman.getUUID(), entry.leader);
            Entity target = findNearestHostile(wingman, entry.leader, AUTO_ATTACK_RANGE, taken, siblings);
            entry.currentAutoTarget = target != null ? target.getUUID() : null;
            return target;
        }
        return null;
    }

    /**
     * Upstream {@code isEnemy}, as its comments describe it: hostile mobs and target drones, never a
     * player, a wingman or anything a player flies. Upstream also took everything its team check
     * passed, which an aircraft attacker always did, so the port names the targets: hostile mobs,
     * target drones, and McHeli gunners of a team the leader's pilot is not on.
     */
    public static boolean isEnemy(@Nullable Entity leader, Entity e) {
        return isEnemy(leader, "", e);
    }

    /**
     * {@link #isEnemy(Entity, Entity)} for an aircraft fighting for {@code team}: with NPC battles
     * on, also the gunners, wingmen and mission aircraft of other teams (NpcBattles), and not the
     * gunners of its own.
     */
    public static boolean isEnemy(@Nullable Entity leader, String team, Entity e) {
        if (e instanceof Player || e.isRemoved()) {
            return false;
        }
        for (Entity passenger : e.getPassengers()) {
            if (passenger instanceof Player) {
                return false;
            }
        }
        if (leader != null && (e == leader || e.getRootVehicle() == leader.getRootVehicle())) {
            return false;
        }
        boolean enemy;
        if (WingmanRegistry.get(e.getUUID()) != null) {
            enemy = NpcBattles.isEnemyNpc(team, e);
        } else if (e instanceof AircraftEntity ac) {
            enemy = ac.getAcInfo() != null && ac.getAcInfo().isTargetDrone && !ac.isDestroyed()
                    && !(ac.getPilot() instanceof Player) || NpcBattles.isEnemyNpc(team, ac);
        } else if (e instanceof GunnerEntity gunner && NpcBattles.enabled() && !team.isEmpty()) {
            enemy = NpcBattles.isEnemyNpc(team, gunner);
        } else if (e instanceof GunnerEntity gunner) {
            Entity pilot = leader != null ? pilotOf(leader) : null;
            enemy = gunner.isAlive() && !gunner.getTeamName().isEmpty()
                    && (pilot == null || !gunner.isAlliedTo(pilot));
        } else {
            enemy = e instanceof Enemy && e instanceof LivingEntity living && living.isAlive();
        }
        if (enemy && ENEMY_LOGGED.add(e.getUUID())) {
            McheliCePort.LOGGER.info("[Wingman/IFF] {} -> enemy", e.getName().getString());
        }
        return enemy;
    }

    /**
     * Upstream {@code findNearestHostile}: the nearest enemy that no other wingman of the leader is
     * attacking, skipping one whose attack path passes too close to another wingman.
     */
    private static @Nullable Entity findNearestHostile(AircraftEntity wingman, @Nullable Entity leader, double range,
            Set<UUID> exclude, List<Entity> siblings) {
        String team = NpcBattles.teamOf(wingman);
        double minSep = WingmanConfig.formationSideDist > 0 ? WingmanConfig.formationSideDist
                : WingmanConfig.formationRearDist;
        double bestSq = range * range;
        Entity best = null;
        // M10e: the pilot looks by eye, so rain and night cut its range unless the target is lit.
        FlightWeather.Conditions weather = FlightWeather.at(wingman.level(), wingman.position());
        double inDark = FlightWeather.sensorFactor(weather, FlightWeather.Sensor.VISUAL,
                FlightWeather.seesInDark(wingman, null));
        double inLight = FlightWeather.sensorFactor(weather, FlightWeather.Sensor.VISUAL, true);
        outer:
        for (Entity e : wingman.level().getEntities(wingman, wingman.getBoundingBox().inflate(range),
                c -> isEnemy(leader, team, c))) {
            if (exclude.contains(e.getUUID())) {
                continue;
            }
            double sees = range * (inDark == inLight || !FlightWeather.lit(e) ? inDark : inLight);
            if (wingman.distanceToSqr(e) > sees * sees) {
                continue;
            }
            if (minSep > 0) {
                double midX = (wingman.getX() + e.getX()) * 0.5;
                double midZ = (wingman.getZ() + e.getZ()) * 0.5;
                for (Entity sib : siblings) {
                    double sdx = midX - sib.getX();
                    double sdz = midZ - sib.getZ();
                    if (sdx * sdx + sdz * sdz < minSep * minSep) {
                        continue outer;
                    }
                }
            }
            double dsq = wingman.distanceToSqr(e);
            if (dsq < bestSq) {
                bestSq = dsq;
                best = e;
            }
        }
        return best;
    }

    private static List<Entity> siblings(ServerLevel level, UUID selfId, @Nullable Entity leader) {
        List<Entity> result = new ArrayList<>();
        for (Map.Entry<UUID, WingmanEntry> e : WingmanRegistry.snapshot().entrySet()) {
            if (e.getKey().equals(selfId) || e.getValue().leader != leader) {
                continue;
            }
            Entity en = level.getEntity(e.getKey());
            if (en != null) {
                result.add(en);
            }
        }
        return result;
    }

    /** The targets the leader's other wingmen are attacking, so they spread out. */
    private static Set<UUID> targetedUuids(UUID selfId, @Nullable Entity leader) {
        Set<UUID> taken = new HashSet<>();
        for (Map.Entry<UUID, WingmanEntry> e : WingmanRegistry.snapshot().entrySet()) {
            WingmanEntry other = e.getValue();
            if (e.getKey().equals(selfId) || other.leader != leader) {
                continue;
            }
            if (other.attackMode == WingmanEntry.ATK_MANUAL && other.manualTargetId != null) {
                taken.add(other.manualTargetId);
            } else if (other.attackMode == WingmanEntry.ATK_AUTO && other.currentAutoTarget != null) {
                taken.add(other.currentAutoTarget);
            }
        }
        return taken;
    }

    // ---- weapons ----

    private static double standoffForWeapon(@Nullable String type) {
        if (type == null) {
            return 80.0;
        }
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "gun", "machinegun", "machinegun1", "machinegun2", "cannon" -> 50.0;
            case "cas" -> 60.0;
            case "rocket", "mkrocket" -> 80.0;
            case "missile", "aamissile", "atmissile", "tvmissile" -> 200.0;
            case "asmissile" -> 280.0;
            case "bomb" -> 180.0;
            case "torpedo" -> 40.0;
            default -> 80.0;
        };
    }

    private static double altOffsetForWeapon(@Nullable String type) {
        if (type == null) {
            return 50.0;
        }
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "gun", "machinegun", "machinegun1", "machinegun2", "cannon" -> 40.0;
            case "cas" -> 50.0;
            case "rocket", "mkrocket" -> 60.0;
            case "missile", "aamissile", "atmissile", "tvmissile" -> 80.0;
            case "asmissile" -> 200.0;
            // Upstream bombed from 200 blocks; from 100 a bomb falls in half the time, for a moving target to stray.
            case "bomb" -> 100.0;
            case "torpedo" -> 5.0;
            default -> 50.0;
        };
    }

    private static double fireRangeForWeapon(@Nullable String type) {
        if (type == null) {
            return 150.0;
        }
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "gun", "machinegun", "machinegun1", "machinegun2", "cannon" -> 120.0;
            case "cas" -> 140.0;
            case "rocket", "mkrocket" -> 200.0;
            case "missile", "aamissile", "atmissile", "tvmissile" -> 300.0;
            case "asmissile" -> 380.0;
            case "bomb" -> 350.0;
            case "torpedo" -> 80.0;
            default -> 150.0;
        };
    }

    private static int fireCooldownForType(@Nullable String type) {
        if (type == null) {
            return 10;
        }
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "gun", "machinegun", "machinegun1", "machinegun2" -> 2;
            case "cas" -> 3;
            case "cannon" -> 5;
            case "rocket", "mkrocket" -> 10;
            case "aamissile", "atmissile", "missile" -> 20;
            case "asmissile" -> 60;
            case "tvmissile", "torpedo" -> 30;
            case "bomb" -> 80;
            default -> 10;
        };
    }

    private static boolean isHeavyWeapon(@Nullable String type) {
        if (type == null) {
            return false;
        }
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "bomb", "asmissile", "missile", "aamissile", "atmissile", "tvmissile" -> true;
            default -> false;
        };
    }

    /**
     * Upstream {@code tryFire}: fires the first weapon of the wanted type that is ready (else the
     * first of that type), with infinite ammunition, at the wingman's own cooldown. The port fires
     * as the wingman itself, along its own heading, where upstream fired as the leader and along the
     * leader's heading, and passes the target to seeking weapons as the port's gunners do. With
     * {@code aimed} it fires the set the pilot chose, along the fire control's yaw and pitch.
     */
    private static void tryFire(AircraftEntity ac, WingmanEntry entry, Entity target, float yaw, float pitch,
            @Nullable String weaponType, boolean aimed) {
        if (ac.getWeaponNum() <= 0) {
            return;
        }
        UUID id = ac.getUUID();
        WeaponSet chosen = aimed && entry.aiWeapon >= 0 && entry.aiWeapon < ac.getWeaponNum() ? ac.getWeapon(entry.aiWeapon)
                : pickWeapon(ac, weaponType, true);
        if (chosen == null) {
            chosen = pickWeapon(ac, weaponType, false);
        }
        if (chosen == null) {
            return;
        }
        // Upstream's wingmen fire with infinite ammunition, but that only refills a magazine once it
        // is fired empty: one that was never loaded (a fresh aircraft) never fired at all.
        if (chosen.getMagSize() > 0 && chosen.getAmmo() <= 0 && chosen.getRestAllAmmoNum() <= 0 && !chosen.isBusy()) {
            chosen.setAmmo(chosen.getMagSize());
        }
        String resolvedType = resolveWeaponType(chosen, weaponType);
        WeaponParam prm = new WeaponParam();
        prm.entity = ac;
        prm.user = ac;
        prm.isInfinity = true;
        prm.option1 = target.getId();
        prm.setPosAndRot(ac.getX(), ac.getY(), ac.getZ(), yaw, pitch);
        // The fire control lays the shot itself; upstream's shot went along the aircraft's nose.
        if (aimed ? ac.weapons().useAimed(chosen, prm, yaw, pitch) : chosen.use(prm)) {
            FIRE_COOLDOWNS.put(id, fireCooldownForType(resolvedType));
            if (isHeavyWeapon(resolvedType)) {
                POST_FIRE_ESCAPE.put(id, POST_FIRE_ESCAPE_TICKS);
            }
            McheliCePort.LOGGER.debug("[Wingman] fired type={}", resolvedType);
        }
    }

    private static @Nullable WeaponSet pickWeapon(AircraftEntity ac, @Nullable String weaponType,
            boolean requireCanFire) {
        for (int i = 0; i < ac.getWeaponNum(); i++) {
            WeaponSet ws = ac.getWeapon(i);
            if (ws == null || ws.getInfo() == null) {
                continue;
            }
            String t = resolveWeaponType(ws, "");
            if ("dummy".equalsIgnoreCase(t)
                    || "targetingpod".equalsIgnoreCase(t) && !"targetingpod".equalsIgnoreCase(weaponType)) {
                continue;
            }
            if (weaponType != null && !matchesWeaponType(ws, weaponType) || requireCanFire && !ws.canFire()) {
                continue;
            }
            return ws;
        }
        return null;
    }

    private static @Nullable String resolveWeaponType(WeaponSet ws, @Nullable String fallback) {
        return ws.getInfo() == null || ws.getInfo().type == null ? fallback : ws.getInfo().type;
    }

    private static boolean matchesWeaponType(WeaponSet ws, String expectedType) {
        String actual = resolveWeaponType(ws, null);
        if (actual == null) {
            return false;
        }
        if (expectedType.equalsIgnoreCase(actual)) {
            return true;
        }
        String act = actual.toLowerCase(Locale.ROOT);
        return switch (expectedType.toLowerCase(Locale.ROOT)) {
            case "gun" -> act.contains("gun") || act.equals("cas");
            case "cannon" -> act.contains("cannon");
            case "missile" -> act.contains("missile");
            case "asmissile" -> act.equals("asmissile");
            case "rocket" -> act.contains("rocket");
            case "bomb" -> act.contains("bomb");
            case "torpedo" -> act.contains("torpedo");
            default -> false;
        };
    }

    // ---- engine ----

    /** Upstream {@code maintainEngineAdaptive}: full throttle far from the slot, the leader's close to it. */
    private static void maintainEngineAdaptive(AircraftEntity aircraft, Entity leader, double distToSlot) {
        double leaderThrottle = leader instanceof AircraftEntity la ? la.getCurrentThrottle() : 0.5;
        double target;
        if (distToSlot >= THROTTLE_BLEND_FAR) {
            target = 1.0;
        } else if (distToSlot <= THROTTLE_BLEND_NEAR) {
            target = leaderThrottle;
        } else {
            double t = (distToSlot - THROTTLE_BLEND_NEAR) / (THROTTLE_BLEND_FAR - THROTTLE_BLEND_NEAR);
            target = leaderThrottle + t * (1.0 - leaderThrottle);
        }
        double minThrottle = distToSlot > ARRIVAL_THRESHOLD ? 0.25 : 0.05;
        applyThrottle(aircraft, Math.max(minThrottle, Math.min(1.0, target)));
    }

    /**
     * Upstream {@code applySlotBoost}: after the flight model has moved a wingman more than 20
     * blocks from its slot, it moves it further towards the slot, up to twice the leader's speed
     * from 80 blocks. Only horizontally: the flight model keeps the altitude. A plane only forwards.
     */
    private static void applySlotBoost(AircraftEntity wingman, double fdx, double fdz, double distToSlot,
            double leaderSpeed) {
        if (distToSlot <= BOOST_DIST_OFF) {
            return;
        }
        double taper = distToSlot >= BOOST_DIST_ON ? 1.0
                : (distToSlot - BOOST_DIST_OFF) / (BOOST_DIST_ON - BOOST_DIST_OFF);
        double boost = leaderSpeed * BOOST_LEADER_MULT * taper;
        double norm = Math.max(distToSlot, 0.01);
        double bx = fdx / norm;
        double bz = fdz / norm;
        if (isPlane(wingman)) {
            // A plane is pushed only along its nose, and only as far as the slot lies ahead of it:
            // pushed straight at the slot it slid sideways through the air.
            double rad = Math.toRadians(wingman.getYaw());
            double fx = -Math.sin(rad);
            double fz = Math.cos(rad);
            double ahead = Math.max(0.0, bx * fx + bz * fz);
            bx = fx * ahead;
            bz = fz * ahead;
        }
        wingman.setPos(wingman.getX() + bx * boost, wingman.getY(), wingman.getZ() + bz * boost);
        if (++boostDebugTick >= 20) {
            boostDebugTick = 0;
            McheliCePort.LOGGER.debug("[Wingman/Boost] dist={} leaderSpd={} boost={}", (int) distToSlot,
                    String.format(Locale.ROOT, "%.3f", leaderSpeed), String.format(Locale.ROOT, "%.3f", boost));
        }
    }

    /**
     * A helicopter's throttle for its target height: the throttle that holds it level (lift of
     * 0.055 a tick at full throttle against its gravity), plus 0.03 for each block it is below the
     * target. Upstream gave helicopters the planes' formation throttle, so far from their slot they
     * climbed at full throttle without end, and held a stopped leader at 0.35, too little to hover
     * (KNOWN_ISSUES 97).
     */
    static double heliAltitudeThrottle(AircraftEntity heli, double targetY) {
        float gravity = heli.getAcInfo() != null ? heli.getAcInfo().gravity : -0.04F;
        double hover = Mth.clamp(-gravity / 0.055, 0.0, 1.0);
        return Mth.clamp(hover + (targetY - heli.getY()) * 0.03, 0.0, 1.0);
    }

    private static void applyThrottle(AircraftEntity aircraft, double throttle) {
        aircraft.setCurrentThrottle(throttle);
    }

    // ---- gear and hold ----

    /** Upstream {@code syncGear}: the wingman folds and unfolds its gear with the leader's. */
    private static void syncGear(AircraftEntity wingman, Entity leader) {
        if (!(leader instanceof AircraftEntity l) || !wingman.canFoldLandingGear()) {
            return;
        }
        boolean lf = l.isLandingGearFolded();
        boolean wf = wingman.isLandingGearFolded();
        if (lf && !wf) {
            wingman.foldLandingGear();
        } else if (!lf && wf) {
            wingman.unfoldLandingGear();
        }
    }

    /**
     * Upstream {@code autoGear}: on a mission the gear is up while flying and down on the ground,
     * on final and landing vertically.
     */
    private static void autoGear(AircraftEntity wingman, AutonomousState state) {
        boolean retract = switch (state) {
            case CLIMB, ENROUTE, ATTACK, LOITER, DESCEND, CIRCUIT_DOWNWIND, CIRCUIT_BASE, TRANSIT_TO, ON_STATION,
                    STRIKE_PASS, RTB, VTOL_TAKEOFF -> true;
            default -> false;
        };
        if (!wingman.canFoldLandingGear()) {
            return;
        }
        boolean folded = wingman.isLandingGearFolded();
        if (retract && !folded) {
            wingman.foldLandingGear();
        } else if (!retract && folded) {
            wingman.unfoldLandingGear();
        }
    }

    // ---- taxiing ----

    /** About 5 metres a second. */
    private static final double TAXI_SPEED = 0.25;

    /** Upstream {@code isTaxiingState}: taxiing and the roll-out after touchdown move along the ground. */
    static boolean isTaxiingState(AutonomousState state) {
        return state == AutonomousState.TAXI_OUT || state == AutonomousState.TAXI_IN || state == AutonomousState.LANDING;
    }

    /** Upstream {@code isAfhThrottleState}: the states whose throttle the mission sets precisely. */
    static boolean isAfhThrottleState(AutonomousState state) {
        return switch (state) {
            case TAKEOFF_ROLL, CLIMB, DESCEND, CIRCUIT_DOWNWIND, CIRCUIT_BASE, CIRCUIT_FINAL, LANDING, VTOL_TAKEOFF,
                    VTOL_LAND -> true;
            default -> false;
        };
    }

    /**
     * Upstream {@code taxiPushPosition}: after the flight model has moved the aircraft, it is put
     * at the taxi speed from where it started the tick towards the next marker, at the height the
     * flight model left it, level and facing the way it goes. After touchdown the speed falls from
     * the landing speed to the taxi speed by 3.5% a tick.
     */
    private static void taxiPushPosition(AircraftEntity wingman, WingmanEntry entry) {
        double baseX = entry.taxiPreX;
        double baseZ = entry.taxiPreZ;
        double dx = entry.autoTargetX - baseX;
        double dz = entry.autoTargetZ - baseZ;
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < 0.5) {
            wingman.setDeltaMovement(0, wingman.getDeltaMovement().y, 0);
            return;
        }
        double speed = TAXI_SPEED;
        if (entry.autoState == AutonomousState.LANDING && entry.landingRollSpeed > TAXI_SPEED) {
            entry.landingRollSpeed = Math.max(TAXI_SPEED, entry.landingRollSpeed * 0.965);
            speed = entry.landingRollSpeed;
        }
        double step = Math.min(speed, dist);
        wingman.setPos(baseX + dx / dist * step, wingman.getY(), baseZ + dz / dist * step);
        wingman.setDeltaMovement(dx / dist * speed, wingman.getDeltaMovement().y, dz / dist * speed);
        wingman.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));
        wingman.setPitch(0.0F);
        wingman.setRoll(0.0F);
    }

    public static boolean isLeaderStopped(Entity leader) {
        return leader.getDeltaMovement().length() < LEADER_STOP_SPEED;
    }

    /**
     * Upstream {@code holdStop}: a plane idles; a helicopter keeps a hover throttle trimmed to the
     * leader's altitude, since at zero throttle it would fall and overshoot upwards again. Both turn
     * to the leader's heading.
     */
    private static void holdStop(AircraftEntity aircraft, Entity leader) {
        if (aircraft instanceof HeliEntity) {
            applyThrottle(aircraft, heliAltitudeThrottle(aircraft, leader.getY() + WingmanConfig.formationAltOffset));
            // The port levels the nose it flew with, so the helicopter stops (KNOWN_ISSUES 97).
            aircraft.setPitch(aircraft.getPitch() - Mth.clamp(aircraft.getPitch(), -MAX_PITCH_RATE, MAX_PITCH_RATE));
        } else {
            applyThrottle(aircraft, 0.0);
        }
        float childYaw = aircraft.getYaw();
        aircraft.setYaw(childYaw + Mth.clamp(Mth.wrapDegrees(leader.getYRot() - childYaw), -MAX_YAW_RATE_HELI,
                MAX_YAW_RATE_HELI));
    }

    private static @Nullable Entity pilotOf(Entity aircraft) {
        if (aircraft instanceof AircraftEntity ac) {
            Entity pilot = ac.getPilot();
            if (pilot != null) {
                return pilot;
            }
        }
        return aircraft.getFirstPassenger();
    }
}
