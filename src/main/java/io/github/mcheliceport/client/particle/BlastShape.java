package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.effect.BlastStyle;
import net.minecraft.world.phys.Vec3;

/** Stable birth choices keep dominant billows when distant detail requests are reduced. */
final class BlastShape {
    private BlastShape() {}
    static boolean wisp(int index) { return index > 1 && index % 3 == 2; }
    static float scale(int index, float variation) {
        float t = Float.isFinite(variation) ? Math.clamp(variation, 0, 1) : 0;
        return wisp(index) ? .28F + .16F * t : index < 2 ? .83F + .14F * t : .54F + .22F * t;
    }
    static float height(BlastStyle style, boolean base, float variation) {
        float t = Math.clamp(variation, 0, 1);
        return style == BlastStyle.PENETRATOR ? (base ? .10F : .45F + t * .45F)
                : ExplosionLook.broad(style) ? (base ? .05F : .18F + t * .30F)
                : base ? .10F : .25F + t * .40F;
    }
    static Vec3 offset(BlastStyle style, Vec3 axis, double x, double z, float radius) {
        if (style == BlastStyle.PENETRATOR) return new Vec3(x * .48, 0, z * .48);
        if (style != BlastStyle.BLAST || axis.lengthSqr() < .001) return new Vec3(x, 0, z);
        double horizontal = Math.hypot(axis.x, axis.z);
        if (horizontal < .05) return new Vec3(x, 0, z);
        double ax = axis.x / horizontal, az = axis.z / horizontal;
        double along = x * ax + z * az;
        double weight = Math.min(1, horizontal / .6);
        return new Vec3(x * (1 - .25 * weight) + ax * (along * .4 + radius * .16) * weight,
                0, z * (1 - .25 * weight) + az * (along * .4 + radius * .16) * weight);
    }
}
