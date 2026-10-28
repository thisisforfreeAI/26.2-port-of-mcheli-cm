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

    /** One pilot's state. */
    static final class Pilot {
        long threatSince = -1;
        int breakTicks;
        float breakYaw;
        int cmCooldown;
        boolean crippled;
        boolean pullingUp;
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
