package io.github.mcheliceport.platform.fabric;

import fuzs.forgeconfigapiport.fabric.api.v5.ConfigRegistry;
import fuzs.forgeconfigapiport.fabric.api.v5.ModConfigEvents;
import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.config.CommonConfig;
import io.github.mcheliceport.config.ServerConfig;
import io.github.mcheliceport.net.ModNetwork;
import io.github.mcheliceport.registry.ModEntityTypes;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.config.ModConfig;
import org.jspecify.annotations.Nullable;

/** Fabric entry point of McHeli CE (26.2 Port); the loader-neutral setup is in {@link McheliCePort}. */
public final class FabricMod implements ModInitializer {
    private static @Nullable MinecraftServer server;

    @Override
    public void onInitialize() {
        String version = FabricLoader.getInstance().getModContainer(McheliCePort.MOD_ID).orElseThrow()
                .getMetadata().getVersion().getFriendlyString();
        McheliCePort.init("McHeli CE (26.2 Port)", FabricPlatform.modFiles(), version);

        ConfigRegistry.INSTANCE.register(McheliCePort.MOD_ID, ModConfig.Type.COMMON, CommonConfig.SPEC);
        ConfigRegistry.INSTANCE.register(McheliCePort.MOD_ID, ModConfig.Type.SERVER, ServerConfig.SPEC);
        ModConfigEvents.reloading(McheliCePort.MOD_ID).register(config -> {
            if (config.getSpec() == ServerConfig.SPEC && config.getType() == ModConfig.Type.SERVER) {
                ModNetwork.onServerConfigReloaded(server);
            }
        });
        ServerLifecycleEvents.SERVER_STARTING.register(started -> server = started);
        ServerLifecycleEvents.SERVER_STOPPED.register(stopped -> server = null);

        FabricDefaultAttributeRegistry.register(ModEntityTypes.GUNNER.get(), LivingEntity.createLivingAttributes());
        FabricNetwork.register();
        McheliCePort.setup();
    }
}
