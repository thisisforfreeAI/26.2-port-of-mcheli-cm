package io.github.mcheliceport.util;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The maths helpers of upstream's {@code MCH_Lib} and {@code W_Vec3} that vehicles use.
 *
 * <p>The rotations use {@link Mth#sin}/{@link Mth#cos}, which read the same 65,536-entry table as
 * 1.12's {@code MathHelper}, so they give upstream's exact results.
 */
public final class McheliMath {

    private McheliMath() {
    }

    /** Upstream {@code MCH_Lib.RNG}: clamps {@code a} to {@code [min, max]}, testing max first. */
    public static float rng(float a, float min, float max) {
        return a > max ? max : Math.max(a, min);
    }

    public static double rng(double a, double min, double max) {
        return a > max ? max : Math.max(a, min);
    }

    /** Upstream {@code MCH_Lib.getRotate360}: {@code r} wrapped to {@code [0, 360)}. */
    public static double getRotate360(double r) {
        r %= 360.0;
        return r >= 0.0 ? r : r + 360.0;
    }

    /** 1.12's {@code Vec3d#rotatePitch}. */
    public static Vec3 rotatePitch(Vec3 v, float pitch) {
        float f = Mth.cos(pitch);
        float f1 = Mth.sin(pitch);
        double d0 = v.x;
        double d1 = v.y * f + v.z * f1;
        double d2 = v.z * f - v.y * f1;
        return new Vec3(d0, d1, d2);
    }

    /** 1.12's {@code Vec3d#rotateYaw}. */
    public static Vec3 rotateYaw(Vec3 v, float yaw) {
        float f = Mth.cos(yaw);
        float f1 = Mth.sin(yaw);
        double d0 = v.x * f + v.z * f1;
        double d1 = v.y;
        double d2 = v.z * f - v.x * f1;
        return new Vec3(d0, d1, d2);
    }

    /** Upstream {@code W_Vec3.rotateRoll}. */
    public static Vec3 rotateRoll(Vec3 v, float roll) {
        float f1 = Mth.cos(roll);
        float f2 = Mth.sin(roll);
        double d0 = v.x * f1 + v.y * f2;
        double d1 = v.y * f1 - v.x * f2;
        double d2 = v.z;
        return new Vec3(d0, d1, d2);
    }

    /**
     * Upstream {@code MCH_Lib.RotVec3(x, y, z, yaw, pitch, roll)}: rotates by roll, then pitch, then
     * yaw, all in degrees. Vehicles pass their negated angles to turn a body offset into a world
     * offset.
     */
    public static Vec3 rotVec3(double x, double y, double z, float yaw, float pitch, float roll) {
        Vec3 v = new Vec3(x, y, z);
        v = rotateRoll(v, roll / 180.0F * (float) Math.PI);
        v = rotatePitch(v, pitch / 180.0F * (float) Math.PI);
        return rotateYaw(v, yaw / 180.0F * (float) Math.PI);
    }

    public static Vec3 rotVec3(Vec3 v, float yaw, float pitch, float roll) {
        return rotVec3(v.x, v.y, v.z, yaw, pitch, roll);
    }

    /** Upstream {@code MCH_Lib.RotVec3(x, y, z, yaw, pitch)}. */
    public static Vec3 rotVec3(double x, double y, double z, float yaw, float pitch) {
        Vec3 v = new Vec3(x, y, z);
        v = rotatePitch(v, pitch / 180.0F * (float) Math.PI);
        return rotateYaw(v, yaw / 180.0F * (float) Math.PI);
    }

    /** Upstream {@code MCH_Lib.orientationQuat}: the quaternion matching {@code rotVec3(v, yaw, pitch, roll)}. */
    public static Quaternionf orientationQuat(float yaw, float pitch, float roll) {
        return new Quaternionf()
                .rotateY((float) Math.toRadians(yaw))
                .rotateX((float) Math.toRadians(-pitch))
                .rotateZ((float) Math.toRadians(-roll));
    }

    /**
     * Upstream {@code MCH_Lib.slerpOrientationYPR}: the body attitude between two ticks, as a
     * quaternion slerp turned back into {yaw, pitch, roll} in degrees.
     */
    public static float[] slerpOrientationYPR(float yaw0, float pitch0, float roll0, float yaw1, float pitch1,
            float roll1, float t) {
        Quaternionf q = orientationQuat(yaw0, pitch0, roll0);
        q.slerp(orientationQuat(yaw1, pitch1, roll1), t);
        Vector3f e = q.getEulerAnglesYXZ(new Vector3f());
        return new float[] {(float) Math.toDegrees(e.y), (float) -Math.toDegrees(e.x), (float) -Math.toDegrees(e.z)};
    }

    /** Upstream {@code MCH_Lib.applyQuat}. */
    public static Vec3 applyQuat(Quaternionf q, double x, double y, double z) {
        Vector3f v = q.transform(new Vector3f((float) x, (float) y, (float) z));
        return new Vec3(v.x, v.y, v.z);
    }

    /**
     * Upstream {@code MCH_Lib.eulerFromOrientationQuat}: {pitch, yaw, roll} in degrees from a
     * quaternion built in the {@code Rz(roll)*Rx(pitch)*Ry(yaw)} order, including its fallback at
     * pitch ±90.
     */
    public static float[] eulerFromOrientationQuat(Quaternionf q) {
        float x = q.x, y = q.y, z = q.z, w = q.w;
        float x2 = 2.0F * x * x, y2 = 2.0F * y * y, z2 = 2.0F * z * z;
        float xy = 2.0F * x * y, yz = 2.0F * y * z, zx = 2.0F * z * x;
        float wx = 2.0F * w * x, wy = 2.0F * w * y, wz = 2.0F * w * z;
        float m00 = 1.0F - y2 - z2;
        float m01 = xy - wz;
        float m02 = zx + wy;
        float m11 = 1.0F - z2 - x2;
        float m20 = zx - wy;
        float m21 = yz + wx;
        float m22 = 1.0F - x2 - y2;
        float b = (float) -Math.asin(m21);
        float cosB = (float) Math.cos(b);
        float a;
        float c;
        if (Math.abs(cosB) >= 1.0e-4F) {
            c = (float) Math.atan2(m20, m22);
            float xyc = m01 / cosB;
            if (xyc > 1.0F) {
                xyc = 1.0F;
            } else if (xyc < -1.0F) {
                xyc = -1.0F;
            }
            a = (float) Math.asin(xyc);
            if (Float.isNaN(a)) {
                a = 0.0F;
            }
        } else {
            c = (float) Math.atan2(-m02, m00);
            a = 0.0F;
        }
        a = (float) Math.toDegrees(a);
        b = (float) Math.toDegrees(b);
        c = (float) Math.toDegrees(c);
        if (m11 < 0.0F) {
            a = 180.0F - a;
        }
        return new float[] {-b, -c, -a};
    }

    /**
     * Upstream {@code MCH_Lib.worldOrientationQuat}: {@code Ry(-yaw)*Rx(pitch)*Rz(roll)}, the
     * rotation the renderer applies to the body.
     */
    public static Quaternionf worldOrientationQuat(float yaw, float pitch, float roll) {
        return new Quaternionf()
                .rotateY((float) Math.toRadians(-yaw))
                .rotateX((float) Math.toRadians(pitch))
                .rotateZ((float) Math.toRadians(roll));
    }

    /** Upstream {@code MCH_Lib.worldEuler}: {pitch, yaw, roll}, the inverse of {@link #worldOrientationQuat}. */
    public static float[] worldEuler(Quaternionf q) {
        Vector3f e = q.getEulerAnglesYXZ(new Vector3f());
        return new float[] {
                (float) Math.toDegrees(e.x),
                (float) -Math.toDegrees(e.y),
                (float) Math.toDegrees(e.z)
        };
    }
}
