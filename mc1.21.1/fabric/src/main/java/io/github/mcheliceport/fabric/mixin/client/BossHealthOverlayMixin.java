package io.github.mcheliceport.fabric.mixin.client;

import io.github.mcheliceport.shim.client.event.ClientBus;
import io.github.mcheliceport.shim.client.event.RenderGuiLayerEvent;
import io.github.mcheliceport.shim.client.event.VanillaGuiLayers;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.BossHealthOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BossHealthOverlay.class)
abstract class BossHealthOverlayMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void mcheli$render(GuiGraphics graphics, CallbackInfo ci) {
        if (ClientBus.post(new RenderGuiLayerEvent.Pre(VanillaGuiLayers.BOSS_OVERLAY)).isCanceled()) {
            ci.cancel();
        }
    }
}
