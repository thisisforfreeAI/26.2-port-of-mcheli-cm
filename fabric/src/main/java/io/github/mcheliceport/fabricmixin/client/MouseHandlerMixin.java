package io.github.mcheliceport.fabricmixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import io.github.mcheliceport.platform.ClientEvents;
import net.minecraft.client.MouseHandler;
import org.joml.Vector2i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NeoForge's {@code InputEvent.MouseScrollingEvent}: the wheel turned in the world, before the hotbar moves. */
@Mixin(MouseHandler.class)
abstract class MouseHandlerMixin {

    @Inject(method = "onScroll", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;isSpectator()Z"), cancellable = true)
    private void mcheli$scroll(long window, double xOffset, double yOffset, CallbackInfo ci,
            @Local Vector2i wheel) {
        var scroll = new ClientEvents.MouseScroll(wheel.y);
        ClientEvents.fire(ClientEvents.MOUSE_SCROLL, scroll);
        if (scroll.isCanceled()) {
            ci.cancel();
        }
    }
}
