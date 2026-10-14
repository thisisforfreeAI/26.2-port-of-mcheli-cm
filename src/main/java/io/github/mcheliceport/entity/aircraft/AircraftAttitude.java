package io.github.mcheliceport.entity.aircraft;

import io.github.mcheliceport.util.McheliMath;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

/**
 * Turning a vehicle from the pilot's mouse: the attitude half of upstream's
 * {@code MCH_EntityAircraft#setAngles} and {@code #applyQuatAngles}, without the player-view part
 * (which stays in the client input code).
 *
 * <p>Upstream has two paths, both kept:
 * <ul>
 *   <li>the Euler path, which composes the control delta with the current attitude as a quaternion
 *       and reads Euler angles back with {@code MCH_Lib.eulerFromOrientationQuat};</li>
 *   <li>the quaternion path ({@code ExperimentalQuaternionRotation}, on by default), which keeps a
 *       persistent attitude quaternion so the vehicle can fly through vertical.</li>
 * </ul>
 * Everything here works on plain numbers, so the tests drive it without a world.
 */
public final class AircraftAttitude {

    private AircraftAttitude() {
    }

    /** Something with an attitude: a vehicle entity, or a test stand-in. */
    public interface Rotatable {
        float getYaw();

        void setYaw(float yaw);

        float getPitch();

        void setPitch(float pitch);

        float getRoll();

        void setRoll(float roll);

        /** The persistent attitude quaternion of the quaternion path, null until first used. */
        @Nullable Quaternionf getOrientation();

        void setOrientation(@Nullable Quaternionf orientation);
    }

    /** The vehicle's content limits ({@code LimitRotation}, {@code Min/MaxRotationPitch/Roll}). */
    public record Limits(boolean limitRotation, float minPitch, float maxPitch, float minRoll, float maxRoll) {
        public static final Limits NONE = new Limits(false, 0, 0, 0, 0);
    }

    /** Per-vehicle hook that runs after the control rotation: upstream's {@code onUpdateAngles}. */
    @FunctionalInterface
    public interface AngleUpdater {
        void onUpdateAngles(float deltaSeconds);
    }

    /**
     * The control rotation one frame adds: upstream {@code setAngles} from its start up to the
     * choice of path. {@code inputX}/{@code inputY} are the mouse stick values; for helicopters the
     * yaw and roll inputs are both {@code inputX} and the pitch input is {@code inputY}.
     *
     * @return {yaw, pitch, roll} in degrees to add this frame
     */
    public static float[] controlDelta(boolean freeLook, boolean canUpdateYaw, boolean canUpdatePitch,
            boolean canUpdateRoll, float yawInput, float pitchInput, float rollInput, double yawLimit,
            double pitchLimit, double rollLimit, float yawFactor, float pitchFactor, float rollFactor,
            float deltaSeconds) {
        if (freeLook) {
            yawInput = 0.0F;
            pitchInput = 0.0F;
            rollInput = 0.0F;
        }
        float yaw = 0.0F;
        float pitch = 0.0F;
        float roll = 0.0F;
        if (canUpdateYaw) {
            yaw = (float) Mth.clamp(yawInput, -yawLimit, yawLimit);
            yaw *= yawFactor * deltaSeconds;
        }
        if (canUpdatePitch) {
            pitch = (float) Mth.clamp(pitchInput, -pitchLimit, pitchLimit);
            pitch *= -pitchFactor * deltaSeconds;
        }
        if (canUpdateRoll) {
            roll = (float) Mth.clamp(rollInput, -rollLimit, rollLimit);
            roll *= rollFactor * deltaSeconds;
        }
        return new float[] {yaw, pitch, roll};
    }

    /**
     * Applies one frame's control rotation: upstream {@code setAngles} from the choice of path up to
     * the roll wrap. Runs {@code updater} (the vehicle's {@code onUpdateAngles}) in between, as
     * upstream.
     */
    public static void apply(Rotatable r, float yaw, float pitch, float roll, float deltaSeconds,
            boolean quaternion, Limits limits, AngleUpdater updater) {
        if (quaternion) {
            applyQuat(r, yaw, pitch, roll, deltaSeconds, limits, updater);
        } else {
            applyEuler(r, yaw, pitch, roll, deltaSeconds, limits, updater);
        }
        if (r.getRoll() > 180.0F) {
            r.setRoll(r.getRoll() - 360.0F);
        }
        if (r.getRoll() < -180.0F) {
            r.setRoll(r.getRoll() + 360.0F);
        }
    }

