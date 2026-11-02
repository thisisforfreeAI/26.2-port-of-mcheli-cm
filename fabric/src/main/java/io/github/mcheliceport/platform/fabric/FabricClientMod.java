package io.github.mcheliceport.platform.fabric;

import fuzs.forgeconfigapiport.fabric.api.v5.ConfigRegistry;
import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.config.ClientConfig;
import net.fabricmc.api.ClientModInitializer;
import net.neoforged.fml.config.ModConfig;

/** Fabric client entry point. */
public final class FabricClientMod implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ConfigRegistry.INSTANCE.register(McheliCePort.MOD_ID, ModConfig.Type.CLIENT, ClientConfig.SPEC);
        FabricNetwork.registerClientReceivers();
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents.ENTITY_LOAD.register(
                (entity, level) -> FabricParts.add(entity));
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents.ENTITY_UNLOAD.register(
                (entity, level) -> FabricParts.remove(entity));
        // The shared listeners first: FabricClientEvents fires the registration events right away.
        io.github.mcheliceport.client.McheliCePortClient.init();
        FabricClientEvents.register();
        FabricClientEvents.registerContent();
    }
}
