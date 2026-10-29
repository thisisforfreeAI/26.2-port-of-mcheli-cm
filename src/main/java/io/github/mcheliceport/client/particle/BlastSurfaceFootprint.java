package io.github.mcheliceport.client.particle;

import java.util.List;
import net.minecraft.core.BlockPos;

/** Bounded packet-only angular footprint. Never ray marches, reads terrain or changes blocks. */
final class BlastSurfaceFootprint {
    static final int SECTORS = 12;
    static final BlastSurfaceFootprint EMPTY = new BlastSurfaceFootprint(new float[SECTORS], new int[SECTORS]);
    private final float[] reach;
    private final int[] top;

    private BlastSurfaceFootprint(float[] reach, int[] top) { this.reach = reach; this.top = top; }

    static BlastSurfaceFootprint sample(double x, double z, List<BlockPos> affected) {
        if (affected.isEmpty() || !Double.isFinite(x) || !Double.isFinite(z)) return EMPTY;
        int[][] bins = new int[SECTORS][64];
        int[] counts = new int[SECTORS], top = new int[SECTORS];
        java.util.Arrays.fill(top, Integer.MIN_VALUE);
        int samples = Math.min(4096, affected.size());
        for (int i = 0; i < samples; i++) {
            var p = affected.get((int) ((long) i * affected.size() / samples));
            double dx = p.getX() + .5 - x, dz = p.getZ() + .5 - z;
            int sector = sector(Math.atan2(dz, dx));
            bins[sector][(int) Math.clamp(Math.ceil(Math.hypot(dx, dz) * 2), 0, 63)]++;
            counts[sector]++;
            top[sector] = Math.max(top[sector], p.getY() + 1);
        }
        float[] reach = new float[SECTORS];
        for (int s = 0; s < SECTORS; s++) {
            int target = (counts[s] * 9 + 9) / 10, sum = 0;
            if (target == 0) continue;
            for (int bin = 0; bin < 64; bin++) if ((sum += bins[s][bin]) >= target) {
                reach[s] = bin * .5F; break;
            }
        }
        return new BlastSurfaceFootprint(reach, top);
    }

    private static int sector(double angle) {
        return Math.floorMod((int) Math.floor((angle + Math.PI) * SECTORS / (Math.PI * 2)), SECTORS);
    }
    float reach(double angle, float fallback) {
        float value = reach[sector(angle)];
        // Empty sectors stay empty when a packet describes a one-sided crater.
        return this == EMPTY ? fallback : Math.min(24, value * 1.08F);
    }
    double surfaceTop(double angle, double fallback) {
        int value = top[sector(angle)];
        return this == EMPTY || value == Integer.MIN_VALUE ? fallback : Math.clamp(value, fallback - 8, fallback + 8);
    }
}
