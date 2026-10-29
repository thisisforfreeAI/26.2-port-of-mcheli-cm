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
import java.util.List;
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

    /** Cached terrain top face, relative to its explosion; corner strengths include distance falloff. */
    public record Surface(float x0, float x1, float y, float z0, float z1, float a, float b, float c, float d) {}
    public record Lamp(Vec3 position,float radius,int color,float alpha){}

    public static void surfaces(PoseStack pose, SubmitNodeCollector collector, List<Surface> surfaces,
            float alpha, int count) {
        surfaces(pose,collector,surfaces,alpha,count,0xFFAC50);
    }
    public static void surfaces(PoseStack pose, SubmitNodeCollector collector, List<Surface> surfaces,
            float alpha, int count,int color) {
        collector.submitCustomGeometry(pose, TYPE, (matrix, buffer) -> {
            for (int i = 0; i < count; i++) {
                Surface s = surfaces.get(i);
                int a = argb(color, alpha * s.a), b = argb(color, alpha * s.b);
                int c = argb(color, alpha * s.c), d = argb(color, alpha * s.d);
                vertex(buffer, matrix, s.x0, s.y, s.z0, a);
                vertex(buffer, matrix, s.x0, s.y, s.z1, b);
                vertex(buffer, matrix, s.x1, s.y, s.z1, c);
                vertex(buffer, matrix, s.x0, s.y, s.z0, a);
                vertex(buffer, matrix, s.x1, s.y, s.z1, c);
                vertex(buffer, matrix, s.x1, s.y, s.z0, d);
            }
        });
    }
    /** All small lamps on one vehicle share one geometry submission and the existing glow material. */
    public static void lamps(PoseStack stack,SubmitNodeCollector collector,CameraRenderState camera,List<Lamp> lamps){
        if(lamps.isEmpty())return;var snapshot=List.copyOf(lamps);
        var right=new org.joml.Vector3f(1,0,0).rotate(camera.orientation);var up=new org.joml.Vector3f(0,1,0).rotate(camera.orientation);
        collector.submitCustomGeometry(stack,TYPE,(pose,buffer)->{
            for(var lamp:snapshot){var p=lamp.position;int center=argb(lamp.color,lamp.alpha),rim=argb(lamp.color,0);
                for(int i=0;i<SEGMENTS;i++){
                    vertex(buffer,pose,(float)p.x,(float)p.y,(float)p.z,center);
                    for(int j=i;j<=i+1;j++)vertex(buffer,pose,(float)p.x+(right.x*RIM_X[j]+up.x*RIM_Y[j])*lamp.radius,
                            (float)p.y+(right.y*RIM_X[j]+up.y*RIM_Y[j])*lamp.radius,(float)p.z+(right.z*RIM_X[j]+up.z*RIM_Y[j])*lamp.radius,rim);
                }
            }
        });
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

    /** A tapered burning streak or tiny end-on base, four triangles in either case. */
    public static void tracer(PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera,
            Vec3 tail, Vec3 toCamera, float width, int rgb, float alpha) {
        double lengthSquared = tail.lengthSqr(), distanceSquared = toCamera.lengthSqr();
        if (alpha <= 0 || width <= 0 || !Double.isFinite(lengthSquared) || !Double.isFinite(distanceSquared)
                || lengthSquared < 1.0E-6) return;
        Vec3 side = tail.cross(toCamera);
        double sideLength = side.length();
        boolean endOn = distanceSquared < 1.0E-6 || sideLength * sideLength < lengthSquared * distanceSquared * 0.0144;
        int hot = argb(TracerLook.coreColor(rgb), alpha), warm = argb(rgb, alpha * 0.65F);
        int edge = argb(rgb, alpha * 0.30F), end = argb(rgb, 0);
        if (endOn) {
            // The old cross-product ribbon vanished when looking along the shot direction.
            // A compact base is visible from a firing camera, without a disc or bloom halo.
            var right = new org.joml.Vector3f(1, 0, 0).rotate(camera.orientation).mul(width * 0.5F);
            var up = new org.joml.Vector3f(0, 1, 0).rotate(camera.orientation).mul(width * 0.5F);
            collector.submitCustomGeometry(poseStack, TYPE, (pose, buffer) -> {
                tracerFace(buffer, pose, right.x, right.y, right.z, up.x, up.y, up.z, edge);
                tracerFace(buffer, pose, right.x * 0.55F, right.y * 0.55F, right.z * 0.55F,
                        up.x * 0.55F, up.y * 0.55F, up.z * 0.55F, hot);
            });
            return;
        }
        side = side.scale(width * 0.5 / sideLength);
        float sx = (float) side.x, sy = (float) side.y, sz = (float) side.z;
        float tx = (float) tail.x, ty = (float) tail.y, tz = (float) tail.z;
        collector.submitCustomGeometry(poseStack, TYPE, (pose, buffer) -> {
            // Faint tapered colour skirt, a narrow hot core, then a cooling pointed tip.
            vertex(buffer, pose, sx, sy, sz, edge);
            vertex(buffer, pose, tx, ty, tz, end);
            vertex(buffer, pose, -sx, -sy, -sz, edge);
            float hx = sx * 0.55F, hy = sy * 0.55F, hz = sz * 0.55F;
            float mx = tx * 0.65F, my = ty * 0.65F, mz = tz * 0.65F;
            float ex = sx * 0.18F, ey = sy * 0.18F, ez = sz * 0.18F;
            vertex(buffer, pose, hx, hy, hz, hot);
            vertex(buffer, pose, mx + ex, my + ey, mz + ez, warm);
            vertex(buffer, pose, mx - ex, my - ey, mz - ez, warm);
            vertex(buffer, pose, hx, hy, hz, hot);
            vertex(buffer, pose, mx - ex, my - ey, mz - ez, warm);
            vertex(buffer, pose, -hx, -hy, -hz, hot);
            vertex(buffer, pose, mx + ex, my + ey, mz + ez, warm);
            vertex(buffer, pose, tx, ty, tz, end);
            vertex(buffer, pose, mx - ex, my - ey, mz - ez, warm);
        });
    }

    private static void tracerFace(VertexConsumer buffer, PoseStack.Pose pose, float rx, float ry, float rz,
            float ux, float uy, float uz, int color) {
        vertex(buffer, pose, -rx - ux, -ry - uy, -rz - uz, color);
        vertex(buffer, pose, -rx + ux, -ry + uy, -rz + uz, color);
        vertex(buffer, pose, rx + ux, ry + uy, rz + uz, color);
        vertex(buffer, pose, -rx - ux, -ry - uy, -rz - uz, color);
        vertex(buffer, pose, rx + ux, ry + uy, rz + uz, color);
        vertex(buffer, pose, rx - ux, ry - uy, rz - uz, color);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, int color) {
        buffer.addVertex(pose, x, y, z).setColor(color);
    }

    static int argb(int rgb, float alpha) {
        return Mth.clamp((int) (alpha * 255.0F), 0, 255) << 24 | rgb & 0xFFFFFF;
    }
}
