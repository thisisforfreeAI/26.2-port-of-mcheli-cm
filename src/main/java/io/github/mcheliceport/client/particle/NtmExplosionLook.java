package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.config.ClientConfig.EffectBudget;

/** Size mapping for the native NTM particles, independent of damage and crater generation. */
public final class NtmExplosionLook {
    private NtmExplosionLook() {}
    /** The native Vulkan instancing path crashed in Mesa validation; keep its portable group. */
    public static boolean acceleratedBackend(String backend) {return "OpenGL".equalsIgnoreCase(backend);}
    public record Profile(boolean small, int clouds, float scale, float speed, float wave, int life) {
        public int faces() { return small ? 1 : 10; }
    }
    public static Profile profile(float power, float footprint) {
        if (!Float.isFinite(power) || power <= 0) return new Profile(true, 0, 0, 0, 0, 0);
        float ground = Float.isFinite(footprint) ? Math.clamp(footprint, 0, 31.5F) : 0;
        if (power < 4 && ground < 8)
            return new Profile(true, Math.clamp((int)Math.ceil(power * 3), 2, 10),
                    Math.max(0.45F, Math.max(Math.min(2.8F, power * 0.7F), ground * 0.5F)),
                    Math.min(0.5F, power * 0.15F), 0, 35);
        boolean large = power >= 8 || ground >= 12;
        return new Profile(false, large ? 30 : 15,
                Math.max(large ? 6.5F : 5F, Math.min(12, ground * 0.55F)),
                large ? 2 : 1, large ? 65 : 45, 89);
    }
    public static int liveFaces(EffectBudget quality) {
        return switch (quality) {case LOW -> 240; case BALANCED -> 1920; case HIGH -> 3840;};
    }
    public static int tickFaces(EffectBudget quality, int pressure) {
        int base = switch (quality) {case LOW -> 80; case BALANCED -> 400; case HIGH -> 800;};
        return base / (1 + Math.clamp(pressure, 0, 3));
    }
    public static int clouds(Profile profile, EffectBudget quality, int pressure, double distanceSquared, int setting) {
        if (profile.clouds() == 0) return 0;
        int count = Math.max(1, profile.clouds() / EffectTuning.stride(distanceSquared, setting, false));
        if (quality == EffectBudget.LOW) count = Math.min(count, profile.small() ? 4 : 6);
        return Math.max(1, count / (1 + Math.clamp(pressure, 0, 3)));
    }
}
