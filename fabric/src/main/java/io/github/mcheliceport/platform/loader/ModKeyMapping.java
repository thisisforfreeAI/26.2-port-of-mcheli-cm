package io.github.mcheliceport.platform.loader;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.mcheliceport.platform.KeyContext;
import net.minecraft.client.KeyMapping;

/**
 * Creates the mod's key bindings. Vanilla has no conflict contexts and sets every binding on a
 * pressed key, so a binding here reads as up, and drops its clicks, while its context is inactive,
 * as NeoForge's do.
 */
public final class ModKeyMapping {

    private ModKeyMapping() {
    }

    public static KeyMapping create(String name, KeyContext context, InputConstants.Type type, int code,
            KeyMapping.Category category) {
        return new KeyMapping(name, type, code, category) {
            @Override
            public boolean isDown() {
                return super.isDown() && context.isActive();
            }

            @Override
            public boolean consumeClick() {
                if (context.isActive()) {
                    return super.consumeClick();
                }
                while (super.consumeClick()) {
                    // Clicks made outside the context never act.
                }
                return false;
            }
        };
    }
}