    private static void applyEuler(Rotatable r, float yaw, float pitch, float roll, float deltaSeconds,
            Limits limits, AngleUpdater updater) {
        Quaternionf q = new Quaternionf()
                .rotateZ(roll * ((float) Math.PI / 180.0F))
                .rotateX(pitch * ((float) Math.PI / 180.0F))
                .rotateY(yaw * ((float) Math.PI / 180.0F))
                .rotateZ(r.getRoll() * ((float) Math.PI / 180.0F))
                .rotateX(r.getPitch() * ((float) Math.PI / 180.0F))
                .rotateY(r.getYaw() * ((float) Math.PI / 180.0F));
        float[] ypr = McheliMath.eulerFromOrientationQuat(q);
        float vPitch = ypr[0];
        float vYaw = ypr[1];
        float vRoll = ypr[2];
        if (limits.limitRotation()) {
            vPitch = McheliMath.rng(vPitch, limits.minPitch(), limits.maxPitch());
            vRoll = McheliMath.rng(vRoll, limits.minRoll(), limits.maxRoll());
        }
        if (vRoll > 180.0F) {
            vRoll -= 360.0F;
        }
        if (vRoll < -180.0F) {
            vRoll += 360.0F;
        }
        r.setYaw(vYaw);
        r.setPitch(vPitch);
        r.setRoll(vRoll);
        updater.onUpdateAngles(deltaSeconds);
        if (limits.limitRotation()) {
            r.setPitch(McheliMath.rng(r.getPitch(), limits.minPitch(), limits.maxPitch()));
            r.setRoll(McheliMath.rng(r.getRoll(), limits.minRoll(), limits.maxRoll()));
        }
    }

    private static void applyQuat(Rotatable r, float yaw, float pitch, float roll, float deltaSeconds,
            Limits limits, AngleUpdater updater) {
        Quaternionf orientation = r.getOrientation();
        if (orientation == null) {
            orientation = McheliMath.worldOrientationQuat(r.getYaw(), r.getPitch(), r.getRoll());
            r.setOrientation(orientation);
        }
        // Control deltas apply in the body frame (right-multiply), as upstream.
        orientation.mul(McheliMath.worldOrientationQuat(yaw, pitch, roll));
        orientation.normalize();
        float[] e = McheliMath.worldEuler(orientation);
        r.setPitch(e[0]);
        r.setYaw(e[1]);
        r.setRoll(e[2]);

        // The per-type levelling runs in Euler; its change is folded back into the quaternion.
        float pBefore = r.getPitch();
        float yBefore = r.getYaw();
        float rBefore = r.getRoll();
        updater.onUpdateAngles(deltaSeconds);
        float dP = Mth.wrapDegrees(r.getPitch() - pBefore);
        float dY = Mth.wrapDegrees(r.getYaw() - yBefore);
        float dR = Mth.wrapDegrees(r.getRoll() - rBefore);
        if (dP != 0.0F || dY != 0.0F || dR != 0.0F) {
            orientation.mul(McheliMath.worldOrientationQuat(dY, dP, dR));
            orientation.normalize();
            float[] e2 = McheliMath.worldEuler(orientation);
            r.setPitch(e2[0]);
            r.setYaw(e2[1]);
            r.setRoll(e2[2]);
        }

        if (limits.limitRotation()) {
            float cp = McheliMath.rng(r.getPitch(), limits.minPitch(), limits.maxPitch());
            float cr = McheliMath.rng(r.getRoll(), limits.minRoll(), limits.maxRoll());
            if (cp != r.getPitch() || cr != r.getRoll()) {
                r.setPitch(cp);
                r.setRoll(cr);
                r.setOrientation(McheliMath.worldOrientationQuat(r.getYaw(), cp, cr));
            }
        }
    }

    /** Upstream {@code resyncOrientationFromEuler}: rebuilds the quaternion after an outside change. */
    public static void resyncOrientation(Rotatable r) {
        r.setOrientation(McheliMath.worldOrientationQuat(r.getYaw(), r.getPitch(), r.getRoll()));
    }
}
