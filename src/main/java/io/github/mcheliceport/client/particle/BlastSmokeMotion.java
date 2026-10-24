package io.github.mcheliceport.client.particle;

/** Cosmetic wind in blocks/tick. No terrain reads, allocations, or entity scans. */
final class BlastSmokeMotion {
    private BlastSmokeMotion() {}

    // One slowly changing weather vector for every explosion in this client world.
    private static long sampledTick = Long.MIN_VALUE;
    private static double windX, windZ;
    static double windX(long tick) { sample(tick); return windX; }
    static double windZ(long tick) { sample(tick); return windZ; }
    private static void sample(long tick) {
        if (sampledTick == tick) return;
        sampledTick = tick;
        double phase = Math.floorMod(tick, 24000L) * (Math.PI * 2 / 24000.0);
        windX = 0.024 + Math.sin(phase) * 0.008;
        windZ = 0.012 + Math.cos(phase) * 0.006;
    }

    /** Ejection dominates first; drag then converges on shared wind rather than stopping. */
    static double advect(double velocity, double wind, double drag, int age) {
        double coupling = Math.clamp((age - 4) / 24.0, 0, 1);
        return velocity * drag + wind * (1 - drag) * coupling;
    }

    /** Wisps disperse first; cores keep the original bounded lifetime. */
    static int lifetime(int coreLife, boolean wisp) {
        return wisp ? Math.max(16, coreLife * 2 / 3) : coreLife;
    }
}
