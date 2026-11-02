package io.github.mcheliceport.wingman.ai;

import io.github.mcheliceport.config.ServerConfig;
import io.github.mcheliceport.damage.ComponentKind;
import io.github.mcheliceport.damage.ComponentState;
import io.github.mcheliceport.damage.ComponentView;
import io.github.mcheliceport.damage.VehicleComponents;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.aircraft.Countermeasures;
import io.github.mcheliceport.entity.weapon.BaseBulletEntity;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * M10d, a port addition: what an AI pilot (a wingman or an aircraft on a mission) does besides
 * following its orders. It climbs over the terrain it sees ahead, fires its countermeasures at a
 * missile homing on it after a reaction time and, from Normal up, turns away from the missile; it
 * breaks off an attack once an engine or a rotor is disabled. {@link AiSkill} sets how well.
 * See {@code docs/M10_AI_PILOTS.md}.
 */
public final class PilotAi {

    /** Ticks between countermeasure salvos, as the gunner's. */
    static final int CM_COOLDOWN = 30;
    /** How far ahead a break points, in blocks. */
    static final double BREAK_DISTANCE = 80.0;
    /** The least a formation wingman keeps over the ground ahead, however low its leader flies. */
    static final double MIN_FORMATION_CLEARANCE = 3.0;
    /** The least a plane extends away after a pass, in ticks, and the most before it turns back in. */
    static final int EXTEND_TICKS = 30;
    static final int MAX_EXTEND_TICKS = 200;
    /** How far ahead an extending plane points, in blocks. */
    static final double EXTEND_DISTANCE = 120.0;
    /** A tracked target's acceleration is believed at most this far, in blocks a tick per tick. */
    static final double MAX_TARGET_ACCEL = 0.15;

    /** One pilot's state. */
    static final class Pilot {
        long threatSince = -1;
        int breakTicks;
        float breakYaw;
        int cmCooldown;
        boolean crippled;
        boolean pullingUp;
        // Fire control (a port addition): the target it tracks, and the pass it flies.
        @Nullable UUID tracked;
        long trackedAt = Long.MIN_VALUE;
        Vec3 rawVel = Vec3.ZERO;
        Vec3 targetVel = Vec3.ZERO;
        Vec3 targetAcc = Vec3.ZERO;
        boolean extending;
        int extendTicks;
        float extendYaw;
        double lastAlong = Double.NaN;
    }

    /** A tracked target's velocity and acceleration, in blocks a tick (and per tick). */
    public record Track(Vec3 velocity, Vec3 acceleration) {
    }

    private static final Map<UUID, Pilot> PILOTS = new HashMap<>();

    private PilotAi() {
    }

    public static AiSkill skill() {
        return ServerConfig.SPEC.isLoaded() ? ServerConfig.AI_DIFFICULTY.get() : AiSkill.NORMAL;
    }

    public static boolean terrainAvoidance() {
        return !ServerConfig.SPEC.isLoaded() || ServerConfig.AI_TERRAIN.get();
    }

    private static Pilot pilot(Entity ac) {
        return PILOTS.computeIfAbsent(ac.getUUID(), id -> new Pilot());
    }

    /** Drops the state of pilots no longer flying. */
    public static void retain(Set<UUID> flying) {
        PILOTS.keySet().retainAll(flying);
    }

    public static void forget(UUID id) {
        PILOTS.remove(id);
    }

    /**
     * Once a tick for each AI pilot, before it steers: counts down its break, looks for missiles
     * (each tick while one is on it, else every 5), answers one after the reaction time and checks
     * its components.
     */
    public static void tick(AircraftEntity ac, long gameTime) {
        Pilot p = pilot(ac);
        AiSkill skill = skill();
        p.pullingUp = false;
        if (p.breakTicks > 0) {
            p.breakTicks--;
        }
        if (p.cmCooldown > 0) {
            p.cmCooldown--;
        }
        p.crippled = crippled(ac);
        if (ac.isDestroyed() || (p.threatSince < 0 && ac.tickCount % 5 != 0)) {
            return;
        }
        BaseBulletEntity missile = Countermeasures.incoming(ac);
        if (missile == null) {
            p.threatSince = -1;
            return;
        }
        if (p.threatSince < 0) {
            p.threatSince = gameTime;
        }
        if (gameTime - p.threatSince < skill.reactionTicks()) {
            return;
        }
        if (p.cmCooldown <= 0 && Countermeasures.fire(ac)) {
            p.cmCooldown = CM_COOLDOWN;
        }
        if (skill.breakTicks() > 0 && p.breakTicks == 0) {
            p.breakTicks = skill.breakTicks();
            p.breakYaw = beam(ac.getYaw(), ac.position(), missile.position());
        }
    }

