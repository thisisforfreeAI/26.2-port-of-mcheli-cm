package io.github.mcheliceport.neoforge;

import io.github.mcheliceport.platform.ClientPlatform;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import org.jspecify.annotations.Nullable;

public final class NeoForgeClientPlatform implements ClientPlatform {
    @Override
    public @Nullable Screen configScreen(@Nullable Screen parent) {
        return McheliCePortNeoForge.container == null ? null
                : new ConfigurationScreen(McheliCePortNeoForge.container, parent);
    }
}
