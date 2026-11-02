package io.github.mcheliceport.platform;

import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

/** Client-only loader hooks. Loaded on first use, which is only ever on the client. */
public interface ClientPlatform {
    ClientPlatform INSTANCE = Services.load(ClientPlatform.class);

    /** The loader's config screen for this mod (NeoForge's {@code ConfigurationScreen}), or null. */
    @Nullable Screen configScreen(@Nullable Screen parent);
}
