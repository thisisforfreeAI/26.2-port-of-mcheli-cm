package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.config.ClientConfig.EffectBudget;
import io.github.mcheliceport.effect.BlastStyle;

/** Secondary pressure-front dust, always below primary fire/smoke in admission priority. */
public final class BlastRingLook {
    private BlastRingLook() {}
    public static int sectors(float power, BlastStyle style, EffectBudget quality, int pressure) {
        if (!Float.isFinite(power) || power < 4 || style == BlastStyle.CANNON
                || style == BlastStyle.PENETRATOR || quality == EffectBudget.LOW || pressure >= 1) return 0;
        return quality == EffectBudget.HIGH ? 14 : 10;
    }
    public static int tickCap(EffectBudget quality) { return quality == EffectBudget.HIGH ? 20 : 12; }
    public static float reach(float power) { return Float.isFinite(power) ? Math.clamp(power * 0.65F, 2.5F, 10) : 0; }
    public static float width(float power) { return Math.min(1.5F, 0.5F + reach(power) * 0.08F); }
    public static float travel(float age) {
        float t = Math.clamp(age / 10, 0, 1);
        return 1 - (1 - t) * (1 - t);
    }
    public static float fade(float age, int life) {
        return Math.clamp(age / 2, 0, 1) * (float) Math.pow(Math.clamp(1 - age / life, 0, 1), 2);
    }
}
