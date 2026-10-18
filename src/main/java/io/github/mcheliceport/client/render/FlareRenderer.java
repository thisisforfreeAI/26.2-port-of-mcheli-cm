package io.github.mcheliceport.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.mcheliceport.config.ClientConfig;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

/**
 * Draws flares (a yellow-tinted quarter-block cube) and chaff (half that size). Ported from upstream
 * {@code MCH_RenderFlare}, {@code MCH_RenderChaff} and {@code MCH_ModelFlare}. Upstream's chaff
 * texture ({@code textures/chaff.png}) was never shipped, so chaff uses the flare texture untinted.
 * Polished style keeps the cube full-bright and adds one small halo, except at Low quality.
 */
public class FlareRenderer<T extends Entity> extends EntityRenderer<T, FlareRenderer.State> {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("mcheli", "textures/flare.png");
    private static final Identifier HALO = Identifier.fromNamespaceAndPath("mcheli_ce_port", "textures/particle/m8_flare_halo.png");

    public static class State extends EntityRenderState {
        public float yaw;
        public float pitch;
        public boolean polished;
        public boolean halo;
    }

    private final float scale;
    private final int color;

    private FlareRenderer(EntityRendererProvider.Context context, float scale, int color) {
        super(context);
        this.scale = scale;
        this.color = color;
    }

    public static <T extends Entity> FlareRenderer<T> flare(EntityRendererProvider.Context context) {
        return new FlareRenderer<>(context, 1.0F, 0xFFFFFF80);
    }

    public static <T extends Entity> FlareRenderer<T> chaff(EntityRendererProvider.Context context) {
        return new FlareRenderer<>(context, 0.5F, 0xFFFFFFFF);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(T entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.yaw = entity.getYRot();
        state.pitch = entity.getXRot();
        state.polished = this.scale == 1.0F
                && ClientConfig.get(ClientConfig.EFFECT_STYLE) == ClientConfig.EffectStyle.POLISHED;
        state.halo = state.polished && ClientConfig.get(ClientConfig.EFFECT_QUALITY) != ClientConfig.EffectQuality.LOW;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        int light = state.polished ? net.minecraft.util.LightCoordsUtil.FULL_BRIGHT : state.lightCoords;
        int color = this.color;
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-state.yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(state.pitch));
        poseStack.mulPose(Axis.ZP.rotationDegrees(45.0F));
        poseStack.scale(this.scale, this.scale, this.scale);
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(TEXTURE),
                (pose, buffer) -> cube(buffer, pose, 0.125F, color, light));
        poseStack.popPose();
        if (state.halo) {
            BulletRenderer.submitFlame(poseStack, collector, camera, 0.0, 0.0, 0.0, HALO, 0.65F);
        }
        super.submit(state, poseStack, collector, camera);
    }

    /**
     * A cube of half size {@code h} with the whole texture on each face (upstream's 4x4x4 box at 1/16
     * scale), drawn without culling so the faces' winding does not matter.
     */
    private static void cube(VertexConsumer buffer, PoseStack.Pose pose, float h, int color, int light) {
        float[][] n = {{0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}, {0, -1, 0}, {0, 1, 0}};
        float[][][] faces = {
                {{-h, -h, -h}, {-h, h, -h}, {h, h, -h}, {h, -h, -h}},
                {{-h, -h, h}, {h, -h, h}, {h, h, h}, {-h, h, h}},
                {{-h, -h, -h}, {-h, -h, h}, {-h, h, h}, {-h, h, -h}},
                {{h, -h, -h}, {h, h, -h}, {h, h, h}, {h, -h, h}},
                {{-h, -h, -h}, {h, -h, -h}, {h, -h, h}, {-h, -h, h}},
                {{-h, h, -h}, {-h, h, h}, {h, h, h}, {h, h, -h}}};
        float[][] uv = {{0, 1}, {0, 0}, {1, 0}, {1, 1}};
        for (int f = 0; f < 6; f++) {
            for (int v = 0; v < 4; v++) {
                float[] p = faces[f][v];
                buffer.addVertex(pose, p[0], p[1], p[2])
                        .setColor(color)
                        .setUv(uv[v][0], uv[v][1])
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(light)
                        .setNormal(pose, n[f][0], n[f][1], n[f][2]);
            }
        }
    }
}
