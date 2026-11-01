package io.github.mcheliceport.fabric.mixin.client;

import io.github.mcheliceport.shim.client.event.ClientBus;
import io.github.mcheliceport.shim.client.event.InputEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NeoForge's {@code InputEvent.MouseScrollingEvent}: in game, before the hotbar takes the wheel. */
@Mixin(MouseHandler.class)
abstract class MouseHandlerMixin {
    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "onScroll", cancellable = true, at = @At(value = "FIELD", ordinal = 0,
            target = "Lnet/minecraft/client/Minecraft;player:Lnet/minecraft/client/player/LocalPlayer;"))
    private void mcheli$scroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
        if (this.minecraft.player == null) {
            return;
        }
        boolean discrete = this.minecraft.options.discreteMouseScroll().get();
        double sensitivity = this.minecraft.options.mouseWheelSensitivity().get();
        double dx = (discrete ? Math.signum(xOffset) : xOffset) * sensitivity;
        double dy = (discrete ? Math.signum(yOffset) : yOffset) * sensitivity;
        if (ClientBus.post(new InputEvent.MouseScrollingEvent(dx, dy)).isCanceled()) {
            ci.cancel();
        }
    }
}
