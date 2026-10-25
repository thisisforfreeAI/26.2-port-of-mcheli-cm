package io.github.mcheliceport.combat;

import io.github.mcheliceport.config.ServerConfig;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.aircraft.AircraftHitBox;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.jspecify.annotations.Nullable;

/**
 * M9 lag compensation, a port addition: the server keeps each vehicle's last second of positions
 * and a player's projectiles test vehicles where that player saw them, about one round trip plus
 * the client's interpolation in the past, capped by {@code projectiles.lagCompensationMaxMs}.
 *
 * <p>Only vehicles (with their hit boxes and riders) are rewound, never blocks, players on foot or
 * mobs. Explosions and splash damage still happen where things are now.
 */
public final class LagCompensation {

    /** How far behind the server a client draws other vehicles: a 2-tick update plus vanilla's interpolation. */
    public static final int INTERPOLATION_MS = 100;

    private static final Map<AircraftEntity, PoseHistory> HISTORY = new WeakHashMap<>();

    private LagCompensation() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(LagCompensation::onEntityTick);
    }

    private static void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof AircraftEntity aircraft && !aircraft.level().isClientSide()) {
            record(aircraft);
        }
    }

    /** Records the vehicle's position for this tick. */
    public static void record(AircraftEntity aircraft) {
        if (aircraft.isRemoved()) {
            HISTORY.remove(aircraft);
            return;
        }
        HISTORY.computeIfAbsent(aircraft, k -> new PoseHistory())
                .record(aircraft.level().getGameTime(), aircraft.getX(), aircraft.getY(), aircraft.getZ());
    }

    /** Records the vehicle at a given tick (tests, which cannot wait for real ticks). */
    public static void recordAt(AircraftEntity aircraft, long tick, double x, double y, double z) {
        HISTORY.computeIfAbsent(aircraft, k -> new PoseHistory()).record(tick, x, y, z);
    }

    /**
     * Ticks to rewind targets for a shot fired with this round-trip latency: latency plus the
     * interpolation delay, capped, in whole ticks. No latency (the integrated server's host, NPCs,
     * test players) or a cap of 0 gives no rewind.
     */
    public static int rewindTicks(int latencyMs, int maxMs) {
        if (maxMs <= 0 || latencyMs <= 0) {
            return 0;
        }
        return (int) Math.round(Math.min(latencyMs + INTERPOLATION_MS, maxMs) / 50.0);
    }

    /** The rewind for shots from this entity, fixed when the shot is fired; 0 for anything but a remote player. */
    public static int rewindTicksFor(@Nullable Entity shooter) {
        if (!(shooter instanceof ServerPlayer player) || player.connection == null) {
            return 0;
        }
        return rewindTicks(player.connection.latency(), ServerConfig.get(ServerConfig.LAG_COMPENSATION_MAX_MS));
    }

    /**
     * How far to move this entity's current box to where it was {@code ticksBack} ticks ago, when
     * it is a vehicle, a vehicle's hit box or a vehicle's rider; {@link Vec3#ZERO} otherwise.
     */
    public static Vec3 shift(Entity target, double ticksBack) {
        if (ticksBack <= 0.0) {
            return Vec3.ZERO;
        }
        AircraftEntity aircraft = vehicleOf(target);
        PoseHistory history = aircraft != null ? HISTORY.get(aircraft) : null;
        if (history == null) {
            return Vec3.ZERO;
        }
        double[] out = new double[3];
        history.displacement(aircraft.level().getGameTime(), ticksBack, aircraft.getX(), aircraft.getY(),
                aircraft.getZ(), out);
        return out[0] == 0.0 && out[1] == 0.0 && out[2] == 0.0 ? Vec3.ZERO : new Vec3(out[0], out[1], out[2]);
    }

    private static @Nullable AircraftEntity vehicleOf(Entity target) {
        if (target instanceof AircraftEntity aircraft) {
            return aircraft;
        }
        if (target instanceof AircraftHitBox box) {
            return box.getParent();
        }
        return target.getRootVehicle() instanceof AircraftEntity aircraft ? aircraft : null;
    }
}
