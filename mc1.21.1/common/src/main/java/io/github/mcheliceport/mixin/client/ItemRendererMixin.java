package io.github.mcheliceport.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.mcheliceport.client.render.ToolItemModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The hand tools' 3D models ({@link ToolItemModel}), in place of 26.2's custom item model type. */
@Mixin(ItemRenderer.class)
abstract class ItemRendererMixin {
    /** The entity holding the item being drawn, if any. */
    @Unique private LivingEntity mcheli$holder;

    @Inject(method = "renderStatic(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/level/Level;III)V",
            at = @At("HEAD"))
    private void mcheli$holder(LivingEntity entity, ItemStack stack, ItemDisplayContext context, boolean leftHand,
            PoseStack poseStack, MultiBufferSource buffers, Level level, int light, int overlay, int seed,
            CallbackInfo ci) {
        this.mcheli$holder = entity;
    }

    @Inject(method = "renderStatic(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/level/Level;III)V",
            at = @At("RETURN"))
    private void mcheli$noHolder(LivingEntity entity, ItemStack stack, ItemDisplayContext context, boolean leftHand,
            PoseStack poseStack, MultiBufferSource buffers, Level level, int light, int overlay, int seed,
            CallbackInfo ci) {
        this.mcheli$holder = null;
    }

    @Inject(method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V",
            at = @At("HEAD"), cancellable = true)
    private void mcheli$tool(ItemStack stack, ItemDisplayContext context, boolean leftHand, PoseStack poseStack,
            MultiBufferSource buffers, int light, int overlay, BakedModel model, CallbackInfo ci) {
        if (!stack.isEmpty() && ToolItemModel.render(stack, context, leftHand, poseStack, buffers, light, this.mcheli$holder)) {
            ci.cancel();
        }
    }
}
