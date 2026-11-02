package io.github.mcheliceport.fabricmixin.client;

import io.github.mcheliceport.platform.ClientEvents;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** NeoForge's {@code RenderFrameEvent}, {@code FlipFrameEvent} and {@code InputEvent.InteractionKeyMappingTriggered} (attack and use). */
@Mixin(Minecraft.class)
abstract class MinecraftClientMixin {

    @Inject(method = "renderFrame", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;render(Lnet/minecraft/client/DeltaTracker;Z)V"))
    private void mcheli$framePre(boolean advanceGameTime, CallbackInfo ci) {
        ClientEvents.run(ClientEvents.RENDER_FRAME_PRE);
    }

    @Inject(method = "renderFrame", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;render(Lnet/minecraft/client/DeltaTracker;Z)V",
            shift = At.Shift.AFTER))
    private void mcheli$framePost(boolean advanceGameTime, CallbackInfo ci) {
        ClientEvents.run(ClientEvents.RENDER_FRAME_POST);
    }

    @Inject(method = "renderFrame", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;endFrame()V", shift = At.Shift.AFTER))
    private void mcheli$flipFrame(boolean advanceGameTime, CallbackInfo ci) {
        ClientEvents.run(ClientEvents.FLIP_FRAME);
    }

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void mcheli$attack(CallbackInfoReturnable<Boolean> cir) {
        if (mcheli$interactionCanceled(true)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void mcheli$continueAttack(boolean down, CallbackInfo ci) {
        if (down && mcheli$interactionCanceled(true)) {
            ci.cancel();
        }
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void mcheli$use(CallbackInfo ci) {
        if (mcheli$interactionCanceled(false)) {
            ci.cancel();
        }
    }

    private static boolean mcheli$interactionCanceled(boolean attack) {
        var key = new ClientEvents.InteractionKey(attack, !attack);
        ClientEvents.fire(ClientEvents.INTERACTION_KEY, key);
        return key.isCanceled();
    }
}
