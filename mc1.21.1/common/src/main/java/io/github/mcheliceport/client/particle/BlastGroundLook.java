package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.config.ClientConfig.EffectBudget;
import io.github.mcheliceport.effect.BlastStyle;

/** Cosmetic ground response. No world queries or changes to weapon damage. */
public final class BlastGroundLook {
    public enum Surface {
        SAND(0.84F, 1.15F, 1.1F, 32), SOIL(0.82F, 1, 0.9F, 28),
        SNOW(0.68F, 1.1F, 0.75F, 36), SOLID(0.48F, 0.75F, 0.8F, 22),
        WOOD(0.62F, 0.9F, 0.85F, 26);
        public final float opacity, radiusScale, speedScale;
        public final int life;
        Surface(float opacity, float radiusScale, float speedScale, int life) {
            this.opacity = opacity; this.radiusScale = radiusScale;
            this.speedScale = speedScale; this.life = life;
        }
    }

    private BlastGroundLook() {}

    public static int requests(float power, BlastStyle style, EffectBudget budget, int pressure) {
        if (!Float.isFinite(power) || power < 2 || style == BlastStyle.CANNON
                || budget == EffectBudget.LOW || pressure >= 2) return 0;
        if (ExplosionLook.broad(style) && power >= 3) return ExplosionLook.dustCount(power, style, budget, pressure);
        return (budget == EffectBudget.HIGH ? 5 : 3) / (1 + Math.clamp(pressure, 0, 1));
    }

    /** Replace soil-jet slots with chips on hard surfaces, without adding requests. */
    public static boolean fragmentSlot(int slot, int count, boolean broad, boolean jet, Surface surface) {
        return count > 1 && (broad ? slot % 3 == 2 || surface == Surface.SOLID && jet
                : slot == count - 1 || surface == Surface.SOLID && jet);
    }

    public static float dustRadius(float power, BlastStyle style, Surface surface) {
        float p = Float.isFinite(power) ? Math.clamp(power, 0, 16) : 0;
        float scale = ExplosionLook.broad(style) ? 1 : 0.45F;
        return Math.min(4, (0.9F + p * 0.16F) * surface.radiusScale * scale);
    }

    public static float fragmentRadius(float power, BlastStyle style, Surface surface) {
        float p = Float.isFinite(power) ? Math.clamp(power, 0, 16) : 0;
        return Math.min(0.24F, 0.07F + p * 0.012F) * (ExplosionLook.broad(style) ? 1 : 0.65F)
                * (surface == Surface.SNOW ? 0.65F : 1);
    }

    public static int fragmentTickCap(EffectBudget budget, int pressure) {
        if (budget == EffectBudget.LOW || pressure >= 2) return 0;
        return (budget == EffectBudget.HIGH ? 10 : 6) / (1 + Math.clamp(pressure, 0, 1));
    }

    public static int fragmentLiveCap(EffectBudget budget) {
        return budget == EffectBudget.LOW ? 0 : budget == EffectBudget.HIGH ? 72 : 36;
    }

    public static int surfaceReadCap(EffectBudget budget) {
        return budget == EffectBudget.LOW ? 0 : budget == EffectBudget.HIGH ? 192 : 96;
    }

    public static float fragmentScale(float age, int life) {
        return EffectLook.smoothstep(Math.clamp((life - age) / 6, 0, 1));
    }

    public static boolean hotFragment(float power, BlastStyle style, Surface surface, int slot) {
        return Float.isFinite(power) && power >= 8 && style == BlastStyle.INCENDIARY
                && surface == Surface.SOLID && slot % 2 == 0;
    }
}
