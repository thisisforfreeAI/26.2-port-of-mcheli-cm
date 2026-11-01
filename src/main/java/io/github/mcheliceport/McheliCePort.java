package io.github.mcheliceport;

import com.mojang.logging.LogUtils;
import io.github.mcheliceport.config.CommonConfig;
import io.github.mcheliceport.item.AircraftDispenseBehavior;
import io.github.mcheliceport.item.ThrowableDispenseBehavior;
import io.github.mcheliceport.platform.Platform;
import io.github.mcheliceport.registry.ModRegistries;
import org.slf4j.Logger;

/**
 * McHeli CE (26.2 Port), an unofficial port of McHeli: Community Edition: the setup both loaders
 * share. The loader entry points are {@code platform.neoforge.NeoForgeMod} and the Fabric build's
 * {@code platform.fabric.FabricMod}.
 */
public final class McheliCePort {
    /** Must match {@code mod_id} in gradle.properties. */
    public static final String MOD_ID = "mcheli_ce_port";
    public static final Logger LOGGER = LogUtils.getLogger();

    private McheliCePort() {
    }

    /** Loads the content packs and declares every registry entry; called while the mod is constructed. */
    public static void init(String displayName, Platform.ModFiles modFiles, String modVersion) {
        LOGGER.info("Loading {} {} on {}. This is an unofficial port of McHeli CE: report problems to the port, "
                + "not to upstream.", displayName, modVersion, Platform.get().loaderName());
        ModRegistries.register(ModContentLoader.load(modFiles, modVersion));
    }

    /** Common setup once registries are filled (NeoForge's common setup, Fabric's init). */
    public static void setup() {
        AircraftDispenseBehavior.register();
        ThrowableDispenseBehavior.register();
        if (CommonConfig.DEBUG_LOG.getAsBoolean()) {
            ModRegistries.logSummary();
        }
    }
}
