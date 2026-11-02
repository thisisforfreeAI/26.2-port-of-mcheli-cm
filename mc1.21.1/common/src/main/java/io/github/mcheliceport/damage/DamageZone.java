package io.github.mcheliceport.damage;

import net.minecraft.world.phys.Vec3;

/**
 * Where a component sits, as a box in the vehicle's unanimated body coordinates. {@code share} is
 * the part of a hit's hull damage it takes; {@code health} its whole health as a share of the
 * vehicle's max HP.
 */
public record DamageZone(ComponentKind kind, int index, Vec3 center, Vec3 halfSize, String partName, float share,
        float health) {

    /** A zone with its kind's damage share and the default health. */
    public DamageZone(ComponentKind kind, int index, Vec3 center, Vec3 halfSize, String partName) {
        this(kind, index, center, halfSize, partName, kind.damageShare, ComponentDamage.HEALTH_SHARE_OF_MAX_HP);
    }

    /** This zone grown by {@code slack} on every side. */
    public DamageZone grown(double slack) {
        return new DamageZone(this.kind, this.index, this.center, this.halfSize.add(slack, slack, slack),
                this.partName, this.share, this.health);
    }

    /** This zone's whole health in hull HP for a vehicle of {@code maxHp}. */
    public float capacity(int maxHp) {
        return Math.max(1.0F, this.health * maxHp);
    }

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
