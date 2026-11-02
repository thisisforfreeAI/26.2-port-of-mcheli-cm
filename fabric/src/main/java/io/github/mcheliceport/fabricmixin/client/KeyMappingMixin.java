package io.github.mcheliceport.fabricmixin.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.mcheliceport.platform.fabric.inject.KeyMappingExtras;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Implements {@link KeyMappingExtras}. */
@Mixin(KeyMapping.class)
abstract class KeyMappingMixin implements KeyMappingExtras {
    @Shadow protected InputConstants.Key key;

    @Override
    public InputConstants.Key getKey() {
        return this.key;
    }
}
