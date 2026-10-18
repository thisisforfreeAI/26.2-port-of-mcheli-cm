package io.github.mcheliceport.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.mcheliceport.config.EffectQuality;
import io.github.mcheliceport.client.particle.EffectPerformance;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * Draws flares (a yellow-tinted quarter-block cube) and chaff (half that size). Ported from upstream
 * {@code MCH_RenderFlare}, {@code MCH_RenderChaff} and {@code MCH_ModelFlare}. Upstream's chaff
 * texture ({@code textures/chaff.png}) was never shipped, so chaff uses the flare texture untinted.
 */
public class FlareRenderer<T extends Entity> extends EntityRenderer<T, FlareRenderer.State> {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("mcheli", "textures/flare.png");
    private static final Identifier HALO = Identifier.fromNamespaceAndPath("mcheli_ce_port", "textures/particle/m8_flare_halo.png");

    public static class State extends EntityRenderState {
        public float yaw;
        public float pitch;
        /** M8: drawn full bright with a flickering glow. */
        public boolean enhanced;
        public boolean halo;
    }

    private static final float[][] NORMALS = {{0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}, {0, -1, 0}, {0, 1, 0}};
    private static final float[][][] FACES = {
                {{-1, -1, -1}, {-1, 1, -1}, {1, 1, -1}, {1, -1, -1}},
                {{-1, -1, 1}, {1, -1, 1}, {1, 1, 1}, {-1, 1, 1}},
                {{-1, -1, -1}, {-1, -1, 1}, {-1, 1, 1}, {-1, 1, -1}},
                {{1, -1, -1}, {1, 1, -1}, {1, 1, 1}, {1, -1, 1}},
                {{-1, -1, -1}, {1, -1, -1}, {1, -1, 1}, {-1, -1, 1}},
                {{-1, 1, -1}, {-1, 1, 1}, {1, 1, 1}, {1, 1, -1}}};
    private static final float[][] UV = {{0, 1}, {0, 0}, {1, 0}, {1, 1}};

    private final float scale;
    private final int color;
    /** Flares burn; chaff (metal strips) does not. */
    private final boolean glows;

    private FlareRenderer(EntityRendererProvider.Context context, float scale, int color, boolean glows) {
        super(context);
        this.scale = scale;
        this.color = color;
        this.glows = glows;
    }

    public static <T extends Entity> FlareRenderer<T> flare(EntityRendererProvider.Context context) {
        return new FlareRenderer<>(context, 1.0F, 0xFFFFFF80, true);
    }

    public static <T extends Entity> FlareRenderer<T> chaff(EntityRendererProvider.Context context) {
        return new FlareRenderer<>(context, 0.5F, 0xFFFFFFFF, false);
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
        state.enhanced = EffectQuality.enhanced();
        state.halo = state.enhanced && EffectPerformance.optionalGlows();
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        boolean glow = state.enhanced && this.glows;
        // Upstream lit flares by the world, so they went dark at night; the M8 look burns at full brightness.
        int light = glow ? LightCoordsUtil.FULL_BRIGHT : state.lightCoords;
        int color = this.color;
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-state.yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(state.pitch));
        poseStack.mulPose(Axis.ZP.rotationDegrees(45.0F));
        poseStack.scale(this.scale, this.scale, this.scale);
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(TEXTURE),
                (pose, buffer) -> cube(buffer, pose, 0.125F, color, light));
        poseStack.popPose();
        if (glow && state.halo) {
            float flicker = 0.85F + 0.15F * Mth.sin(state.ageInTicks * 1.7F);
            BulletRenderer.submitFlame(poseStack, collector, camera, 0, 0, 0, HALO,
                    0.65F * this.scale * flicker);
        }
        super.submit(state, poseStack, collector, camera);
    }

    /**
     * A cube of half size {@code h} with the whole texture on each face (upstream's 4x4x4 box at 1/16
     * scale), drawn without culling so the faces' winding does not matter.
     */
    private static void cube(VertexConsumer buffer, PoseStack.Pose pose, float h, int color, int light) {
        for (int f = 0; f < 6; f++) {
            for (int v = 0; v < 4; v++) {
                float[] p = FACES[f][v];
                buffer.addVertex(pose, p[0] * h, p[1] * h, p[2] * h)
                        .setColor(color)
                        .setUv(UV[v][0], UV[v][1])
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(light)
                        .setNormal(pose, NORMALS[f][0], NORMALS[f][1], NORMALS[f][2]);
            }
        }
    }
}
