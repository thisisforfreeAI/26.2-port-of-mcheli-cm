package io.github.mcheliceport.fabric;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client-only half of {@link FabricNetwork}. */
final class FabricClientNetwork {

    private FabricClientNetwork() {
    }

    static <T extends CustomPacketPayload> void register(FabricNetwork.ClientReceiver<T> receiver) {
        ClientPlayNetworking.registerGlobalReceiver(receiver.type(), (payload, context) ->
                receiver.handler().handle(payload, FabricNetwork.context(context.player(), context.client()::execute)));
    }
}
