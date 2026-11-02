package io.github.mcheliceport.platform.loader;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.mcheliceport.platform.KeyContext;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

/** Creates the mod's key bindings with their {@link KeyContext}. */
public final class ModKeyMapping {

    private ModKeyMapping() {
    }

    public static KeyMapping create(String name, KeyContext context, InputConstants.Type type, int code,
            KeyMapping.Category category) {
        return new KeyMapping(name, conflictContext(context), type, code, category);
    }

    private static IKeyConflictContext conflictContext(KeyContext context) {
        return new IKeyConflictContext() {
            @Override
            public boolean isActive() {
                return context.isActive();
            }

            @Override
            public boolean conflicts(IKeyConflictContext other) {
                // KeyModifier.NONE suppresses keys while Ctrl/Shift/Alt is held unless this context
                // conflicts with IN_GAME. Ctrl itself and Ctrl+C/I/F must all remain usable while riding.
                return other == this || other == KeyConflictContext.IN_GAME;
            }
        };
    }
}
