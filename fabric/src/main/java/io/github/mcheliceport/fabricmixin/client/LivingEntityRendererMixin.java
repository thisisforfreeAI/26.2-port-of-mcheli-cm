package io.github.mcheliceport.fabricmixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.mcheliceport.platform.ClientEvents;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NeoForge's {@code RenderLivingEvent.Pre} (cancellable) and {@code Post}, where NeoForge posts them. */
@Mixin(LivingEntityRenderer.class)
abstract class LivingEntityRendererMixin {

    private static final String SUBMIT = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;"
            + "Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V";

    @Inject(method = SUBMIT, at = @At("HEAD"), cancellable = true)
    private void mcheli$pre(LivingEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
            CameraRenderState camera, CallbackInfo ci) {
        var event = new ClientEvents.RenderLiving((LivingEntityRenderer<?, ?, ?>) (Object) this, state, poseStack, collector);
        ClientEvents.fire(ClientEvents.RENDER_LIVING_PRE, event);
        if (event.isCanceled()) {
            ci.cancel();
        }
    }

    @Inject(method = SUBMIT, at = @At("TAIL"))
    private void mcheli$post(LivingEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
            CameraRenderState camera, CallbackInfo ci) {
        ClientEvents.fire(ClientEvents.RENDER_LIVING_POST,
                new ClientEvents.RenderLiving((LivingEntityRenderer<?, ?, ?>) (Object) this, state, poseStack, collector));
    }
}
