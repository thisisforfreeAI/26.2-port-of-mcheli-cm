package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.config.ClientConfig.EffectBudget;

/** Allocation-free decisions shared by custom emitters. No random draws from the game world. */
public final class EffectTuning {
    private EffectTuning() {}

    public static int smokeLimit(EffectBudget quality) {
        return switch (quality) { case LOW -> 384; case BALANCED -> 768; case HIGH -> 1536; };
    }

    public static int flashLimit(EffectBudget quality) {
        return switch (quality) { case LOW -> 48; case BALANCED -> 96; case HIGH -> 192; };
    }

    public static int perTick(EffectBudget quality, boolean flash) {
        int smoke = smokeLimit(quality) / 8;
        return flash ? smoke / 2 : smoke;
    }

    public static int distance(EffectBudget quality) {
        return switch (quality) { case LOW -> 96; case BALANCED -> 192; case HIGH -> 256; };
    }

    /** Stratified thinning keeps nearby trails solid and bounds distant overdraw. */
    public static int stride(double distanceSquared, int particleSetting, boolean flash) {
        if (flash) {
            return 1;
        }
        int distanceStride = distanceSquared > 128 * 128 ? 4 : distanceSquared > 64 * 64 ? 2 : 1;
        return distanceStride * (1 << Math.clamp(particleSetting, 0, 2));
    }

    /** Estimate relative fill cost at the largest expected radius; no projection or allocations needed. */
    public static int coverageCost(float radius, double distanceSquared) {
        if (!Float.isFinite(radius) || !Double.isFinite(distanceSquared)) return 16;
        return (int) Math.clamp(Math.ceil(64.0 * radius * radius / Math.max(1.0, distanceSquared)), 1, 16);
    }

    /** Each family owns a counter, so a cloud cannot starve weapon flashes. */
    public static final class Budget {
        private long tick = Long.MIN_VALUE;
        private int emitted;
        private int sequence;

        public boolean admit(long now, int limit, int stride) {
            return admit(now, limit, stride, 1);
        }

        public boolean admit(long now, int limit, int stride, int cost) {
            if (now != this.tick) {
                this.tick = now;
                this.emitted = 0;
            }
            if (Math.floorMod(this.sequence++, stride) != 0 || cost > limit - this.emitted) {
                return false;
            }
            this.emitted += cost;
            return true;
        }
    }
}
