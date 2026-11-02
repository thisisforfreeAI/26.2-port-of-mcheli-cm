package io.github.mcheliceport.platform.fabric.inject;

import com.mojang.blaze3d.platform.InputConstants;

/** NeoForge's {@code KeyMapping#getKey}: the key the binding is bound to now. */
public interface KeyMappingExtras {
    default InputConstants.Key getKey() {
        throw new AssertionError("implemented by KeyMappingMixin");
    }
}
