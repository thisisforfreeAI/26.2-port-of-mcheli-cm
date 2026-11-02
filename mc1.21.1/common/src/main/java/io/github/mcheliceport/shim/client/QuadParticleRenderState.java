package io.github.mcheliceport.shim.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 1.21.1 stand-in for 26.2's {@code QuadParticleRenderState}: 26.2 collects particle quads and
 * draws them per layer; here each quad goes straight into the particle's buffer.
 */
public class QuadParticleRenderState {

    private final VertexConsumer buffer;

    public QuadParticleRenderState(VertexConsumer buffer) {
        this.buffer = buffer;
    }

    public VertexConsumer buffer() {
        return buffer;
    }

    /** One quad at camera-relative {@code (x, y, z)}, as 26.2's {@code add}. */
    public void add(SingleQuadParticle.Layer layer, float x, float y, float z, float qx, float qy, float qz, float qw,
            float size, float u0, float u1, float v0, float v1, int color, int light) {
        renderRotatedQuad(buffer, x, y, z, qx, qy, qz, qw, size, u0, u1, v0, v1, color, light);
    }

    /** Emits the quad; 26.2's custom particle states override this to shape it. */
    protected void renderRotatedQuad(VertexConsumer buffer, float x, float y, float z, float qx, float qy, float qz,
            float qw, float size, float u0, float u1, float v0, float v1, int color, int light) {
        Quaternionf rotation = new Quaternionf(qx, qy, qz, qw);
        vertex(rotation, x, y, z, 1.0F, -1.0F, size, u1, v1, color, light);
        vertex(rotation, x, y, z, 1.0F, 1.0F, size, u1, v0, color, light);
        vertex(rotation, x, y, z, -1.0F, 1.0F, size, u0, v0, color, light);
        vertex(rotation, x, y, z, -1.0F, -1.0F, size, u0, v1, color, light);
    }

    private void vertex(Quaternionf rotation, float x, float y, float z, float dx, float dy, float size, float u,
            float v, int color, int light) {
        Vector3f p = new Vector3f(dx, dy, 0.0F).rotate(rotation).mul(size).add(x, y, z);
        buffer.addVertex(p.x(), p.y(), p.z()).setUv(u, v).setColor(color).setLight(light);
    }

    /** 26.2 reuses states between frames; here each frame makes its own. */
    public void clear() {
    }
}
