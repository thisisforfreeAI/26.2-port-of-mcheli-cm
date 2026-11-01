package io.github.mcheliceport.shim.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 26.2's two-step entity renderer (extract a render state, then submit it) on top of 1.21.1's
 * immediate {@link EntityRenderer#render}. Ported renderers keep their 26.2 shape.
 */
public abstract class StateEntityRenderer<T extends Entity, S extends EntityRenderState> extends EntityRenderer<T> {

    private S state;

    protected StateEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    public abstract S createRenderState();

    /** Fills the base fields, as 26.2's {@code EntityRenderer#extractRenderState}. */
    public void extractRenderState(T entity, S state, float partialTick) {
        Vec3 pos = entity.getPosition(partialTick);
        state.entityType = entity.getType();
        state.x = pos.x;
        state.y = pos.y;
        state.z = pos.z;
        state.partialTick = partialTick;
        state.ageInTicks = entity.tickCount + partialTick;
        state.boundingBoxWidth = entity.getBbWidth();
        state.boundingBoxHeight = entity.getBbHeight();
        state.eyeHeight = entity.getEyeHeight();
        state.distanceToCameraSq = this.entityRenderDispatcher.distanceToSqr(entity);
        state.isInvisible = entity.isInvisible();
        state.isDiscrete = entity.isDiscrete();
        state.displayFireAnimation = entity.displayFireAnimation();
        state.lightCoords = this.getPackedLightCoords(entity, partialTick);
        state.shadowRadius = this.shadowRadius;
    }

    public void submit(S state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
    }

    protected float getShadowRadius(S state) {
        return state.shadowRadius;
    }

    /** 26.2's culling box hook; 1.21.1 culls by {@link Entity#getBoundingBoxForCulling()}. */
    protected AABB getBoundingBoxForCulling(T entity) {
        return entity.getBoundingBoxForCulling();
    }

    @Override
    public boolean shouldRender(T entity, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y,
            double z) {
        if (!entity.shouldRender(x, y, z)) {
            return false;
        }
        if (entity.noCulling) {
            return true;
        }
        AABB box = this.getBoundingBoxForCulling(entity).inflate(0.5);
        if (box.hasNaN() || box.getSize() == 0.0) {
            box = new AABB(entity.getX() - 2.0, entity.getY() - 2.0, entity.getZ() - 2.0, entity.getX() + 2.0,
                    entity.getY() + 2.0, entity.getZ() + 2.0);
        }
        return frustum.isVisible(box);
    }

    @Override
    public void render(T entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
            int light) {
        if (this.state == null) {
            this.state = this.createRenderState();
        }
        S s = this.state;
        this.extractRenderState(entity, s, partialTick);
        this.shadowRadius = this.getShadowRadius(s);
        this.submit(s, poseStack, new SubmitNodeCollector(buffers), CameraRenderState.current());
    }

    @Override
    @SuppressWarnings("deprecation")
    public ResourceLocation getTextureLocation(T entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    protected static Minecraft minecraft() {
        return Minecraft.getInstance();
    }
}
