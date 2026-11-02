package io.github.mcheliceport.fabric;

import fuzs.forgeconfigapiport.fabric.api.neoforge.v4.NeoForgeConfigRegistry;
import fuzs.forgeconfigapiport.fabric.api.neoforge.v4.NeoForgeModConfigEvents;
import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.platform.Configs;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/** NeoForge's config system on Fabric, through Forge Config API Port. */
public final class FabricConfigs implements Configs {

    @Override
    public void register(Type type, ModConfigSpec spec) {
        NeoForgeConfigRegistry.INSTANCE.register(McheliCePort.MOD_ID, switch (type) {
            case COMMON -> ModConfig.Type.COMMON;
            case SERVER -> ModConfig.Type.SERVER;
            case CLIENT -> ModConfig.Type.CLIENT;
        }, spec);
    }

    @Override
    public void onLoadOrReload(ModConfigSpec spec, Runnable listener) {
        NeoForgeModConfigEvents.loading(McheliCePort.MOD_ID).register(config -> {
            if (config.getSpec() == spec) {
                listener.run();
            }
        });
        onReload(spec, listener);
    }

    @Override
    public void onReload(ModConfigSpec spec, Runnable listener) {
        NeoForgeModConfigEvents.reloading(McheliCePort.MOD_ID).register(config -> {
            if (config.getSpec() == spec) {
                listener.run();
            }
        });
    }
}
