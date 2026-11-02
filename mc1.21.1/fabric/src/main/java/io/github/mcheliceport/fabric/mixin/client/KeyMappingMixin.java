package io.github.mcheliceport.fabric.mixin.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.mcheliceport.shim.client.KeyContext;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla gives each key to one mapping; NeoForge gives it to every mapping whose context is
 * active. The port's mappings share keys with vanilla controls (W, A, S, D, the mouse buttons),
 * so they also get the presses of keys that vanilla gave to another mapping.
 */
@Mixin(KeyMapping.class)
abstract class KeyMappingMixin {
    @Shadow @Final private static Map<InputConstants.Key, KeyMapping> MAP;
    @Shadow private InputConstants.Key key;
    @Shadow private int clickCount;

    @Inject(method = "click", at = @At("TAIL"))
    private static void mcheli$click(InputConstants.Key key, CallbackInfo ci) {
        KeyMapping owner = MAP.get(key);
        KeyContext.CONTEXTS.forEach((mapping, context) -> {
            KeyMappingMixin self = (KeyMappingMixin) (Object) mapping;
            if (mapping != owner && key.equals(self.key) && context.isActive()) {
                self.clickCount++;
            }
        });
    }

    @Inject(method = "set", at = @At("TAIL"))
    private static void mcheli$set(InputConstants.Key key, boolean down, CallbackInfo ci) {
        KeyMapping owner = MAP.get(key);
        KeyContext.CONTEXTS.forEach((mapping, context) -> {
            KeyMappingMixin self = (KeyMappingMixin) (Object) mapping;
            if (mapping != owner && key.equals(self.key)) {
                mapping.setDown(down && context.isActive());
            }
        });
    }
}
