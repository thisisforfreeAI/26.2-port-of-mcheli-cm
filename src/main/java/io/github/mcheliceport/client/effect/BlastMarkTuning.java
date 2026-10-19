package io.github.mcheliceport.client.effect;

import io.github.mcheliceport.config.ClientConfig.EffectBudget;
import io.github.mcheliceport.effect.BlastStyle;

/** Bounded cosmetic footprint and storage policy. All distances are game blocks, not real-world yields. */
public final class BlastMarkTuning {
    private BlastMarkTuning() {}

    public static float radius(float size, BlastStyle style, EffectBudget budget) {
        if (!Float.isFinite(size) || size <= 0) return 0;
        float scale = switch (style) {
            case CANNON -> 0.4F;
            case PENETRATOR -> 0.45F;
            case BLAST -> 0.65F;
            case HEAVY -> 1.15F;
            case INCENDIARY -> 1.25F;
        };
        float cap = switch (budget) { case LOW -> 2.5F; case BALANCED -> 4.0F; case HIGH -> 5.0F; };
        if (style == BlastStyle.HEAVY || style == BlastStyle.INCENDIARY) {
            // A large bomb must not hit the same small footprint ceiling as an ordinary rocket.
            // Even the largest flat footprint fits the existing 256-piece per-mark cap.
            cap = switch (budget) { case LOW -> 4.0F; case BALANCED -> 6.0F; case HIGH -> 7.0F; };
        }
        return Math.clamp(size * scale, 0.6F, cap);
    }

    public static int quadLimit(EffectBudget budget) {
        return switch (budget) { case LOW -> 128; case BALANCED -> 384; case HIGH -> 768; };
    }

    public static int markLimit(EffectBudget budget) {
        return switch (budget) { case LOW -> 8; case BALANCED -> 24; case HIGH -> 48; };
    }

    public static int range(EffectBudget budget) {
        return switch (budget) { case LOW -> 64; case BALANCED -> 96; case HIGH -> 128; };
    }

    public static int lifetime(EffectBudget budget) {
        return switch (budget) { case LOW -> 1200; case BALANCED -> 2400; case HIGH -> 3600; };
    }

    public static float alpha(long age, int lifetime) {
        if (age < 0 || age >= lifetime) return 0;
        return Math.min(1, (lifetime - age) / 200.0F);
    }

    /** Only the initial few frames illuminate the fitted surface; no saved/world lighting changes. */
    public static float flashAlpha(float age, int duration) {
        if (age < 0 || age >= duration) return 0;
        float t = 1 - age / duration;
        return t * t;
    }

    public static float flashFalloff(double dx, double dy, double dz, float radius) {
        double t = Math.clamp(1 - (dx * dx + dy * dy + dz * dz) / (radius * radius), 0, 1);
        return (float) (t * t);
    }

    /** Higher airbursts leave a smaller mark; a blast outside reach leaves none. */
    public static boolean reached(float size, double dx, double dy, double dz) {
        double reach = Math.min(16, Math.max(0, size) * 1.5);
        return Double.isFinite(reach) && dx * dx + dy * dy + dz * dz <= reach * reach;
    }

    /** Quarter turns preserve the pixel grid and continuity between neighbouring surface tiles. */
    public static float[] uv(double dx, double dz, float radius, int turn) {
        double u = dx / (2 * radius), v = dz / (2 * radius);
        return switch (turn & 3) {
            case 1 -> new float[]{(float) (0.5 - v), (float) (0.5 + u)};
            case 2 -> new float[]{(float) (0.5 - u), (float) (0.5 - v)};
            case 3 -> new float[]{(float) (0.5 + v), (float) (0.5 - u)};
            default -> new float[]{(float) (0.5 + u), (float) (0.5 + v)};
        };
    }
}
