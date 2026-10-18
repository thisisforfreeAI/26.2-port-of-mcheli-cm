package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.config.ClientConfig.EffectBudget;
import io.github.mcheliceport.effect.BlastStyle;

/** Cosmetic bomb scaling; counts, lifetime, trajectories and random draws stay with the emitter. */
public final class ExplosionLook {
    private ExplosionLook() {}

    private static float strength(float power, BlastStyle style) {
        if (!Float.isFinite(power) || style != BlastStyle.HEAVY && style != BlastStyle.INCENDIARY) return 0;
        return Math.clamp((power - 3.0F) / 9.0F, 0, 1);
    }

    public static float puffSize(float original, float power, BlastStyle style, EffectBudget budget) {
        float boost = switch (budget) { case LOW -> 0.15F; case BALANCED -> 0.5F; case HIGH -> 0.75F; };
        return original * (1 + strength(power, style) * boost);
    }

    /** Relative to nominal size; the existing diffusion ceiling is twice nominal size. */
    public static float startScale(float power, BlastStyle style, EffectBudget budget) {
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
