package io.github.mcheliceport.client.particle;

/**
 * The M8 look's formulas ({@link io.github.mcheliceport.config.EffectQuality#ENHANCED}), kept free
 * of game classes so they can be unit tested. None of them changes where, when or how many
 * particles spawn.
 */
public final class EffectLook {

    /** Share of a smoke puff's life over which it fades out at the end. */
    static final float FADE_OUT_SHARE = 0.3F;
    /** Ticks over which a smoke puff fades in. */
    static final float FADE_IN_TICKS = 2.0F;
    /** Share of a fireball's life over which it cools from fire to smoke. */
    static final float COOL_SHARE = 0.35F;
    /** Fire colour at full heat (a hot orange); smoke colour once cool. */
    static final float FIRE_R = 1.0F;
    static final float FIRE_G = 0.62F;
    static final float FIRE_B = 0.22F;

    private EffectLook() {
    }

    /**
     * Opacity factor of a smoke puff at {@code age} (with the frame's partial tick) of
     * {@code lifetime}: a short fade in, full in the middle, a smooth fade to nothing at the end.
     * Upstream's puffs appeared and vanished at full opacity.
     */
    public static float smokeAlpha(float age, int lifetime) {
        if (lifetime <= 0) {
            return 1.0F;
        }
        float in = clamp01((age + 1.0F) / FADE_IN_TICKS);
        float left = (lifetime - age) / (lifetime * FADE_OUT_SHARE);
        float out = smoothstep(clamp01(left));
        return in * out;
    }

    /**
     * Heat of a fireball puff: {@code start} at birth, falling smoothly to 0 after
     * {@link #COOL_SHARE} of its life.
     */
    public static float heat(float start, float age, int lifetime) {
        if (start <= 0.0F || lifetime <= 0) {
            return 0.0F;
        }
        float t = clamp01(age / (lifetime * COOL_SHARE));
        return start * (1.0F - smoothstep(t));
    }

    /** {@code smoke} blended toward the fire colour channel {@code fire} by {@code heat}. */
    public static float fireBlend(float smoke, float fire, float heat) {
        return smoke + (fire - smoke) * clamp01(heat);
    }

    /** Block light (0 to 15) a puff of this heat glows with. */
    public static int heatBlockLight(float heat) {
        return Math.round(15.0F * clamp01(heat));
    }

    /**
     * A glow's opacity at {@code age} of {@code lifetime}: full at once, then dying away
     * quadratically, as a flash does.
     */
    public static float glowAlpha(float age, int lifetime) {
        if (lifetime <= 0) {
            return 0.0F;
        }
        float left = clamp01(1.0F - age / lifetime);
        return left * left;
    }

    /** Preserve sampled sky and torch light while the puff itself glows hot. No world light is emitted. */
    public static int smokeLight(int ambient, float heat) {
        int block = Math.max((ambient >> 4) & 15, heatBlockLight(heat));
        return (ambient & 0xF00000) | (block << 4);
    }

    /** Sample once per tick at most; refresh a moving puff or stationary light every four ticks. */
    public static boolean refreshLight(int age, int previousAge, boolean movedBlock) {
        return age != previousAge && (movedBlock || age - previousAge >= 4);
    }

    /** Linear interpolation of a value between ticks. */
    public static float lerp(float partial, float from, float to) {
        return from + (to - from) * partial;
    }

    static float smoothstep(float t) {
        return t * t * (3.0F - 2.0F * t);
    }

    static float clamp01(float v) {
        return v < 0.0F ? 0.0F : v > 1.0F ? 1.0F : v;
    }
}
