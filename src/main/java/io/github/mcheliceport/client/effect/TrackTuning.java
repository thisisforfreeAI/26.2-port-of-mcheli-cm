package io.github.mcheliceport.client.effect;

import io.github.mcheliceport.config.ClientConfig.EffectBudget;

/** Shared cosmetic track limits; all vehicles share one client cache and tick allowance. */
public final class TrackTuning {
    private TrackTuning() {}
    public static int quads(EffectBudget q) { return switch (q) { case LOW -> 128; case BALANCED -> 512; case HIGH -> 1024; }; }
    public static int reads(EffectBudget q) { return switch (q) { case LOW -> 32; case BALANCED -> 64; case HIGH -> 96; }; }
    public static int fits(EffectBudget q) { return switch (q) { case LOW -> 12; case BALANCED -> 32; case HIGH -> 64; }; }
    public static int checks(EffectBudget q) { return switch (q) { case LOW -> 4; case BALANCED -> 8; case HIGH -> 12; }; }
    public static int range(EffectBudget q) { return switch (q) { case LOW -> 32; case BALANCED -> 64; case HIGH -> 96; }; }
    public static int lifetime(EffectBudget q) { return switch (q) { case LOW -> 400; case BALANCED -> 900; case HIGH -> 1200; }; }
    public static float alpha(long age, int life) {
        if (age < 0 || age >= life) return 0;
        return Math.min(1, (age + 1) / 2F) * Math.min(1, (life - age) / 100F);
    }
}
