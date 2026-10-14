package io.github.mcheliceport;

import com.mojang.logging.LogUtils;
import io.github.mcheliceport.config.CommonConfig;
import io.github.mcheliceport.config.ServerConfig;
import io.github.mcheliceport.item.AircraftDispenseBehavior;
import io.github.mcheliceport.net.ModNetwork;
import io.github.mcheliceport.registry.ModRegistries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;

/**
 * Entry point of McHeli CE (26.2 Port), an unofficial port of McHeli: Community Edition.
 *
 * <p>Client-only setup lives in {@link io.github.mcheliceport.client.McheliCePortClient}.
 */
@Mod(McheliCePort.MOD_ID)
public final class McheliCePort {
    /** Must match {@code mod_id} in gradle.properties. */
    public static final String MOD_ID = "mcheli_ce_port";
    public static final Logger LOGGER = LogUtils.getLogger();

    public McheliCePort(IEventBus modBus, ModContainer container) {
        LOGGER.info("Loading {} {}. This is an unofficial port of McHeli CE: report problems to the port, not to upstream.",
                container.getModInfo().getDisplayName(), container.getModInfo().getVersion());

        ModRegistries.register(modBus, ModContentLoader.load(container));
        container.registerConfig(ModConfig.Type.COMMON, CommonConfig.SPEC);
        container.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);

        modBus.addListener(this::commonSetup);
        ModNetwork.register(modBus);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(AircraftDispenseBehavior::register);
        if (CommonConfig.DEBUG_LOG.getAsBoolean()) {
            ModRegistries.logSummary();
        }
    }
}
