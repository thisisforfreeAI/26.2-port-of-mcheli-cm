package io.github.mcheliceport.client.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.client.model.McheliModel;
import java.util.function.Function;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import org.jspecify.annotations.Nullable;

/**
 * The drafting table's 3D vehicle page (upstream {@code MCH_DraftingTableGui#drawModel}): the
 * vehicle's whole model file, first drawn face by face as dark lines, then filled in with its
 * texture face by face while the lines go away. Upstream drew straight into the screen with
 * lighting off; here the model is drawn into a picture over the whole screen, with the same
 * transforms in GUI units.
 */
public final class DraftingModelRenderer extends PictureInPictureRenderer<DraftingModelRenderer.State> {

    /** Vanilla's entity shader with no lighting or overlay, as upstream's {@code disableLighting}. */
    private static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "pipeline/drafting_model"))
            .withShaderDefine("ALPHA_CUTOUT", 0.1F)
            .withShaderDefine("NO_OVERLAY")
            .withShaderDefine("NO_CARDINAL_LIGHTING")
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .build();
    private static final Function<Identifier, RenderType> TYPE = Util.memoize(texture -> RenderType.create(
            "mcheli_ce_port_drafting_model",
            RenderSetup.builder(PIPELINE)
                    .withTexture("Sampler0", texture)
                    .useLightmap()
                    .createRenderSetup()));
    /** Upstream {@code color(0.1, 0.1, 0.1)} for the lines. */
    private static final int LINE_COLOR = 0xFF1A1A1A;
    private static final int FULL_BRIGHT = 0xF000F0;

    /**
     * One frame of the page. {@code centreXZ} is upstream's {@code modelRot == 0} (planes and
     * ships), which centres the model on its X and Z extents instead of Z and Y. {@code originX}
     * and {@code originY} are the GUI point upstream translated to ({@code guiLeft + 300},
     * {@code guiTop + 110}).
     */
    public record State(McheliModel model, Identifier texture, boolean centreXZ, float rotX, float rotY, float posX,
            float posY, float zoom, int drawFace, int originX, int originY, int x0, int y0, int x1, int y1,
            @Nullable ScreenRectangle scissorArea, @Nullable ScreenRectangle bounds)
            implements PictureInPictureRenderState {

        public State(McheliModel model, Identifier texture, boolean centreXZ, float rotX, float rotY, float posX,
                float posY, float zoom, int drawFace, int originX, int originY, int x0, int y0, int x1, int y1,
                @Nullable ScreenRectangle scissorArea) {
            this(model, texture, centreXZ, rotX, rotY, posX, posY, zoom, drawFace, originX, originY, x0, y0, x1, y1,
                    scissorArea, PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea));
        }

        @Override
        public float scale() {
            return 1.0F;
        }
    }

    public static void registerPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(PIPELINE);
    }

    public static void register(RegisterPictureInPictureRenderersEvent event) {
        event.register(State.class, DraftingModelRenderer::new);
    }

    @Override
    public Class<State> getRenderStateClass() {
        return State.class;
    }

    /** The pose starts at the top middle of the picture, in GUI units. */
    @Override
    protected float getTranslateY(int height, int guiScale) {
        return 0.0F;
    }

    @Override
    protected void renderToTexture(State state, PoseStack poseStack, SubmitNodeCollector collector) {
        McheliModel model = state.model();
        poseStack.translate(-state.x0() - (state.x1() - state.x0()) / 2.0F, -state.y0(), 0.0F);
        // Upstream W_ModelCustom#size: the largest extent over all three axes.
        float size = Math.max(Math.max(model.maxX, model.maxY), model.maxZ)
                - Math.min(Math.min(model.minX, model.minY), model.minZ);
        float scl = 162.0F / (Math.abs(size) < 0.01F ? 0.01F : size);
        float cx = (model.maxX - model.minX) * 0.5F + model.minX;
        float cy = (model.maxY - model.minY) * 0.5F + model.minY;
        float cz = (model.maxZ - model.minZ) * 0.5F + model.minZ;
        if (state.centreXZ()) {
            poseStack.translate(cx * scl, cz * scl, 0.0F);
        } else {
            poseStack.translate(cz * scl, cy * scl, 0.0F);
        }
        poseStack.translate(state.originX() + state.posX(), state.originY() + state.posY(), 0.0F);
        poseStack.mulPose(Axis.XP.rotationDegrees(state.rotX()));
        poseStack.mulPose(Axis.YP.rotationDegrees(state.rotY()));
        float s = scl * state.zoom();
        poseStack.scale(s, s, -s);
        int faces = model.vertexCount() / 3;
        int drawFace = state.drawFace();
        if (drawFace < faces * 2) {
            collector.submitCustomGeometry(poseStack, RenderTypes.lines(),
                    (pose, buffer) -> lines(buffer, pose, model, drawFace - faces, drawFace));
        }
        if (drawFace >= faces) {
            collector.submitCustomGeometry(poseStack, TYPE.apply(state.texture()),
                    (pose, buffer) -> faces(buffer, pose, model, 0, drawFace - faces));
        }
    }

    /**
     * Upstream {@code renderAll(start, max)}: faces are counted from 1 over every group in order,
     * and those from {@code start} to {@code max} are drawn. The port's models hold triangles, so
     * a face here is a triangle.
     */
    private static void faces(VertexConsumer buffer, PoseStack.Pose pose, McheliModel model, int start, int max) {
        int stride = McheliModel.STRIDE;
        int face = 0;
        for (McheliModel.Group group : model.groups) {
            float[] d = group.data();
            for (int t = 0; t + 3 * stride <= d.length; t += 3 * stride) {
                face++;
                if (face > max) {
                    return;
                }
                if (face < start) {
                    continue;
                }
                for (int k = 0; k < 4; k++) {
                    int i = t + Math.min(k, 2) * stride;
                    buffer.addVertex(pose, d[i], d[i + 1], d[i + 2])
                            .setColor(-1)
                            .setUv(d[i + 3], d[i + 4])
                            .setOverlay(OverlayTexture.NO_OVERLAY)
                            .setLight(FULL_BRIGHT)
                            .setNormal(pose, d[i + 5], d[i + 6], d[i + 7]);
                }
            }
        }
    }

    /** Upstream's {@code GL_LINE} polygon mode: each face's edges, one pixel wide. */
    private static void lines(VertexConsumer buffer, PoseStack.Pose pose, McheliModel model, int start, int max) {
        int stride = McheliModel.STRIDE;
        int face = 0;
        for (McheliModel.Group group : model.groups) {
            float[] d = group.data();
            for (int t = 0; t + 3 * stride <= d.length; t += 3 * stride) {
                face++;
                if (face > max) {
                    return;
                }
                if (face < start) {
                    continue;
                }
                for (int k = 0; k < 3; k++) {
                    int a = t + k * stride;
                    int b = t + (k + 1) % 3 * stride;
                    line(buffer, pose, d[a], d[a + 1], d[a + 2], d[b], d[b + 1], d[b + 2]);
                }
            }
        }
    }

    private static void line(VertexConsumer buffer, PoseStack.Pose pose, float x0, float y0, float z0, float x1,
            float y1, float z1) {
        float nx = x1 - x0;
        float ny = y1 - y0;
        float nz = z1 - z0;
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length < 1.0E-6F) {
            return;
        }
        nx /= length;
        ny /= length;
        nz /= length;
        buffer.addVertex(pose, x0, y0, z0).setColor(LINE_COLOR).setNormal(pose, nx, ny, nz).setLineWidth(1.0F);
        buffer.addVertex(pose, x1, y1, z1).setColor(LINE_COLOR).setNormal(pose, nx, ny, nz).setLineWidth(1.0F);
    }

    @Override
    protected String getTextureLabel() {
        return "drafting table model";
    }
}
