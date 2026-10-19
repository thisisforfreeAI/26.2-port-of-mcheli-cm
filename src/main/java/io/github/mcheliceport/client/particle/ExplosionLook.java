package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.config.ClientConfig.EffectBudget;
import io.github.mcheliceport.effect.BlastStyle;

/** Cosmetic footprint/timing policies; gameplay and the original smoke emitter remain unchanged. */
public final class ExplosionLook {
    private ExplosionLook() {}

    public static boolean broad(BlastStyle style) {
        return style == BlastStyle.HEAVY || style == BlastStyle.INCENDIARY;
    }

    public static float coreRadius(float power, BlastStyle style) {
        if (!Float.isFinite(power) || power < 1 || style == BlastStyle.CANNON) return 0;
        return broad(style) ? Math.clamp(power * 0.25F, 0.6F, 3.0F)
                : Math.clamp(power * 0.16F, 0.25F, style == BlastStyle.PENETRATOR ? 0.8F : 1.2F);
    }

    public static int coreLife(BlastStyle style) { return broad(style) ? 7 : 4; }

    /** Change only the fire tint's duration; smoke lifetime and motion are unchanged. */
    public static float coolingShare(BlastStyle style) {
        return switch (style) {
            case BLAST, PENETRATOR -> 0.18F;
            case INCENDIARY -> 0.45F;
            default -> 0.35F;
        };
    }

    public static int dustCount(float power, BlastStyle style, EffectBudget budget, int pressure) {
        if (!Float.isFinite(power) || power < 3 || !broad(style) || budget == EffectBudget.LOW || pressure >= 2) return 0;
        int cap = budget == EffectBudget.HIGH ? 10 : 6;
        return Math.min(cap, (int) Math.min(power * 1.5F, cap)) / (1 + Math.clamp(pressure, 0, 1));
    }

    private static float strength(float power, BlastStyle style) {
        if (!Float.isFinite(power) || style != BlastStyle.HEAVY && style != BlastStyle.INCENDIARY) return 0;
        return Math.clamp((power - 3.0F) / 9.0F, 0, 1);
    }

    public static float puffSize(float original, float power, BlastStyle style, EffectBudget budget) {
        if ((style == BlastStyle.BLAST || style == BlastStyle.PENETRATOR) && Float.isFinite(power) && power >= 2)
            return original * (budget == EffectBudget.LOW ? 1.1F : 1.3F);
        float boost = switch (budget) { case LOW -> 0.15F; case BALANCED -> 0.5F; case HIGH -> 0.75F; };
        return original * (1 + strength(power, style) * boost);
    }

    /** Relative to nominal size; the existing diffusion ceiling is twice nominal size. */
    public static float startScale(float power, BlastStyle style, EffectBudget budget) {
        if ((style == BlastStyle.BLAST || style == BlastStyle.PENETRATOR) && Float.isFinite(power) && power >= 2)
            return 0.32F;
        float boost = switch (budget) { case LOW -> 0.2F; case BALANCED -> 0.4F; case HIGH -> 0.5F; };
        return 0.2F + strength(power, style) * boost;
    }

    public static float glowRadius(float power, BlastStyle style, EffectBudget budget) {
        if (!Float.isFinite(power) || power <= 0) return 0;
        float cap = switch (budget) { case LOW -> 12; case BALANCED -> 16; case HIGH -> 18; };
        float strength = strength(power, style);
        return Math.min(power * (1.2F + 0.3F * strength), 12 + (cap - 12) * strength);
    }
}
