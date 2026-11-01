package io.github.mcheliceport.neoforge;

import io.github.mcheliceport.platform.Configs;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class NeoForgeConfigs implements Configs {

    @Override
    public void register(Type type, ModConfigSpec spec) {
        McheliCePortNeoForge.container.registerConfig(switch (type) {
            case COMMON -> ModConfig.Type.COMMON;
            case SERVER -> ModConfig.Type.SERVER;
            case CLIENT -> ModConfig.Type.CLIENT;
        }, spec);
    }

    @Override
    public void onLoadOrReload(ModConfigSpec spec, Runnable listener) {
        McheliCePortNeoForge.modBus().addListener((ModConfigEvent.Loading event) -> {
            if (event.getConfig().getSpec() == spec) {
                listener.run();
            }
        });
        onReload(spec, listener);
    }

    @Override
    public void onReload(ModConfigSpec spec, Runnable listener) {
        McheliCePortNeoForge.modBus().addListener((ModConfigEvent.Reloading event) -> {
            if (event.getConfig().getSpec() == spec) {
                listener.run();
            }
        });
    }
}
