package io.github.mcheliceport.fabric.mixin.client;

import io.github.mcheliceport.shim.client.event.ClientBus;
import io.github.mcheliceport.shim.client.event.InputEvent;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** NeoForge's {@code InputEvent.InteractionKeyMappingTriggered} for attack, use and pick block. */
@Mixin(Minecraft.class)
abstract class MinecraftMixin {
    private boolean mcheli$cancelled(int button, KeyMapping mapping) {
        Minecraft mc = (Minecraft) (Object) this;
        var event = ClientBus.post(new InputEvent.InteractionKeyMappingTriggered(button, mapping, InteractionHand.MAIN_HAND));
        if (!event.isCanceled()) {
            return false;
        }
        if (event.shouldSwingHand() && mc.player != null) {
            mc.player.swing(InteractionHand.MAIN_HAND);
        }
        return true;
    }

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void mcheli$attack(CallbackInfoReturnable<Boolean> cir) {
        if (mcheli$cancelled(0, ((Minecraft) (Object) this).options.keyAttack)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void mcheli$continueAttack(boolean leftClick, CallbackInfo ci) {
        Minecraft mc = (Minecraft) (Object) this;
        if (leftClick && mc.player != null && !mc.player.isUsingItem()) {
            var event = ClientBus.post(new InputEvent.InteractionKeyMappingTriggered(0, mc.options.keyAttack,
                    InteractionHand.MAIN_HAND));
            if (event.isCanceled()) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void mcheli$use(CallbackInfo ci) {
        if (mcheli$cancelled(1, ((Minecraft) (Object) this).options.keyUse)) {
            ci.cancel();
        }
    }

    @Inject(method = "pickBlock", at = @At("HEAD"), cancellable = true)
    private void mcheli$pick(CallbackInfo ci) {
        if (mcheli$cancelled(2, ((Minecraft) (Object) this).options.keyPickItem)) {
            ci.cancel();
        }
    }
}
