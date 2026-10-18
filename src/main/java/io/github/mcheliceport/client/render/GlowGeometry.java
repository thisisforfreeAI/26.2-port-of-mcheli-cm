package io.github.mcheliceport.client.render;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.mcheliceport.McheliCePort;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

/**
 * M8 glows drawn with entities: soft camera-facing discs (flares, missile flames) and streaks
 * (tracers). Additive position-colour triangles whose colour fades to nothing at the rim, so they
 * need no texture; depth tested without depth writes, like the search light's cones. Vanilla
 * pipeline state only, so it runs on OpenGL and Vulkan. Not in upstream.
 */
public final class GlowGeometry {

    private static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "pipeline/glow"))
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES).build();
    private static final RenderType TYPE = RenderType.create("mcheli_ce_port_glow",
            RenderSetup.builder(PIPELINE).createRenderSetup());
    private static final int SEGMENTS = 16;
    private static final float[] RIM_X = new float[SEGMENTS + 1];
    private static final float[] RIM_Y = new float[SEGMENTS + 1];
    static {
        for (int i = 0; i <= SEGMENTS; i++) {
            float angle = i * Mth.TWO_PI / SEGMENTS;
            RIM_X[i] = Mth.cos(angle);
            RIM_Y[i] = Mth.sin(angle);
        }
    }

    private GlowGeometry() {
    }

    public static void registerPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(PIPELINE);
    }

    /** A soft disc of {@code radius} facing the camera at the pose's origin. */
    public static void disc(PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera,
            float radius, int rgb, float alpha) {
        if (alpha <= 0.0F || radius <= 0.0F) {
            return;
        }
        int center = argb(rgb, alpha);
        int rim = argb(rgb, 0.0F);
        poseStack.pushPose();
        poseStack.mulPose(camera.orientation);
        collector.submitCustomGeometry(poseStack, TYPE, (pose, buffer) -> {
            for (int i = 0; i < SEGMENTS; i++) {
                buffer.addVertex(pose, 0.0F, 0.0F, 0.0F).setColor(center);
                buffer.addVertex(pose, RIM_X[i] * radius, RIM_Y[i] * radius, 0.0F).setColor(rim);
                buffer.addVertex(pose, RIM_X[i + 1] * radius, RIM_Y[i + 1] * radius, 0.0F).setColor(rim);
            }
        });
        poseStack.popPose();
    }

    /**
     * A streak from the pose's origin back along {@code tail} (world axes, unrotated pose), bright at
     * the origin and fading to nothing at the tail's end, turned to face the camera about its own
     * axis. {@code toCamera} points from the origin to the camera.
     */
    public static void streak(PoseStack poseStack, SubmitNodeCollector collector, Vec3 tail, Vec3 toCamera,
            float width, int rgb, float alpha) {
        Vec3 side = tail.cross(toCamera);
        double len = side.length();
        if (alpha <= 0.0F || len < 1.0E-6 || tail.lengthSqr() < 1.0E-6) {
            return;
        }
        side = side.scale(width / 2.0 / len);
        float sx = (float) side.x;
        float sy = (float) side.y;
        float sz = (float) side.z;
        float tx = (float) tail.x;
        float ty = (float) tail.y;
        float tz = (float) tail.z;
        int head = argb(rgb, alpha);
        int end = argb(rgb, 0.0F);
        collector.submitCustomGeometry(poseStack, TYPE, (pose, buffer) -> {
            // Two halves, each bright on the axis and clear at the edge, so the streak has a soft edge.
            for (int s = -1; s <= 1; s += 2) {
                vertex(buffer, pose, 0.0F, 0.0F, 0.0F, head);
                vertex(buffer, pose, tx, ty, tz, end);
                vertex(buffer, pose, tx + sx * s, ty + sy * s, tz + sz * s, end);
                vertex(buffer, pose, 0.0F, 0.0F, 0.0F, head);
                vertex(buffer, pose, tx + sx * s, ty + sy * s, tz + sz * s, end);
                vertex(buffer, pose, sx * s, sy * s, sz * s, end);
            }
        });
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, int color) {
        buffer.addVertex(pose, x, y, z).setColor(color);
    }

    static int argb(int rgb, float alpha) {
        return Mth.clamp((int) (alpha * 255.0F), 0, 255) << 24 | rgb & 0xFFFFFF;
    }
}
