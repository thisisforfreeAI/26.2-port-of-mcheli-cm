package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.config.ClientConfig.EffectBudget;

/** Small per-view allowance for entity halos; the bright physical flare remains when this is shed. */
public final class FlareLook {
    private FlareLook() {}

    public static int haloBudget(EffectBudget budget, int pressure) {
        if (budget == EffectBudget.LOW || pressure >= 2) return 0;
        return (budget == EffectBudget.HIGH ? 48 : 24) / (1 + Math.clamp(pressure, 0, 1));
    }

    public static int range(EffectBudget budget) { return budget == EffectBudget.HIGH ? 96 : 64; }

    public static float flicker(float age, int id) {
        return 0.94F + 0.06F * (float) Math.sin(age * 1.3 + id * 0.73);
    }
}
