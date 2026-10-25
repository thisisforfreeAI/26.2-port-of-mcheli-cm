package io.github.mcheliceport.client.render;

import io.github.mcheliceport.config.ClientConfig.EffectBudget;

/** Tunes vanilla's nearby surface-fitted shadow; no new terrain sampler or draw pass. */
public final class VehicleContactLook {
    private VehicleContactLook() {}
    public static float distanceWeight(double distanceSquared) {
        if(!Double.isFinite(distanceSquared)||distanceSquared>=256)return 0;
        float t=(float)Math.clamp((16-Math.sqrt(Math.max(0,distanceSquared)))/6,0,1);
        return t*t*(3-2*t);
    }
    public static float radius(float bodyWidth, double wheelReach, EffectBudget budget, int pressure) {
        if (budget == EffectBudget.LOW || pressure > 0) return 2;
        if (!Float.isFinite(bodyWidth) || !Double.isFinite(wheelReach)) return 2;
        float cap = budget == EffectBudget.HIGH ? 3 : 2.5F;
        double footprint = Math.max(bodyWidth * 0.5, Math.max(0, wheelReach) * 0.8);
        return (float) Math.clamp(footprint, 2, cap);
    }
}
