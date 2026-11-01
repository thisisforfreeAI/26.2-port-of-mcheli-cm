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
    }
}
