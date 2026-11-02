package io.github.mcheliceport.shim.client.event;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;

/** A living entity is drawn; cancelling {@link Pre} skips vanilla's drawing of it. */
public abstract class RenderLivingEvent extends ClientEvent {
    private final LivingEntity entity;
    private final LivingEntityRenderer<?, ?> renderer;
    private final float yaw;
    private final float partialTick;
    private final PoseStack poseStack;
    private final MultiBufferSource buffers;
    private final int light;

    protected RenderLivingEvent(LivingEntity entity, LivingEntityRenderer<?, ?> renderer, float yaw, float partialTick,
            PoseStack poseStack, MultiBufferSource buffers, int light) {
        this.entity = entity;
        this.renderer = renderer;
        this.yaw = yaw;
        this.partialTick = partialTick;
        this.poseStack = poseStack;
        this.buffers = buffers;
        this.light = light;
    }

    public LivingEntity getEntity() {
        return entity;
    }

    public LivingEntityRenderer<?, ?> getRenderer() {
        return renderer;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPartialTick() {
        return partialTick;
    }

    public PoseStack getPoseStack() {
        return poseStack;
    }

    public MultiBufferSource getMultiBufferSource() {
        return buffers;
    }

    public int getPackedLight() {
        return light;
    }

    public static final class Pre extends RenderLivingEvent {
        public Pre(LivingEntity entity, LivingEntityRenderer<?, ?> renderer, float yaw, float partialTick,
                PoseStack poseStack, MultiBufferSource buffers, int light) {
            super(entity, renderer, yaw, partialTick, poseStack, buffers, light);
        }
    }

    public static final class Post extends RenderLivingEvent {
        public Post(LivingEntity entity, LivingEntityRenderer<?, ?> renderer, float yaw, float partialTick,
                PoseStack poseStack, MultiBufferSource buffers, int light) {
            super(entity, renderer, yaw, partialTick, poseStack, buffers, light);
        }
    }
}
