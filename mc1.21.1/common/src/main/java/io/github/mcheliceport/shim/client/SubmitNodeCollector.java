package io.github.mcheliceport.shim.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

/**
 * 1.21.1 stand-in for 26.2's {@code SubmitNodeCollector}: 26.2 queues geometry and draws it later;
 * here it is drawn into the buffer source at once, which ends up in the same batches.
 */
public class SubmitNodeCollector {

    private final MultiBufferSource buffers;

    public SubmitNodeCollector(MultiBufferSource buffers) {
        this.buffers = buffers;
    }

    public MultiBufferSource buffers() {
        return buffers;
    }

    @FunctionalInterface
    public interface CustomGeometryRenderer {
        void render(PoseStack.Pose pose, VertexConsumer buffer);
    }

    public void submitCustomGeometry(PoseStack poseStack, RenderType type, CustomGeometryRenderer renderer) {
        renderer.render(poseStack.last(), buffers.getBuffer(type));
    }

    /** 26.2's ordered collector; drawing order is submission order here anyway. */
    public SubmitNodeCollector order(int order) {
        return this;
    }
}
