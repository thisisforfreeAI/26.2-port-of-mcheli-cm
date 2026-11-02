package io.github.mcheliceport.combat;

import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.net.ModNetwork;
import io.github.mcheliceport.net.VehicleMotionPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import io.github.mcheliceport.platform.GameEvents;

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
        GameEvents.ENTITY_TICK_POST.add(WatcherMotion::onEntityTick);
    }

    private static void onEntityTick(net.minecraft.world.entity.Entity entity) {
        if (!(entity instanceof AircraftEntity aircraft) || !(aircraft.level() instanceof ServerLevel level)
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
        for (var player : io.github.mcheliceport.platform.Platform.get().playersTracking(aircraft)) {
            if (!aircraft.isPilot(player)) {
                ModNetwork.sendToPlayer(player, payload);
            }
        }
    }

    /** Whether to send this tick: every {@link #SEND_INTERVAL} ticks, for fast vehicles only. */
    /**
     * One tick of a watched vehicle's turn toward the attitude the server last sent: a quaternion
     * slerp by {@code alpha}, so a jet rolling or looping turns the short way about its own axes
     * instead of swinging yaw, pitch and roll apart. Yaw and roll come back unwrapped next to the
     * start, so a roll through 180 degrees keeps going instead of spinning back.
     *
     * @return {yaw, pitch, roll} in degrees
     */
    public static float[] attitudeStep(float yaw, float pitch, float roll, float yawTo, float pitchTo, float rollTo,
            float alpha) {
        float a = Math.clamp(alpha, 0.0F, 1.0F);
        float[] out = io.github.mcheliceport.util.McheliMath.slerpOrientationYPR(yaw, pitch, roll, yawTo, pitchTo,
                rollTo, a);
        out[0] = yaw + net.minecraft.util.Mth.wrapDegrees(out[0] - yaw);
        out[1] = Math.clamp(out[1], -90.0F, 90.0F);
        out[2] = roll + net.minecraft.util.Mth.wrapDegrees(out[2] - roll);
        return out;
    }

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
