package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.config.ClientConfig;
import io.github.mcheliceport.config.ClientConfig.EffectQuality;
import java.lang.ref.WeakReference;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleLimit;

/** Client-thread admission, before allocating a custom particle. The engine owns live counts. */
final class EffectAdmission {
    private static WeakReference<ClientLevel> world = new WeakReference<>(null);
    private static EffectTuning.Budget smoke = new EffectTuning.Budget();
    private static EffectTuning.Budget flash = new EffectTuning.Budget();
    private static final ParticleLimit[] SMOKE_LIMITS = limits(false);
    private static final ParticleLimit[] FLASH_LIMITS = limits(true);

    private EffectAdmission() {}

    private static ParticleLimit[] limits(boolean flash) {
        EffectQuality[] qualities = EffectQuality.values();
        ParticleLimit[] limits = new ParticleLimit[qualities.length];
        for (EffectQuality quality : qualities) {
            limits[quality.ordinal()] = new ParticleLimit(flash
                    ? EffectTuning.flashLimit(quality) : EffectTuning.smokeLimit(quality));
        }
        return limits;
    }

    static Optional<ParticleLimit> limit(boolean flash) {
        return Optional.of((flash ? FLASH_LIMITS : SMOKE_LIMITS)[ClientConfig.get(ClientConfig.EFFECT_QUALITY).ordinal()]);
    }

    static boolean admit(ClientLevel level, double x, double y, double z, boolean isFlash) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != level || mc.getCameraEntity() == null) {
            return false;
        }
        if (world.get() != level) {
            world = new WeakReference<>(level);
            smoke = new EffectTuning.Budget();
            flash = new EffectTuning.Budget();
        }
        EffectQuality quality = ClientConfig.get(ClientConfig.EFFECT_QUALITY);
        double distance = mc.gameRenderer.mainCamera().position().distanceToSqr(x, y, z);
        int range = EffectTuning.distance(quality);
        if (!Double.isFinite(distance) || distance > range * range) {
            return false;
        }
        return (isFlash ? flash : smoke).admit(level.getGameTime(), EffectTuning.perTick(quality, isFlash),
                EffectTuning.stride(distance, mc.options.particles().get().ordinal(), isFlash));
    }
}
