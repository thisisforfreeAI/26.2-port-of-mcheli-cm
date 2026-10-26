package io.github.mcheliceport.combat;

import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.net.ModNetwork;
import io.github.mcheliceport.net.VehicleMotionPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * M9 smoother sync, a port addition: the server sends watching clients the velocity of fast
 * vehicles, and those clients interpolate at that speed and carry on along it when a position
 * update is late, instead of stopping and snapping forward.
 *
 * <p>Clients still draw other vehicles slightly in the past (no forward prediction), which is what
 * {@link LagCompensation} rewinds for.
 */
public final class WatcherMotion {

    /** Ticks between velocity updates; matches the vehicles' position update interval. */
    public static final int SEND_INTERVAL = 2;
    /** Only vehicles at least this fast (blocks per tick, squared) are worth smoothing. */
    public static final double MIN_SPEED_SQR = 1.0;
    /** A velocity older than this many ticks is no longer used. */
    public static final int FRESH_TICKS = 6;
    /** At most this many ticks are carried on past the last position update. */
    public static final int MAX_EXTRAPOLATION = 4;
    /** Interpolation steps while a fresh velocity is known: one position update apart, so the speed stays even. */
    public static final int SMOOTH_STEPS = 2;

    private WatcherMotion() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(WatcherMotion::onEntityTick);
    }

    private static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof AircraftEntity aircraft) || !(aircraft.level() instanceof ServerLevel level)
                || aircraft.isRemoved()) {
            return;
        }
        Vec3 v = aircraft.getDeltaMovement();
        if (!shouldSend(level.getGameTime() + aircraft.getId(), v.lengthSqr())) {
            return;
        }
        VehicleMotionPayload payload = new VehicleMotionPayload(aircraft.getId(), (float) v.x, (float) v.y, (float) v.z);
        if (!payload.valid()) {
            return;
        }
        for (var player : level.getChunkSource().chunkMap.getPlayersWatching(aircraft)) {
            if (!aircraft.isPilot(player)) {
                ModNetwork.sendToPlayer(player, payload);
            }
        }
    }

    /** Whether to send this tick: every {@link #SEND_INTERVAL} ticks, for fast vehicles only. */
    public static boolean shouldSend(long tick, double speedSqr) {
        return Math.floorMod(tick, SEND_INTERVAL) == 0 && speedSqr >= MIN_SPEED_SQR;
    }

    /** Whether a velocity received at {@code at} is still used at {@code now}. */
    public static boolean fresh(long now, long at) {
        return now >= at && now <= at + FRESH_TICKS;
    }

    /** Whether a watching client carries the vehicle on along its velocity this tick. */
    public static boolean extrapolates(boolean interpolating, boolean fresh, int extrapolatedTicks) {
        return !interpolating && fresh && extrapolatedTicks < MAX_EXTRAPOLATION;
    }
}
