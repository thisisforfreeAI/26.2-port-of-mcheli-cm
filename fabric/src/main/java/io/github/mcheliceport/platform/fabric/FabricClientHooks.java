package io.github.mcheliceport.platform.fabric;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jspecify.annotations.Nullable;

/**
 * The client-only parts of {@link FabricPlatform}. Kept apart so that loading FabricPlatform on a
 * dedicated server never loads a client class (the verifier would, for a screen it returns).
 */
final class FabricClientHooks {

    private FabricClientHooks() {
    }

    static Screen configScreen(@Nullable Screen parent) {
        return new net.neoforged.neoforge.client.gui.ConfigurationScreen(io.github.mcheliceport.McheliCePort.MOD_ID,
                parent);
    }

    static void sendToServer(CustomPacketPayload payload) {
        ClientPlayNetworking.send(payload);
    }
}
