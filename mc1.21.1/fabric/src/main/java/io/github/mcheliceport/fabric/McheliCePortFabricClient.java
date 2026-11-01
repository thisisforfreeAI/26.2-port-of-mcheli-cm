package io.github.mcheliceport.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Fabric client entry point: the client half of the payload handlers. */
public final class McheliCePortFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        FabricNetwork.clientSender = ClientPlayNetworking::send;
        FabricNetwork.registerClientReceivers();
    }
}