    /**
     * The heading at right angles to the missile's bearing, on the side nearer the current one, so
     * the missile has to turn hardest to follow.
     */
    public static float beam(float yaw, Vec3 from, Vec3 missile) {
        double dx = missile.x - from.x;
        double dz = missile.z - from.z;
        float bearing = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float left = Mth.wrapDegrees(bearing - 90.0F);
        float right = Mth.wrapDegrees(bearing + 90.0F);
        return Math.abs(Mth.wrapDegrees(left - yaw)) <= Math.abs(Mth.wrapDegrees(right - yaw)) ? left : right;
    }

    /** Whether an engine, the main rotor or the tail rotor is disabled (M9 component damage). */
    public static boolean crippled(AircraftEntity ac) {
        for (ComponentView v : VehicleComponents.of(ac)) {
            ComponentKind k = v.kind();
            if (v.state() == ComponentState.DISABLED
                    && (k == ComponentKind.ENGINE || k == ComponentKind.MAIN_ROTOR || k == ComponentKind.TAIL_ROTOR)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isCrippled(Entity ac) {
        Pilot p = PILOTS.get(ac.getUUID());
        return p != null && p.crippled;
    }

    public static boolean isBreaking(Entity ac) {
        Pilot p = PILOTS.get(ac.getUUID());
        return p != null && p.breakTicks > 0;
    }

    /** Whether the pilot is climbing away from terrain this tick, below its clearance over the ground ahead. */
    public static boolean isPullingUp(Entity ac) {
        Pilot p = PILOTS.get(ac.getUUID());
        return p != null && p.pullingUp;
    }

    /** While breaking, the point {@link #BREAK_DISTANCE} out along the break heading at the current height. */
    public static @Nullable Vec3 breakTarget(AircraftEntity ac) {
        Pilot p = PILOTS.get(ac.getUUID());
        if (p == null || p.breakTicks <= 0) {
            return null;
        }
        double rad = Math.toRadians(p.breakYaw);
        return new Vec3(ac.getX() - Math.sin(rad) * BREAK_DISTANCE, ac.getY(), ac.getZ() + Math.cos(rad) * BREAK_DISTANCE);
    }

    /**
     * A port addition: what the pilot knows of {@code target}'s motion, from where it moved over the
     * last tick. With {@code update} (once a tick, after the entities move) the velocity is
     * smoothed and the change in it taken as the target's acceleration; otherwise the last values
     * are returned, or the raw motion for a target it was not tracking.
     */
    public static Track track(Entity ac, Entity target, long gameTime, boolean update) {
        Pilot p = pilot(ac);
        Vec3 raw = new Vec3(target.getX() - target.xo, target.getY() - target.yo, target.getZ() - target.zo);
        boolean same = target.getUUID().equals(p.tracked) && gameTime - p.trackedAt <= 2;
        if (!update) {
            return same ? new Track(p.targetVel, p.targetAcc) : new Track(raw, Vec3.ZERO);
        }
        if (same && p.trackedAt == gameTime) {
            return new Track(p.targetVel, p.targetAcc);
        }
        if (same) {
            Vec3 acc = p.targetAcc.lerp(raw.subtract(p.rawVel), 0.3);
            p.targetAcc = acc.length() > MAX_TARGET_ACCEL ? acc.normalize().scale(MAX_TARGET_ACCEL) : acc;
            p.targetVel = p.targetVel.lerp(raw, 0.7);
        } else {
            p.tracked = target.getUUID();
            p.targetVel = raw;
            p.targetAcc = Vec3.ZERO;
            p.lastAlong = Double.NaN;
            p.extending = false;
        }
        p.rawVel = raw;
        p.trackedAt = gameTime;
        return new Track(p.targetVel, p.targetAcc);
    }

    /**
     * A port addition: a plane's gun or rocket pass. Closer than {@code minRange}, or too low in a
     * dive, it pulls off and extends straight out for at least {@link #EXTEND_TICKS}, until it is
     * {@code reattack} blocks away (or {@link #MAX_EXTEND_TICKS} have passed) and turns back in.
     * Returns whether it is extending.
     */
    public static boolean updatePass(AircraftEntity ac, double dist, double minRange, double reattack, boolean tooLow) {
        Pilot p = pilot(ac);
        if (p.extending) {
            p.extendTicks++;
            if (p.extendTicks >= EXTEND_TICKS && dist >= reattack || p.extendTicks >= MAX_EXTEND_TICKS) {
                p.extending = false;
            }
        } else if (dist < minRange || tooLow) {
            p.extending = true;
            p.extendTicks = 0;
            p.extendYaw = ac.getYaw();
        }
        return p.extending;
    }

    /** Ends any pass in progress, when the pilot stops attacking or changes weapon. */
    public static void endPass(Entity ac) {
        Pilot p = PILOTS.get(ac.getUUID());
        if (p != null) {
            p.extending = false;
        }
    }

    public static boolean isExtending(Entity ac) {
        Pilot p = PILOTS.get(ac.getUUID());
        return p != null && p.extending;
    }

    /** While extending, the point {@link #EXTEND_DISTANCE} out along the heading it pulled off on, at {@code y}. */
    public static @Nullable Vec3 extendTarget(AircraftEntity ac, double y) {
        Pilot p = PILOTS.get(ac.getUUID());
        if (p == null || !p.extending) {
            return null;
        }
        double rad = Math.toRadians(p.extendYaw);
        return new Vec3(ac.getX() - Math.sin(rad) * EXTEND_DISTANCE, y, ac.getZ() + Math.cos(rad) * EXTEND_DISTANCE);
    }

    /** Stores how far a bomb released now would fall short of the target, and returns the last tick's (NaN for none). */
    public static double swapAlong(Entity ac, double along) {
        Pilot p = pilot(ac);
        double last = p.lastAlong;
        p.lastAlong = along;
        return last;
    }

    /**
     * The move target with the terrain taken into account: a Veteran on an attack run (not a bomb
     * run, and with no attack altitude limits set) keeps 20 to 40 blocks over the ground under the
     * target; every pilot keeps its clearance over the highest ground ahead, or in formation the
     * leader's own height over the ground when that is less.
     *
     * @param formationClearance the leader's height over the ground in formation, else NaN
     */
    public static Vec3 adjust(Level level, AircraftEntity ac, Vec3 raw, boolean lowAttack, double formationClearance) {
        AiSkill skill = skill();
        if (!terrainAvoidance()) {
            return raw;
        }
        TerrainAvoidance.Ground ground = TerrainAvoidance.of(level);
        double y = raw.y;
        if (lowAttack && skill.fliesLow()) {
            double under = ground.top(Mth.floor(raw.x), Mth.floor(raw.z));
            y = Mth.clamp(y, under + AiSkill.LOW_MIN, under + AiSkill.LOW_MAX);
        }
        double clearance = skill.clearance();
        if (!Double.isNaN(formationClearance)) {
            clearance = Math.min(clearance, Math.max(MIN_FORMATION_CLEARANCE, formationClearance));
        }
        double ahead = TerrainAvoidance.highestAhead(ground, ac.position(), ac.getDeltaMovement(), skill.lookahead());
        double safe = TerrainAvoidance.safeY(y, ahead, clearance);
        if (ac.getY() < ahead + clearance) {
            pilot(ac).pullingUp = true;
        }
        return safe == raw.y ? raw : new Vec3(raw.x, safe, raw.z);
    }
}
