package io.github.mcheliceport.client.render;

import io.github.mcheliceport.config.ClientConfig.EffectBudget;

/** Tunes vanilla's nearby surface-fitted shadow; no new terrain sampler or draw pass. */
public final class VehicleContactLook {
    private VehicleContactLook() {}
    public static float radius(float bodyWidth, double wheelReach, EffectBudget budget, int pressure) {
        if (budget == EffectBudget.LOW || pressure > 0) return 2;
        if (!Float.isFinite(bodyWidth) || !Double.isFinite(wheelReach)) return 2;
        float cap = budget == EffectBudget.HIGH ? 3 : 2.5F;
        double footprint = Math.max(bodyWidth * 0.5, Math.max(0, wheelReach) * 0.8);
        return (float) Math.clamp(footprint, 2, cap);
    }
}
