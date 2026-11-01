package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.effect.BlastStyle;
import net.minecraft.world.phys.Vec3;

/** A shallow incoming missile makes an elongated initial cloud, before wind takes over. */
public final class ExplosionFootprint {
    private ExplosionFootprint() {}
    public static Vec3 cloudOffset(float power, BlastStyle style, Vec3 axis, double x, double z, float radius) {
        double horizontal = Math.hypot(axis.x, axis.z);
        if (!ExplosionSequence.largeMissile(power, style) || !Double.isFinite(horizontal)
                || horizontal < 1e-4 || !Float.isFinite(radius) || radius <= 0) return new Vec3(x, 0, z);
        double ax = axis.x / horizontal, az = axis.z / horizontal;
        double weight = Math.min(1, horizontal / 0.6);
        double along = x * ax + z * az;
        double crossScale = 1 - 0.2 * weight;
        double reach = along * 0.35 * weight + radius * 0.14 * weight;
        return new Vec3(x * crossScale + ax * reach, 0, z * crossScale + az * reach);
    }
}
