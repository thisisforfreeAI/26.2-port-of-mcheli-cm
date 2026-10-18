package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.config.ClientConfig.EffectBudget;
import io.github.mcheliceport.effect.BlastStyle;

/** Bounds the primary blast independently of affected blocks, including airbursts. */
public final class ExplosionSequence {
    private ExplosionSequence() {}

    /** Rockets/missiles and small bombs keep the earlier smoke-led character. */
    public static boolean smokeLed(float power, BlastStyle style) {
        return !ExplosionLook.broad(style) || power < 4;
    }

    public static int lobes(float power, BlastStyle style, EffectBudget budget, int pressure) {
        if (!Float.isFinite(power) || power <= 0) return 0;
        int cap = switch (budget) { case LOW -> 6; case BALANCED -> 12; case HIGH -> 18; };
        if (style == BlastStyle.CANNON) cap = Math.min(cap, 4);
        int count = Math.min(cap, 3 + (int) Math.min(power, cap));
        return Math.max(2, count / (1 + Math.clamp(pressure, 0, 3)));
    }

    public static float radius(float power, BlastStyle style) {
        if (!Float.isFinite(power) || power <= 0) return 0;
        float scale = ExplosionLook.broad(style) ? 0.6F : style == BlastStyle.PENETRATOR ? 0.25F : 0.36F;
        return Math.clamp(power * scale, 0.25F, ExplosionLook.broad(style) ? 8 : 3.2F);
    }

    public static int life(float power, BlastStyle style) {
        float p = Float.isFinite(power) ? Math.clamp(power, 0, 16) : 0;
        return (ExplosionLook.broad(style) ? 52 : 24) + (int) (p * 3);
    }

    public static int fireTicks(float power, BlastStyle style) {
        float p = Float.isFinite(power) ? Math.clamp(power, 0, 16) : 0;
        return style == BlastStyle.INCENDIARY ? 20 + (int) p : ExplosionLook.broad(style) ? 8 + (int) p : 4 + (int) (p * 0.5F);
    }

    public static float flashRadius(float power, BlastStyle style) {
        return radius(power, style) * 1.5F;
    }

    /** Spend most large-blast requests on smoke, retaining at least one pocket of fire. */
    public static int smokeLobes(int lobes) {
        return Math.max(0, Math.min(lobes - 1, (lobes * 2 + 2) / 3));
    }

    /** Delayed particles are admitted at birth; there is no deferred emitter or spawn queue. */
    public static float cloudGrowth(float age, int delay, boolean fire, boolean base) {
        float t = Math.clamp((age - delay) / (fire ? 6 : base ? 18 : 26), 0, 1);
        float start = fire ? 0.16F : 0.26F;
        return start + (1 - start) * (1 - (1 - t) * (1 - t));
    }

    public static float cloudAlpha(float age, int life, int delay, boolean fire) {
        return cloudAlpha(age, life, delay, fire, 28);
    }

    public static float cloudAlpha(float age, int life, int delay, boolean fire, int fadeTicks) {
        float entrance = Math.clamp((age - delay) / (fire ? 1.5F : 5), 0, 1);
        return entrance * EffectLook.smoothstep(Math.clamp((life - age) / Math.max(1F, fadeTicks), 0, 1));
    }

    public static float flashGrowth(float age, int life) {
        float t = Math.clamp(age / Math.max(1, life), 0, 1);
        return 0.25F + 0.75F * (1 - (1 - t) * (1 - t));
    }

    /** A flame stays bright while hot; the smoke it leaves receives only ambient lighting. */
    public static float fireHeat(float age, int fireTicks) {
        if (fireTicks <= 0) return 0;
        float cooling = Math.clamp((age / fireTicks - 0.45F) / 0.55F, 0, 1);
        return 1 - EffectLook.smoothstep(cooling);
    }

}
