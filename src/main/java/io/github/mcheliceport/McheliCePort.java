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
        registerEvents();
    }

    /** The shared game-event listeners; each loader fires {@link io.github.mcheliceport.platform.GameEvents}. */
    private static void registerEvents() {
        io.github.mcheliceport.entity.uav.UavRemoteSession.register();
        io.github.mcheliceport.wingman.handler.MissionChunkLoader.register();
        io.github.mcheliceport.wingman.handler.ProjectileChunkLoader.register();
        io.github.mcheliceport.item.ChainItem.register();
        io.github.mcheliceport.platform.GameEvents.TAGS_UPDATED.add(io.github.mcheliceport.weapon.BlockList::clearCaches);
        io.github.mcheliceport.radar.EntityInfoFeed.register();
        io.github.mcheliceport.wingman.handler.WingmanTickHandler.register();
        io.github.mcheliceport.combat.LagCompensation.register();
        io.github.mcheliceport.combat.WatcherMotion.register();
        io.github.mcheliceport.combat.AntiCheat.register();
        io.github.mcheliceport.damage.ComponentEffects.register();
        io.github.mcheliceport.weather.FlightWeather.register();
        io.github.mcheliceport.command.ClientRequests.register();
        io.github.mcheliceport.effectrange.EffectsRange.register();
        io.github.mcheliceport.resources.ModPackFinders.register();
        io.github.mcheliceport.platform.GameEvents.REGISTER_COMMANDS.add(
                io.github.mcheliceport.command.McheliCommand::register);
        io.github.mcheliceport.platform.GameEvents.REGISTER_COMMANDS.add(
                (dispatcher, context) -> io.github.mcheliceport.command.WingmanCommand.register(dispatcher));
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
