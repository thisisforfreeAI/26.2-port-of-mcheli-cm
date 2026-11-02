package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.config.ClientConfig.EffectBudget;

/** Bounded, client-only rotor-wash decisions. Distances are blocks; time is client ticks. */
public final class RotorWashLook {
    public enum Surface {
        GRASS(0.22F, 0.45F), SOLID(0.14F, 0.3F), SOIL(0.4F, 0.7F),
        SAND(0.6F, 1), SNOW(0.45F, 0.85F), WATER(0.36F, 0.9F);
        public final float opacity, density;
        Surface(float opacity, float density) { this.opacity = opacity; this.density = density; }
    }

    private RotorWashLook() {}

    public static float scale(float contentScale) {
        return Float.isFinite(contentScale) && contentScale > 0.01F ? Math.clamp(contentScale, 0.3F, 2) : 0;
    }

    public static int reach(float scale) { return Math.min(28, Math.max(12, Math.round(12 + scale * 8))); }

    public static float strength(double height, float scale, double throttle, double speed) {
        if (!Double.isFinite(height) || !Double.isFinite(throttle) || !Double.isFinite(speed) || height < 0 || scale <= 0) return 0;
        float power = smooth((float) Math.clamp((throttle - 0.1) / 0.7, 0, 1));
        float altitude = smooth((float) Math.clamp((reach(scale) - height) / (reach(scale) * 0.75), 0, 1));
        return (float) (power * altitude / (1 + Math.max(0, speed) * 1.5));
    }

    public static int period(EffectBudget budget, int pressure) {
        return (budget == EffectBudget.LOW ? 3 : budget == EffectBudget.BALANCED ? 2 : 1) * (1 + Math.clamp(pressure, 0, 3));
    }

    public static int candidates(EffectBudget budget) { return budget == EffectBudget.LOW ? 1 : 2; }
    public static int range(EffectBudget budget) { return budget == EffectBudget.LOW ? 48 : budget == EffectBudget.BALANCED ? 72 : 96; }
    public static float radius(float scale, double height) { return (float) Math.clamp(scale * 4 + height * 0.12, 2, 10); }
    public static float puffRadius(float scale) { return Math.clamp(0.85F + scale * 0.5F, 1, 1.8F); }
    public static int debrisCount(Surface surface, float strength, EffectBudget budget, int pressure) {
        if (!Float.isFinite(strength) || strength < 0.15F || pressure >= 2
                || surface == Surface.SOLID || surface == Surface.SOIL) return 0;
        return budget != EffectBudget.LOW && strength > 0.65F ? 2 : 1;
    }
    /** Stable loose patches, with a slow gust: no extra samples or complete dust ring. */
    public static float patch(double x,double z,int id,long tick) {
        double phase=x*.73+z*.47+id*.61;
        double islands=.5+.5*Math.sin(phase)*Math.cos(x*.29-z*.83+id);
        double gust=.86+.14*Math.sin(Math.floorMod(tick,24000L)*.065+id*.37);
        return (float)((.48+.52*islands)*gust);
    }
    public static float fade(float age, int lifetime) {
        return smooth(Math.clamp(age / 4, 0, 1)) * smooth(Math.clamp((lifetime - age) / 10, 0, 1));
    }
    private static float smooth(float x) { return x * x * (3 - 2 * x); }
}
