package io.github.mcheliceport.client.render.baked;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.mcheliceport.client.model.McheliModel;
import io.github.mcheliceport.client.model.VehicleSurfaceShade;
import java.nio.ByteBuffer;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import org.lwjgl.system.MemoryUtil;

/**
 * Model groups uploaded to the GPU once, in {@link BakedVehicles#FORMAT}. A group is uploaded the
 * first frame it is drawn and freed after it has gone undrawn for a while, which also lets go of
 * the groups of models that were reloaded or unloaded (their {@code Group}s are never drawn again).
 * Render thread only.
 */
final class BakedModelCache {

    /** Frames a group may go undrawn before its buffer is freed. */
    static final int EVICT_AFTER_FRAMES = 1200;

    record Baked(GpuBuffer buffer, int vertexCount) {
    }

    private static final class Entry {
        final Baked baked;
        /** The surface shade colours baked in, to bake again if the model loader replaced them. */
        final int[] shades;
        long lastUsed;

        Entry(Baked baked, int[] shades) {
            this.baked = baked;
            this.shades = shades;
        }
    }

    private final Map<McheliModel.Group, Entry> entries = new IdentityHashMap<>();
    private long frame;
    private long bytes;

    /** The group's buffer, uploading it first if needed; null for a group without triangles. */
    Baked get(McheliModel.Group group) {
        Entry entry = this.entries.get(group);
        int[] shades = VehicleSurfaceShade.get(group);
        if (entry != null && entry.shades != shades) {
            this.free(entry);
            this.entries.remove(group);
            entry = null;
        }
        if (entry == null) {
            Baked baked = bake(group, shades);
            if (baked == null) {
                return null;
            }
            entry = new Entry(baked, shades);
            this.entries.put(group, entry);
            this.bytes += (long) baked.vertexCount() * BakedVehicles.FORMAT.getVertexSize();
        }
        entry.lastUsed = this.frame;
        return entry.baked;
    }

    /** Called between frames: frees what has gone undrawn. */
    void endFrame() {
        this.frame++;
        Iterator<Entry> it = this.entries.values().iterator();
        while (it.hasNext()) {
            Entry entry = it.next();
            if (this.frame - entry.lastUsed > EVICT_AFTER_FRAMES) {
                this.free(entry);
                it.remove();
            }
        }
    }

    void clear() {
        for (Entry entry : this.entries.values()) {
            this.free(entry);
        }
        this.entries.clear();
    }

    int size() {
        return this.entries.size();
    }

    long bytes() {
        return this.bytes;
    }

    private void free(Entry entry) {
        this.bytes -= (long) entry.baked.vertexCount() * BakedVehicles.FORMAT.getVertexSize();
        entry.baked.buffer().close();
    }

    private static Baked bake(McheliModel.Group group, int[] shades) {
        float[] d = group.data();
        int stride = McheliModel.STRIDE;
        int triangles = d.length / (3 * stride);
        if (triangles == 0) {
            return null;
        }
        int vertexCount = triangles * 3;
        ByteBuffer data = MemoryUtil.memAlloc(vertexCount * BakedVehicles.FORMAT.getVertexSize());
        try {
            for (int t = 0; t < triangles * 3 * stride; t += 3 * stride) {
                int last = t + 2 * stride;
                for (int k = 0; k < 3; k++) {
                    int i = t + k * stride;
                    data.putFloat(d[i]).putFloat(d[i + 1]).putFloat(d[i + 2]);
                    data.putFloat(d[i + 3]).putFloat(d[i + 4]);
                    putNormal(data, d, i + 5, shade(shades, i / stride));
                    putNormal(data, d, last + 5, (byte) 0);
                }
            }
            data.flip();
            GpuBuffer buffer = RenderSystem.getDevice().createBuffer(() -> "McHeli model " + group.name(),
                    GpuBuffer.USAGE_VERTEX, data);
            return new Baked(buffer, vertexCount);
        } finally {
            MemoryUtil.memFree(data);
        }
    }

    private static void putNormal(ByteBuffer data, float[] d, int i, byte w) {
        data.put(snorm(d[i])).put(snorm(d[i + 1])).put(snorm(d[i + 2])).put(w);
    }

    /**
     * The vertex's surface shade grey ({@code VehicleSurfaceShade} tints a grey base colour) less
     * 128, which the shader reads back exactly from the normal's fourth byte; -128 for none. The
     * shades never go below 0.84 of the base grey, so they stay above 128.
     */
    static byte shade(int[] shades, int vertex) {
        if (shades == null || vertex >= shades.length) {
            return Byte.MIN_VALUE;
        }
        return (byte) Math.clamp((shades[vertex] & 0xFF) - 128, 0, 127);
    }

    static byte snorm(float f) {
        return (byte) Math.round(Math.clamp(f, -1.0F, 1.0F) * 127.0F);
    }
}
