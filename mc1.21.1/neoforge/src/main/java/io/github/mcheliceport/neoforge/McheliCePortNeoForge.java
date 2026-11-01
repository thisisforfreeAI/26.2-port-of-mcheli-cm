package io.github.mcheliceport.neoforge;

import io.github.mcheliceport.McheliCePort;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/** NeoForge entry point: hands over to the shared {@link McheliCePort#init()}. */
@Mod(McheliCePort.MOD_ID)
public final class McheliCePortNeoForge {

    public McheliCePortNeoForge(IEventBus modBus, ModContainer container) {
        McheliCePort.init();
    }
}
