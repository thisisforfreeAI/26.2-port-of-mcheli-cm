package io.github.mcheliceport.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.mcheliceport.shim.client.LivingLightModifiers;
import io.github.mcheliceport.shim.client.event.ClientBus;
import io.github.mcheliceport.shim.client.event.RenderLivingEvent;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Posts the port's living render events around a living entity's drawing on both loaders, and
 * lets searchlights and scene lights brighten it (26.2's render state modifiers).
 */
@Mixin(LivingEntityRenderer.class)
abstract class LivingEntityRendererMixin {
    private static final String RENDER =
            "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V";

    @ModifyVariable(method = RENDER, at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int mcheli$light(int light, LivingEntity entity, float yaw, float partialTick, PoseStack pose,
            MultiBufferSource buffers, int packedLight) {
        return LivingLightModifiers.apply(entity, partialTick, light);
    }

    @Inject(method = RENDER, at = @At("HEAD"), cancellable = true)
    private void mcheli$pre(LivingEntity entity, float yaw, float partialTick, PoseStack pose,
            MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (ClientBus.post(new RenderLivingEvent.Pre(entity, (LivingEntityRenderer<?, ?>) (Object) this, yaw,
                partialTick, pose, buffers, light)).isCanceled()) {
            ci.cancel();
        }
    }

    @Inject(method = RENDER, at = @At("RETURN"))
    private void mcheli$post(LivingEntity entity, float yaw, float partialTick, PoseStack pose,
            MultiBufferSource buffers, int light, CallbackInfo ci) {
        ClientBus.post(new RenderLivingEvent.Post(entity, (LivingEntityRenderer<?, ?>) (Object) this, yaw,
                partialTick, pose, buffers, light));
    }
}
