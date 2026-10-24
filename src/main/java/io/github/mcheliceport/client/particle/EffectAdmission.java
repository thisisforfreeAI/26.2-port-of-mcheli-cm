package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.config.ClientConfig;
import io.github.mcheliceport.config.ClientConfig.EffectBudget;
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
        EffectBudget[] qualities = EffectBudget.values();
        ParticleLimit[] limits = new ParticleLimit[qualities.length];
        for (EffectBudget quality : qualities) {
            limits[quality.ordinal()] = new ParticleLimit(flash
                    ? EffectTuning.flashLimit(quality) : EffectTuning.smokeLimit(quality));
        }
        return limits;
    }

    static Optional<ParticleLimit> limit(boolean flash) {
        return Optional.of((flash ? FLASH_LIMITS : SMOKE_LIMITS)[ClientConfig.get(ClientConfig.EFFECT_BUDGET).ordinal()]);
    }

    /** Reject a whole effect before its block queries; include its extent at the range boundary. */
    static boolean inRange(ClientLevel level, double x, double y, double z, float extent) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != level || mc.getCameraEntity() == null) return false;
        double distance = mc.gameRenderer.mainCamera().position().distanceToSqr(x, y, z);
        double range = EffectTuning.distance(ClientConfig.get(ClientConfig.EFFECT_BUDGET)) + Math.max(0, extent);
        return Double.isFinite(distance) && distance <= range * range;
    }

    static boolean admit(ClientLevel level, double x, double y, double z, boolean isFlash) {
        return admit(level, x, y, z, isFlash, 0);
    }

    static boolean admit(ClientLevel level, double x, double y, double z, boolean isFlash, float radius) {
        return admit(level, x, y, z, isFlash, radius, false, 1);
    }

    /** Only the primary explosion emitter, whose request count is already pressure-scaled. */
    static boolean admitBlastLobe(ClientLevel level, double x, double y, double z, float radius) {
        return admit(level, x, y, z, false, radius, true, 1);
    }

    static boolean depthDetail(ClientLevel level, double x, double y, double z) {
        return io.github.mcheliceport.config.EffectQuality.enhanced()
                && BlastDepthLook.detail(ClientConfig.get(ClientConfig.EFFECT_BUDGET), EffectPerformance.pressure(),
                Minecraft.getInstance().gameRenderer.mainCamera().position().distanceToSqr(x, y, z));
    }

    static boolean admitBlastLobe(ClientLevel level, double x, double y, double z, float radius, int cards) {
        return admit(level, x, y, z, false, radius, true, cards);
    }

    static boolean admitGeometry(ClientLevel level, double x, double y, double z, float radius, int quads) {
        return admit(level, x, y, z, false, radius, false, quads);
    }

    static boolean admitFragment(ClientLevel level, double x, double y, double z, float radius) {
        // A cube contributes three visible faces and pays for its full bounding radius.
        return admit(level, x, y, z, false, radius * 1.733F, false, 3);
    }

    private static boolean admit(ClientLevel level, double x, double y, double z, boolean isFlash,
            float radius, boolean blastLobe, int quads) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != level || mc.getCameraEntity() == null) {
            return false;
        }
        if (world.get() != level) {
            world = new WeakReference<>(level);
            smoke = new EffectTuning.Budget();
            flash = new EffectTuning.Budget();
        }
        EffectBudget quality = ClientConfig.get(ClientConfig.EFFECT_BUDGET);
        double distance = mc.gameRenderer.mainCamera().position().distanceToSqr(x, y, z);
        int range = EffectTuning.distance(quality);
        if (!Double.isFinite(distance) || distance > range * range) {
            return false;
        }
        int pressure = isFlash ? 0 : EffectPerformance.pressure();
        int stride = EffectTuning.admissionStride(distance, mc.options.particles().get().ordinal(), isFlash,
                pressure, blastLobe);
        int cap = Math.max(16, EffectTuning.perTick(quality, isFlash) / (1 + pressure));
        int cost = isFlash ? 1 : EffectTuning.coverageCost(radius, distance) * quads;
        return (isFlash ? flash : smoke).admit(level.getGameTime(), cap, stride, cost);
    }
}
