package io.github.mcheliceport.neoforge;

import io.github.mcheliceport.McheliCePort;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/** NeoForge entry point: hands over to the shared {@link McheliCePort#init()}. */
@Mod(McheliCePort.MOD_ID)
public final class McheliCePortNeoForge {

    static IEventBus modBus;
    static ModContainer container;

    public McheliCePortNeoForge(IEventBus modBus, ModContainer container) {
        McheliCePortNeoForge.modBus = modBus;
        McheliCePortNeoForge.container = container;
        NeoForgeEvents.register(modBus);
        McheliCePort.init();
        NeoForgeCapabilities.register(modBus);
    }

    static IEventBus modBus() {
        if (modBus == null) {
            throw new IllegalStateException("The NeoForge entry point has not run yet");
        }
        return modBus;
    }
}
