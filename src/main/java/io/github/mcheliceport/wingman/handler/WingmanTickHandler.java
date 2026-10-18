package io.github.mcheliceport.wingman.handler;

import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.gunner.GunnerEntity;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.weapon.WeaponParam;
import io.github.mcheliceport.weapon.WeaponSet;
import io.github.mcheliceport.wingman.config.WingmanConfig;
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
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.jspecify.annotations.Nullable;

/**
 * Upstream's {@code WingmanTickHandler}, its formation and attack half: each server level tick
 * steers the level's wingmen, as upstream, by setting throttle, yaw, pitch and roll within rate
 * limits before the aircraft move ({@link LevelTickEvent.Pre}) and lets the aircraft's own flight
 * model fly them. After they move ({@link LevelTickEvent.Post}) it fires, pushes a wingman that has
 * fallen far behind its slot and copies the leader's landing gear. The mission states come with the
 * mission increment.
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
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> {
            WingmanRegistry.clear();
            FIRE_COOLDOWNS.clear();
            POST_FIRE_ESCAPE.clear();
            ENEMY_LOGGED.clear();
            WingmanConfig.reset();
        });
    }

    // ---- before the aircraft move ----

    private static void onLevelTickPre(LevelTickEvent.Pre event) {
        if (event.getLevel() instanceof ServerLevel level) {
            tickStart(level);
        }
    }

    /** Upstream {@code onWorldTickStart}: engine and attitude for each wingman in the level. */
    public static void tickStart(ServerLevel level) {
        for (Map.Entry<UUID, WingmanEntry> e : WingmanRegistry.snapshot().entrySet()) {
            if (!(level.getEntity(e.getKey()) instanceof AircraftEntity wingman) || wingman.isRemoved()) {
                continue;
            }
            WingmanEntry entry = e.getValue();
            if (entry.attackMode != WingmanEntry.ATK_NONE) {
                Vec3 movTarget = wingman instanceof HeliEntity ? computeMoveTarget(level, wingman, entry) : null;
                applyThrottle(wingman, movTarget != null ? heliAltitudeThrottle(wingman, movTarget.y) : 1.0);
                steerToTarget(level, wingman, entry);
            } else if (entry.leader != null) {
                Vec3 fp = formationPos(entry.leader, entry.formationSlot);
                double distToSlot = fp.distanceTo(wingman.position());
                if (isLeaderStopped(entry.leader) && distToSlot < HOLD_THRESHOLD) {
                    holdStop(wingman, entry.leader);
                } else {
                    if (wingman instanceof HeliEntity) {
                        applyThrottle(wingman, heliAltitudeThrottle(wingman, fp.y));
                    } else {
                        maintainEngineAdaptive(wingman, entry.leader, distToSlot);
                    }
                    steerToTarget(level, wingman, entry);
                }
            }
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
        Vec3 aimTarget = computeAimTarget(level, wingman, entry);
        Vec3 aimRef = aimTarget != null ? aimTarget : movTarget;

        boolean heli = wingman instanceof HeliEntity;
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
            if (hDist >= 1.0) {
                float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                if (!inAttack && entry.leader != null) {
                    float alpha = (float) Math.min(1.0, hDist / 30.0);
                    float leaderYaw = entry.leader.getYRot();
                    targetYaw = leaderYaw + Mth.wrapDegrees(targetYaw - leaderYaw) * alpha;
                }
                float yawStep = Mth.clamp(Mth.wrapDegrees(targetYaw - currentYaw), -maxYawRate, maxYawRate);
                wingman.setYaw(currentYaw + yawStep);
                wingman.lastRiderYaw = targetYaw;
            }
            if (!inAttack && entry.leader != null) {
                float leaderRoll = entry.leader instanceof AircraftEntity la ? la.getRoll() : 0.0F;
                float currentRoll = wingman.getRoll();
                float rollStep = Mth.clamp(Mth.wrapDegrees(leaderRoll - currentRoll), -MAX_ROLL_RATE, MAX_ROLL_RATE);
                wingman.setRoll(currentRoll + rollStep);
            }
            // Fixed guns aim with the nose; other weapons keep the aircraft level and aim through lastRiderPitch.
            boolean fixedGun = inAttack && isFixedGunWeapon(entry.weaponType);
            float targetPitch;
            if (fixedGun) {
                targetPitch = (float) -Math.toDegrees(Math.atan2(dy, Math.max(hDist, 1.0)));
                targetPitch = Mth.clamp(targetPitch, MAX_PITCH_DOWN, MAX_PITCH_UP);
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
            case "gun", "machinegun", "machinegun1", "machinegun2", "cannon", "cas", "rocket", "mkrocket" -> true;
            default -> false;
        };
    }

    // ---- after the aircraft move ----

    private static void onLevelTickPost(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            tickEnd(level);
        }
    }

    /** Upstream {@code onWorldTickEnd}: firing, the slot boost and the landing gear. */
    public static void tickEnd(ServerLevel level) {
        for (Map.Entry<UUID, WingmanEntry> e : WingmanRegistry.snapshot().entrySet()) {
            UUID id = e.getKey();
            WingmanEntry entry = e.getValue();
            if (!entry.isAutonomous() && (entry.leader == null || entry.leader.isRemoved())) {
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
            if (entry.attackMode != WingmanEntry.ATK_NONE) {
                fireAtTarget(level, wingman, entry);
            }
            if (entry.attackMode == WingmanEntry.ATK_NONE && !entry.isAutonomous() && entry.leader != null
                    && !isLeaderStopped(entry.leader)) {
                Vec3 fp = formationPos(entry.leader, entry.formationSlot);
                double fdx = fp.x - wingman.getX();
                double fdz = fp.z - wingman.getZ();
                applySlotBoost(wingman, fdx, fdz, Math.sqrt(fdx * fdx + fdz * fdz),
                        entry.leader.getDeltaMovement().length());
            }
            if (entry.leader != null) {
                syncGear(wingman, entry.leader);
            }
        }
    }

    /**
     * Upstream's firing in {@code onWorldTickEnd}: within the weapon's range a bomb drops within 80
     * blocks horizontally, a fixed gun fires within 20 degrees of the target and a missile at any heading.
     */
    private static void fireAtTarget(ServerLevel level, AircraftEntity wingman, WingmanEntry entry) {
        Entity target = resolveTarget(level, wingman, entry);
        if (target == null || target.isRemoved()) {
            return;
        }
        if (entry.diagTick % 100 == 1) {
            McheliCePort.LOGGER.info("[Wingman/ATK] {} targeting: name='{}' class={} pos=({},{},{}) dist={}",
                    wingman.getUUID().toString().substring(0, 8), target.getName().getString(),
                    target.getClass().getSimpleName(), (int) target.getX(), (int) target.getY(), (int) target.getZ(),
                    (int) wingman.distanceTo(target));
        }
        double dx = target.getX() - wingman.getX();
        double dy = target.getY() + 1.5 - wingman.getY();
        double dz = target.getZ() - wingman.getZ();
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        float hd = (float) Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.max(hd, 1.0F)));
        if (dist > fireRangeForWeapon(entry.weaponType)) {
            return;
        }
        boolean canFire = true;
        if (isBombWeapon(entry.weaponType)) {
            canFire = hd <= 80.0F;
        } else if (isFixedGunWeapon(entry.weaponType)) {
            canFire = Math.abs(Mth.wrapDegrees(yaw - wingman.getYaw())) < 20.0F;
        }
        if (canFire) {
            tryFire(wingman, target, yaw, pitch, entry.weaponType);
        }
    }

    // ---- targets ----

    /** Upstream {@code computeMoveTarget}: the attack path while attacking, else the formation slot. */
    private static @Nullable Vec3 computeMoveTarget(ServerLevel level, AircraftEntity wingman, WingmanEntry entry) {
        if (entry.attackMode != WingmanEntry.ATK_NONE) {
            Entity target = resolveTarget(level, wingman, entry);
            if (target != null && !target.isRemoved()) {
                if (isOrbitFireWeapon(entry.weaponType)) {
                    return computeOrbitTarget(target, entry);
                } else if (isBombWeapon(entry.weaponType)) {
                    return computeOverflyTarget(wingman, target, entry);
                }
                return computeApproachTarget(wingman, target, entry);
            }
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
        return new Vec3(target.getX(), target.getY() + 1.5, target.getZ());
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

    /** Upstream {@code computeOrbitTarget}: circles the target at the weapon's standoff, 2 degrees a tick. */
    private static Vec3 computeOrbitTarget(Entity target, WingmanEntry entry) {
        double orbitRadius = standoffForWeapon(entry.weaponType);
        double idealY = clampAttackAltitude(target.getY() + altOffsetForWeapon(entry.weaponType));
        entry.orbitAngle = (entry.orbitAngle + 2.0) % 360.0;
        double rad = Math.toRadians(entry.orbitAngle);
        return applyLeaderClearance(new Vec3(target.getX() + Math.sin(rad) * orbitRadius, idealY,
                target.getZ() + Math.cos(rad) * orbitRadius), entry.leader);
    }

    /** Upstream {@code computeApproachTarget}: closes to the standoff distance, then turns away. */
    private static Vec3 computeApproachTarget(AircraftEntity wingman, Entity target, WingmanEntry entry) {
        double standoff = standoffForWeapon(entry.weaponType);
        double dx = target.getX() - wingman.getX();
        double dz = target.getZ() - wingman.getZ();
        double hDist = Math.sqrt(dx * dx + dz * dz);
        double idealY = clampAttackAltitude(target.getY() + altOffsetForWeapon(entry.weaponType));
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

    /** Upstream {@code computeOverflyTarget}: a bomber aims 80 blocks past the target along its heading. */
    private static Vec3 computeOverflyTarget(AircraftEntity wingman, Entity target, WingmanEntry entry) {
        double idealY = clampAttackAltitude(target.getY() + altOffsetForWeapon("bomb"));
        double facing = Math.toRadians(wingman.getYaw());
        return applyLeaderClearance(new Vec3(target.getX() - Math.sin(facing) * 80.0, idealY,
                target.getZ() + Math.cos(facing) * 80.0), entry.leader);
    }

    private static boolean isOrbitFireWeapon(@Nullable String type) {
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
        if (e instanceof Player || e.isRemoved() || WingmanRegistry.get(e.getUUID()) != null) {
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
        if (e instanceof AircraftEntity ac) {
            enemy = ac.getAcInfo() != null && ac.getAcInfo().isTargetDrone && !ac.isDestroyed()
                    && !(ac.getPilot() instanceof Player);
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
        double minSep = WingmanConfig.formationSideDist > 0 ? WingmanConfig.formationSideDist
                : WingmanConfig.formationRearDist;
        double bestSq = range * range;
        Entity best = null;
        outer:
        for (Entity e : wingman.level().getEntities(wingman, wingman.getBoundingBox().inflate(range),
                c -> isEnemy(leader, c))) {
            if (exclude.contains(e.getUUID())) {
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
            case "asmissile", "bomb" -> 200.0;
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
     * leader's heading, and passes the target to seeking weapons as the port's gunners do.
     */
    private static void tryFire(AircraftEntity ac, Entity target, float yaw, float pitch, @Nullable String weaponType) {
        if (ac.getWeaponNum() <= 0) {
            return;
        }
        UUID id = ac.getUUID();
        int cd = FIRE_COOLDOWNS.getOrDefault(id, 0);
        if (cd > 0) {
            FIRE_COOLDOWNS.put(id, cd - 1);
            return;
        }
        WeaponSet chosen = pickWeapon(ac, weaponType, true);
        if (chosen == null) {
            chosen = pickWeapon(ac, weaponType, false);
        }
        if (chosen == null) {
            return;
        }
        String resolvedType = resolveWeaponType(chosen, weaponType);
        WeaponParam prm = new WeaponParam();
        prm.entity = ac;
        prm.user = ac;
        prm.isInfinity = true;
        prm.option1 = target.getId();
        prm.setPosAndRot(ac.getX(), ac.getY(), ac.getZ(), yaw, pitch);
        if (chosen.use(prm)) {
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
     * from 80 blocks. Only horizontally: the flight model keeps the altitude.
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
        wingman.setPos(wingman.getX() + fdx / norm * boost, wingman.getY(), wingman.getZ() + fdz / norm * boost);
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
