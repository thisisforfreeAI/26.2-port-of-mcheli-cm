package io.github.mcheliceport.client.render;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;

/**
 * Block and sky light at the eight corners of a vehicle's box, which the vehicle shader blends
 * across the hull, so a bomber half inside a hangar is dark inside and lit outside instead of
 * taking one light value from its centre. Corner {@code i} has x from bit 0, y from bit 1 and z
 * from bit 2; {@code light} holds its block and sky light as lightmap coordinates (level × 16).
 */
public record VehicleLightProbe(double minX, double minY, double minZ, float sizeX, float sizeY, float sizeZ,
        float[] light) {

    /** Boxes narrower than this on every axis keep the vehicle's single light value. */
    static final double MIN_SPAN = 2.5;

    /** Samples {@code box}, inset half a block; null when it is too small to need more than one value. */
    public static VehicleLightProbe sample(Level level, AABB box) {
        if (level == null || box == null || !(box.getXsize() >= MIN_SPAN || box.getYsize() >= MIN_SPAN
                || box.getZsize() >= MIN_SPAN) || !Double.isFinite(box.minX + box.minY + box.minZ + box.maxX + box.maxY + box.maxZ)) {
            return null;
        }
        AABB inner = box.deflate(Math.min(0.5, box.getXsize() / 4), Math.min(0.5, box.getYsize() / 4),
                Math.min(0.5, box.getZsize() / 4));
        float[] light = new float[16];
        var pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 8; i++) {
            double x = (i & 1) == 0 ? inner.minX : inner.maxX;
            double y = (i & 2) == 0 ? inner.minY : inner.maxY;
            double z = (i & 4) == 0 ? inner.minZ : inner.maxZ;
            pos.set(x, y, z);
            if (!level.hasChunkAt(pos)) {
                return null;
            }
            int block = level.getBrightness(LightLayer.BLOCK, pos), sky = level.getBrightness(LightLayer.SKY, pos);
            // A corner inside the ground or a wall reads no light; take the space above it instead.
            if (level.getBlockState(pos).isSolidRender()) {
                pos.move(0, 1, 0);
                block = Math.max(block, level.getBrightness(LightLayer.BLOCK, pos));
                sky = Math.max(sky, level.getBrightness(LightLayer.SKY, pos));
            }
            light[i * 2] = block * 16;
            light[i * 2 + 1] = sky * 16;
        }
        return new VehicleLightProbe(inner.minX, inner.minY, inner.minZ, (float) inner.getXsize(),
                (float) inner.getYsize(), (float) inner.getZsize(), light);
    }

    /** The probe's light at a world point, trilinearly blended and clamped to the box, as the shader does. */
    public float[] at(double x, double y, double z) {
        float tx = t(x - this.minX, this.sizeX), ty = t(y - this.minY, this.sizeY), tz = t(z - this.minZ, this.sizeZ);
        float[] out = new float[2];
        for (int i = 0; i < 8; i++) {
            float w = ((i & 1) == 0 ? 1 - tx : tx) * ((i & 2) == 0 ? 1 - ty : ty) * ((i & 4) == 0 ? 1 - tz : tz);
            out[0] += w * this.light[i * 2];
            out[1] += w * this.light[i * 2 + 1];
        }
        return out;
    }

    private static float t(double offset, float size) {
        return size > 0 ? (float) Math.clamp(offset / size, 0, 1) : 0;
    }
}
