package io.github.mcheliceport.damage;

import net.minecraft.world.phys.Vec3;

/** Where a component sits, as a box in the vehicle's unanimated body coordinates. */
public record DamageZone(ComponentKind kind, int index, Vec3 center, Vec3 halfSize, String partName) {

    /**
     * Where a ray enters this box, as a distance along {@code dir} from {@code from} (both in body
     * coordinates, {@code dir} of length 1), or NaN when it misses. A ray starting inside enters at 0.
     */
    public double entry(Vec3 from, Vec3 dir) {
        double tMin = Double.NEGATIVE_INFINITY;
        double tMax = Double.POSITIVE_INFINITY;
        double[] o = {from.x - this.center.x, from.y - this.center.y, from.z - this.center.z};
        double[] d = {dir.x, dir.y, dir.z};
        double[] h = {this.halfSize.x, this.halfSize.y, this.halfSize.z};
        for (int i = 0; i < 3; i++) {
            if (Math.abs(d[i]) < 1.0E-9) {
                if (Math.abs(o[i]) > h[i]) {
                    return Double.NaN;
                }
                continue;
            }
            double t1 = (-h[i] - o[i]) / d[i];
            double t2 = (h[i] - o[i]) / d[i];
            tMin = Math.max(tMin, Math.min(t1, t2));
            tMax = Math.min(tMax, Math.max(t1, t2));
        }
        if (tMax < tMin || tMax < 0.0) {
            return Double.NaN;
        }
        return Math.max(tMin, 0.0);
    }

    /** Distance from a body-space point to the box, 0 inside. */
    public double distanceTo(Vec3 p) {
        double dx = Math.max(Math.abs(p.x - this.center.x) - this.halfSize.x, 0.0);
        double dy = Math.max(Math.abs(p.y - this.center.y) - this.halfSize.y, 0.0);
        double dz = Math.max(Math.abs(p.z - this.center.z) - this.halfSize.z, 0.0);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
