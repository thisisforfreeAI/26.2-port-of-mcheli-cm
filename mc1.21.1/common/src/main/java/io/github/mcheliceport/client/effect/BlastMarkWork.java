package io.github.mcheliceport.client.effect;

import io.github.mcheliceport.config.ClientConfig.EffectBudget;

/** One shared terrain-work allowance per actual client world tick, independent of impact count. */
final class BlastMarkWork {
    private long tick = Long.MIN_VALUE;
    private int readLimit, rayLimit, columnLimit, checkLimit;
    int reads, rays, columns, checks;

    void begin(long now, EffectBudget quality) {
        if (tick == now) return;
        tick = now;
        reads = rays = columns = checks = 0;
        readLimit = switch (quality) { case LOW -> 128; case BALANCED -> 256; case HIGH -> 384; };
        rayLimit = switch (quality) { case LOW -> 16; case BALANCED -> 32; case HIGH -> 48; };
        columnLimit = switch (quality) { case LOW -> 24; case BALANCED -> 48; case HIGH -> 64; };
        checkLimit = switch (quality) { case LOW -> 16; case BALANCED -> 40; case HIGH -> 80; };
    }

    boolean read() { if (reads >= readLimit) return false; reads++; return true; }
    boolean ray() { if (rays >= rayLimit) return false; rays++; return true; }
    boolean column() { if (columns >= columnLimit) return false; columns++; return true; }
    boolean check() { if (checks >= checkLimit) return false; checks++; return true; }
    void clear() { tick = Long.MIN_VALUE; reads = rays = columns = checks = 0; }
}
