package io.github.mcheliceport.fabric;

import io.github.mcheliceport.McheliCePort;
import net.fabricmc.api.ModInitializer;

/** Fabric entry point: hands over to the shared {@link McheliCePort#init()}. */
public final class McheliCePortFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        McheliCePort.init();
    }
}
