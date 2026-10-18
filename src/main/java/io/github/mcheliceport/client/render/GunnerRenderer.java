package io.github.mcheliceport.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.gunner.GunnerEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Upstream's {@code MCH_RenderGunner}: the 1.12 biped model with {@code heligunner.png}, scaled to
 * 0.9375, hidden when it flies a vehicle that hides its pilot, with a name tag only in a team.
 */
public class GunnerRenderer extends LivingEntityRenderer<GunnerEntity, HumanoidRenderState,
        HumanoidModel<HumanoidRenderState>> {

    /** The 1.12 biped's 64×32 layout, which upstream's texture uses. */
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "gunner"), "main");
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("mcheli", "textures/mob/heligunner.png");

    public GunnerRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(LAYER)), 0.5F);
    }

    public static void registerLayer(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(LAYER,
                () -> LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F), 64, 32));
    }

    @Override
    public HumanoidRenderState createRenderState() {
        return new HumanoidRenderState();
    }

    @Override
    public void extractRenderState(GunnerEntity entity, HumanoidRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        HumanoidMobRenderer.extractHumanoidRenderState(entity, state, partialTicks, this.itemModelResolver);
    }

    @Override
    public Identifier getTextureLocation(HumanoidRenderState state) {
        return TEXTURE;
    }

    /** Upstream drew nothing off a vehicle, or as the pilot of one with {@code HideEntity}. */
    @Override
    public boolean shouldRender(GunnerEntity entity, Frustum culler, double camX, double camY, double camZ) {
        AircraftEntity ac = entity.getAc();
        return ac != null && ac.getAcInfo() != null && (!ac.getAcInfo().hideEntity || !ac.isPilot(entity))
                && super.shouldRender(entity, culler, camX, camY, camZ);
    }

    @Override
    protected void scale(HumanoidRenderState state, PoseStack poseStack) {
        poseStack.scale(0.9375F, 0.9375F, 0.9375F);
    }

    /** Upstream {@code canRenderName}: only a gunner in a team has a name. */
    @Override
    protected boolean shouldShowName(GunnerEntity entity, double distanceToCameraSq) {
        return entity.getTeam() != null && super.shouldShowName(entity, distanceToCameraSq);
    }
}
