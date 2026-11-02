package io.github.mcheliceport.fabricmixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import io.github.mcheliceport.platform.ClientEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NeoForge's {@code RenderLevelStageEvent.AfterLevel} and {@code RenderHandEvent}. */
@Mixin(GameRenderer.class)
abstract class GameRendererMixin {

    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lorg/joml/Matrix4fc;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V"))
    private void mcheli$afterLevel(LevelRenderer renderer, GraphicsResourceAllocator pool, DeltaTracker delta,
            boolean outline, CameraRenderState camera, Matrix4fc modelView, GpuBufferSlice fog, Vector4f fogColor,
            boolean bossFog, Operation<Void> original) {
        original.call(renderer, pool, delta, outline, camera, modelView, fog, fogColor, bossFog);
        ClientEvents.fire(ClientEvents.AFTER_LEVEL, new ClientEvents.AfterLevel(renderer.levelRenderState, modelView));
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void mcheli$renderHand(CameraRenderState camera, float partialTicks, Matrix4fc projection, CallbackInfo ci) {
        var hand = new ClientEvents.Cancelable();
        ClientEvents.fire(ClientEvents.RENDER_HAND, hand);
        if (hand.isCanceled()) {
            ci.cancel();
        }
    }
}
