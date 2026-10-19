package io.github.mcheliceport.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.mcheliceport.client.AircraftClientEvents;
import io.github.mcheliceport.client.camera.GltdCameraClient;
import io.github.mcheliceport.entity.aircraft.AircraftCamera;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.gltd.GltdEntity;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.Model;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.submit.RenderPhaseKey;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

/**
 * Thermal vision's white-hot entities: upstream's {@code MCH_TextureManagerDummy}, which bound its
 * plain white {@code textures/test.png} in place of every entity texture while the rider's camera
 * was in thermal vision, except for the vehicle being ridden. The thermal post effect then turns
 * those bright grey shapes white and darkens the rest.
 *
 * <p>Living entities are submitted again through a collector that swaps the texture of each model
 * for the white one; held items keep theirs, as upstream's item renderer bound through its own
 * texture manager. McHeli vehicles swap their texture in {@link AircraftRenderer}.
 */
public final class ThermalVision {

    public static final Identifier WHITE = Identifier.fromNamespaceAndPath("mcheli", "textures/test.png");

    private static boolean resubmitting;

    private ThermalVision() {
    }

    public static void register() {
        // Before every other listener, so the tilt and hiding listeners act once, on the resubmit.
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ThermalVision::onPre);
    }

    /** Upstream {@code CameraHandler.cameraMode == 2}: the local rider's camera is in thermal vision. */
    public static boolean active() {
        return cameraMode(Minecraft.getInstance().player) == AircraftCamera.MODE_THERMALVISION;
    }

    /** Upstream {@code MouseInputHandler#getCameraMode}: the ridden vehicle's camera, else the GLTD's. */
    static int cameraMode(@Nullable LocalPlayer player) {
        AircraftEntity aircraft = AircraftClientEvents.riddenAircraft(player);
        if (aircraft != null) {
            return aircraft.getCameraMode(player);
        }
        GltdEntity gltd = GltdCameraClient.riddenGltd();
        return gltd != null ? gltd.camera.getMode(0) : AircraftCamera.MODE_NORMAL;
    }

    /** Whether an entity is drawn white: all but the vehicle being ridden, as upstream. */
    public static boolean drawsWhite(Entity entity) {
        return active() && entity != AircraftClientEvents.riddenAircraft(Minecraft.getInstance().player);
    }

    private static void onPre(RenderLivingEvent.Pre<?, ?, ?> event) {
        if (resubmitting || event.isCanceled() || !active()) {
            return;
        }
        event.setCanceled(true);
        resubmitting = true;
        try {
            resubmit(event.getRenderer(), event.getRenderState(), event.getPoseStack(),
                    new WhiteCollector(event.getSubmitNodeCollector(), event.getSubmitNodeCollector()));
        } finally {
            resubmitting = false;
        }
    }

    @SuppressWarnings("unchecked")
    private static <S extends LivingEntityRenderState> void resubmit(LivingEntityRenderer<?, ?, ?> renderer,
            LivingEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
        CameraRenderState camera = Minecraft.getInstance().gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
        ((LivingEntityRenderer<?, S, ?>) renderer).submit((S) state, poseStack, collector, camera);
    }

    /** The white render type for a model's: translucent where it blended, else cut out. */
    static RenderType white(RenderType original) {
        if (original.isOutline()) {
            return original;
        }
        return original.hasBlending() ? RenderTypes.entityTranslucent(WHITE) : RenderTypes.entityCutout(WHITE);
    }

    /** Passes everything on, with each model's texture swapped for the white one. */
    static final class WhiteCollector implements SubmitNodeCollector {
        private final SubmitNodeCollector root;
        private final OrderedSubmitNodeCollector out;

        WhiteCollector(SubmitNodeCollector root, OrderedSubmitNodeCollector out) {
            this.root = root;
            this.out = out;
        }

        @Override
        public OrderedSubmitNodeCollector order(int order) {
            return new WhiteCollector(this.root, this.root.order(order));
        }

        @Override
        public <S> void submitModel(Model<? super S> model, S state, PoseStack poseStack, RenderType renderType,
                int lightCoords, int overlayCoords, int tintedColor, @Nullable TextureAtlasSprite sprite, int outlineColor,
                ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay) {
            // A sprite would map the white texture's UVs onto an atlas region; drop it.
            this.out.submitModel(model, state, poseStack, white(renderType), lightCoords, overlayCoords, tintedColor,
                    null, outlineColor, crumblingOverlay);
        }

        @Override
        public void submitShadow(PoseStack poseStack, float radius, List<EntityRenderState.ShadowPiece> pieces) {
            this.out.submitShadow(poseStack, radius, pieces);
        }

        @Override
        public void submitNameTag(PoseStack poseStack, @Nullable Vec3 nameTagAttachment, int offset, Component name,
                boolean seeThrough, int lightCoords, CameraRenderState camera) {
            this.out.submitNameTag(poseStack, nameTagAttachment, offset, name, seeThrough, lightCoords, camera);
        }

        @Override
        public void submitText(PoseStack poseStack, float x, float y, FormattedCharSequence string, boolean dropShadow,
                Font.DisplayMode displayMode, int lightCoords, int color, int backgroundColor, int outlineColor) {
            this.out.submitText(poseStack, x, y, string, dropShadow, displayMode, lightCoords, color, backgroundColor,
                    outlineColor);
        }

        @Override
        public void submitFlame(PoseStack poseStack, EntityRenderState renderState, Quaternionf rotation) {
            this.out.submitFlame(poseStack, renderState, rotation);
        }

        @Override
        public void submitLeash(PoseStack poseStack, EntityRenderState.LeashState leashState) {
            this.out.submitLeash(poseStack, leashState);
        }

        @Override
        public void submitMovingBlock(PoseStack poseStack, MovingBlockRenderState movingBlockRenderState, int outlineColor) {
            this.out.submitMovingBlock(poseStack, movingBlockRenderState, outlineColor);
        }

        @Override
        public void submitBlockModel(PoseStack poseStack, RenderType renderType, List<BlockStateModelPart> parts,
                int[] tintLayers, int lightCoords, int overlayCoords, int outlineColor) {
            this.out.submitBlockModel(poseStack, renderType, parts, tintLayers, lightCoords, overlayCoords, outlineColor);
        }

        @Override
        public void submitBreakingBlockModel(PoseStack poseStack, List<BlockStateModelPart> parts, int progress) {
            this.out.submitBreakingBlockModel(poseStack, parts, progress);
        }

        @Override
        public void submitShapeOutline(PoseStack poseStack, VoxelShape shape, RenderType renderType, int color,
                float width, boolean afterTerrain) {
            this.out.submitShapeOutline(poseStack, shape, renderType, color, width, afterTerrain);
        }

        @Override
        public void submitItem(PoseStack poseStack, ItemDisplayContext displayContext, int lightCoords,
                int overlayCoords, int outlineColor, int[] tintLayers, List<BakedQuad> quads,
                ItemStackRenderState.FoilType foilType) {
            this.out.submitItem(poseStack, displayContext, lightCoords, overlayCoords, outlineColor, tintLayers, quads,
                    foilType);
        }

        @Override
        public void submitCustomGeometry(PoseStack poseStack, RenderType renderType,
                SubmitNodeCollector.CustomGeometryRenderer customGeometryRenderer) {
            this.out.submitCustomGeometry(poseStack, renderType, customGeometryRenderer);
        }

        @Override
        public void submitQuadParticleGroup(QuadParticleRenderState particles) {
            this.out.submitQuadParticleGroup(particles);
        }

        @Override
        public void submitGizmoPrimitives(DrawableGizmoPrimitives.Group group, CameraRenderState camera, boolean onTop) {
            this.out.submitGizmoPrimitives(group, camera, onTop);
        }

        @Override
        public void submitMultiLayerBlockModel(PoseStack poseStack, List<BlockStateModelPart> modelParts,
                boolean translucent, int[] tintLayers, int lightCoords, int overlayCoords, int outlineColor) {
            this.out.submitMultiLayerBlockModel(poseStack, modelParts, translucent, tintLayers, lightCoords,
                    overlayCoords, outlineColor);
        }

        @Override
        public <T extends SubmitNode, S extends T> void submitSpecial(RenderPhaseKey<T> phaseKey, S submitNode) {
            this.out.submitSpecial(phaseKey, submitNode);
        }
    }
}
