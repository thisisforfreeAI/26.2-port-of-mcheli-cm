package io.github.mcheliceport.effect;

import net.minecraft.world.phys.Vec3;

/** Three cosmetic direction bytes; zero means no projectile direction was available. */
public record BlastDirection(byte x, byte y, byte z) {
    public static final BlastDirection NONE = new BlastDirection((byte) 0, (byte) 0, (byte) 0);

    public static BlastDirection from(Vec3 velocity) {
        double max = Math.max(Math.abs(velocity.x), Math.max(Math.abs(velocity.y), Math.abs(velocity.z)));
        if (!Double.isFinite(max) || max < 1.0E-8) return NONE;
        // Scale first so even extreme finite velocities cannot overflow normalization.
        Vec3 unit = velocity.scale(1 / max).normalize();
        return new BlastDirection((byte) Math.round(unit.x * 127), (byte) Math.round(unit.y * 127),
                (byte) Math.round(unit.z * 127));
    }

    /** Ejecta follows the incoming heading but lifts out of the ground at steep impacts. */
    public Vec3 burstAxis() {
        return equals(NONE) ? Vec3.ZERO : new Vec3(x * 0.8, Math.max(44, Math.abs((int) y)), z * 0.8).normalize();
    }
}
