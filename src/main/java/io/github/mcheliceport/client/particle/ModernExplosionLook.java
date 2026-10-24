package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.config.ClientConfig;
import io.github.mcheliceport.config.EffectQuality;
import io.github.mcheliceport.effect.BlastStyle;

/** Explosion-only presentation preference; does not mutate global Classic/Enhanced settings. */
public final class ModernExplosionLook {
    private ModernExplosionLook() {}
    public static boolean enabled() {
        return active(ExplosionModes.selected() == ClientConfig.ExplosionMode.MODERN, EffectQuality.enhanced(),
                ClientConfig.get(ClientConfig.DEFAULT_EXPLOSION_PARTICLE));
    }
    public static boolean active(boolean requested, boolean enhanced, boolean vanilla) {
        return requested && enhanced && !vanilla;
    }
    public static int fireTicks(int original, BlastStyle style) {
        return style == BlastStyle.INCENDIARY ? original : Math.max(3, Math.min(12, (original * 2 + 2) / 3));
    }
    public static int smallSmokeLobes(int total, BlastStyle style) {
        if (total<=1)return 0;
        return Math.max(0, total - Math.min(total-1, style==BlastStyle.INCENDIARY?3:2));
    }
    public static float craterRadius(float current,float nominal,float footprint,float power,BlastStyle style,ClientConfig.EffectBudget quality) {
        if ((style!=BlastStyle.PENETRATOR && style!=BlastStyle.BLAST) || !Float.isFinite(power) || power<2
                || !Float.isFinite(footprint) || footprint<=0)return current;
        float cap=quality==ClientConfig.EffectBudget.LOW?nominal*1.25F:quality==ClientConfig.EffectBudget.HIGH?22:16;
        return Math.max(current,Math.min(cap,footprint*.75F));
    }
    public static float smokeRadius(float radius, float power) {
        return radius * (power<4 ? 1.15F : 1);
    }
}
