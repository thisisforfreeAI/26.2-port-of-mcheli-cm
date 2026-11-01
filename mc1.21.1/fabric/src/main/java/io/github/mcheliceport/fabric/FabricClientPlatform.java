package io.github.mcheliceport.fabric;

import io.github.mcheliceport.platform.ClientPlatform;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

/** Fabric has no built-in config screen; the config files are edited directly. */
public final class FabricClientPlatform implements ClientPlatform {
    @Override
    public @Nullable Screen configScreen(@Nullable Screen parent) {
        return null;
    }
}
