package io.github.mcheliceport.fabricmixin.client;

import io.github.mcheliceport.platform.ClientEvents;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NeoForge's {@code InputEvent.Key}, after the game handled the key. */
@Mixin(KeyboardHandler.class)
abstract class KeyboardHandlerMixin {

    @Inject(method = "keyPress", at = @At("TAIL"))
    private void mcheli$key(long window, int action, KeyEvent event, CallbackInfo ci) {
        ClientEvents.fire(ClientEvents.KEY, new ClientEvents.KeyInput(event, action));
    }
}
