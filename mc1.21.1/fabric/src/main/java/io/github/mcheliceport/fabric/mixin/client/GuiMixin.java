package io.github.mcheliceport.fabric.mixin.client;

import io.github.mcheliceport.shim.client.event.ClientBus;
import io.github.mcheliceport.shim.client.event.RenderGuiLayerEvent;
import io.github.mcheliceport.shim.client.event.VanillaGuiLayers;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NeoForge's {@code RenderGuiLayerEvent.Pre} for the vanilla HUD parts the port hides. */
@Mixin(Gui.class)
abstract class GuiMixin {
    private static boolean mcheli$hidden(ResourceLocation layer) {
        return ClientBus.post(new RenderGuiLayerEvent.Pre(layer)).isCanceled();
    }

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void mcheli$crosshair(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
        if (mcheli$hidden(VanillaGuiLayers.CROSSHAIR)) {
            ci.cancel();
        }
    }

    @Inject(method = "renderItemHotbar", at = @At("HEAD"), cancellable = true)
    private void mcheli$hotbar(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
        if (mcheli$hidden(VanillaGuiLayers.HOTBAR)) {
            ci.cancel();
        }
    }

    /** Vanilla draws health, armour, food and air in one method; NeoForge splits them into layers. */
    @Inject(method = "renderPlayerHealth", at = @At("HEAD"), cancellable = true)
    private void mcheli$health(GuiGraphics graphics, CallbackInfo ci) {
        if (mcheli$hidden(VanillaGuiLayers.PLAYER_HEALTH)) {
            ci.cancel();
        }
    }
}
