package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.config.ClientConfig.EffectBudget;
import io.github.mcheliceport.effect.BlastStyle;
import java.util.List;
import net.minecraft.core.BlockPos;

/** Approximate the reported affected footprint without reading terrain or sorting the packet. */
public final class ExplosionCraterScale {
    private ExplosionCraterScale() {}
    public static float footprint(double x, double z, List<BlockPos> affected) {
        if (affected.isEmpty() || !Double.isFinite(x) || !Double.isFinite(z)) return 0;
        int[] bins = new int[64];
        int samples = Math.min(4096, affected.size());
        for (int i = 0; i < samples; i++) {
            BlockPos p = affected.get((int) ((long) i * affected.size() / samples));
            double distance = Math.hypot(p.getX() + 0.5 - x, p.getZ() + 0.5 - z);
            bins[(int) Math.clamp(Math.ceil(distance * 2), 0, 63)]++;
        }
        // A few distant ray tips should not turn a small impact into a giant cloud.
        int target = (samples * 9 + 9) / 10, accumulated = 0;
        for (int i = 0; i < bins.length; i++) if ((accumulated += bins[i]) >= target) return i * 0.5F;
        return 0;
    }

    public static float cloudRadius(float nominal, float footprint, BlastStyle style, EffectBudget quality) {
        if (style == BlastStyle.CANNON || !Float.isFinite(footprint) || footprint <= 0) return nominal;
        float multiplier = quality == EffectBudget.LOW ? 1.25F : 2.5F;
        float cap = quality == EffectBudget.LOW ? 10 : quality == EffectBudget.HIGH ? 22 : 16;
        return Math.max(nominal, Math.min(Math.min(cap, nominal * multiplier), footprint * 0.85F));
    }

    public static float groundReach(float nominal, float footprint) {
        return Float.isFinite(footprint) ? Math.max(nominal, Math.min(24, footprint * 1.1F)) : nominal;
    }
}
