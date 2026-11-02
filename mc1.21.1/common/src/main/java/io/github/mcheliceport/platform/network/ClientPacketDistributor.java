package io.github.mcheliceport.platform.network;

import io.github.mcheliceport.platform.Services;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: NeoForge's {@code ClientPacketDistributor}, on either loader. */
public final class ClientPacketDistributor {

    private ClientPacketDistributor() {
    }

    public static void sendToServer(CustomPacketPayload payload) {
        Services.NETWORK.sendToServer(payload);
    }
}
