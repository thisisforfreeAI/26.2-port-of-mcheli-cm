package io.github.mcheliceport.client.particle;

/** Cosmetic engine state: no fuel, damage, physics or world random numbers are changed. */
public final class VehicleAtmosphereLook {
    private float spool;
    private int startup;
    private boolean initialized, running;

    public void tick(double throttle, boolean powered) {
        float target = powered && Double.isFinite(throttle) ? (float) Math.clamp(throttle, 0, 1) : 0;
        boolean active = target > 0.015F;
        if (!initialized) {
            initialized = true;
            spool = target;
            running = active; // Watching an already running engine must not replay a cold start.
            return;
        }
        if (active && !running) startup = 80;
        else if (!active) startup = 0;
        else if (startup > 0) startup--;
        running = active;
        spool += Math.clamp(target - spool, -0.025F, 0.0125F);
    }

    public float spool() { return spool; }
    public boolean justStarted() { return startup == 80; }
    public float startup() { return startup / 80F; }
    public float whinePitch() { return 0.55F + spool * 1.1F; }
    public float whineVolume() { return startup() * (0.12F + spool * 0.28F); }
    public float heat() { return EngineExhaustLook.strength(spool); }

    public static float damage(int hp, int maxHp) {
        return maxHp > 0 ? (float) Math.clamp((0.65 - (double) hp / maxHp) / 0.65, 0, 1) : 0;
    }

    public static boolean smokePulse(long tick, int id, float damage) {
        // Uneven bursts, rather than a solid smoke hose. Stable on every observer.
        long phase = Math.floorMod(tick + (long) id * 17, 41);
        return damage > 0 && phase < 4 + (int) (damage * 18);
    }

    public static int casingLimit(io.github.mcheliceport.config.ClientConfig.EffectBudget budget) {
        return switch (budget) { case LOW -> 64; case BALANCED -> 128; case HIGH -> 192; };
    }
    public static int casingsPerTick(io.github.mcheliceport.config.ClientConfig.EffectBudget budget) {
        return switch (budget) { case LOW -> 4; case BALANCED -> 8; case HIGH -> 12; };
    }
}
