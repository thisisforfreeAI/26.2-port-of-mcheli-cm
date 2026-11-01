package io.github.mcheliceport;

import com.mojang.logging.LogUtils;
import io.github.mcheliceport.config.CommonConfig;
import io.github.mcheliceport.config.ServerConfig;
import io.github.mcheliceport.item.AircraftDispenseBehavior;
import io.github.mcheliceport.item.ThrowableDispenseBehavior;
import io.github.mcheliceport.net.ModNetwork;
import io.github.mcheliceport.platform.Configs;
import io.github.mcheliceport.platform.Services;
import io.github.mcheliceport.platform.event.ModEvents;
import io.github.mcheliceport.registry.ModEntityTypes;
import io.github.mcheliceport.registry.ModRegistries;
import org.slf4j.Logger;

/**
 * Shared entry point of McHeli CE (1.21.1 Port), an unofficial port of McHeli: Community Edition.
 * The loader modules call {@link #init()} from their own entry points; it does what the 26.2
 * port's {@code @Mod} constructor does, through the platform layer.
 */
public final class McheliCePort {
    /** Must match {@code mod_id} in gradle.properties. */
    public static final String MOD_ID = "mcheli_ce_port";
    public static final Logger LOGGER = LogUtils.getLogger();

    private McheliCePort() {
    }

    public static void init() {
        LOGGER.info("Loading McHeli CE (1.21.1 Port) {} on {}. This is an unofficial port of McHeli CE: "
                + "report problems to the port, not to upstream.",
                Services.PLATFORM.modVersion(), Services.PLATFORM.name());

        Services.CONFIGS.register(Configs.Type.COMMON, CommonConfig.SPEC);
        Services.CONFIGS.register(Configs.Type.SERVER, ServerConfig.SPEC);
        ModRegistries.register(ModContentLoader.load());
        Services.REGISTRAR.entityAttributes(ModEntityTypes.GUNNER,
                () -> net.minecraft.world.entity.LivingEntity.createLivingAttributes().build());

        ModNetwork.register();
        io.github.mcheliceport.wingman.handler.ProjectileChunkLoader.register();
        io.github.mcheliceport.item.ChainItem.register();
        ModEvents.TAGS_UPDATED.register(io.github.mcheliceport.weapon.BlockList::clearCaches);
        io.github.mcheliceport.radar.EntityInfoFeed.register();
        io.github.mcheliceport.wingman.handler.WingmanTickHandler.register();
        io.github.mcheliceport.combat.LagCompensation.register();
        io.github.mcheliceport.combat.WatcherMotion.register();
        io.github.mcheliceport.combat.AntiCheat.register();
        io.github.mcheliceport.damage.ComponentEffects.register();
        io.github.mcheliceport.weather.FlightWeather.register();
        io.github.mcheliceport.command.McheliCommand.registerEvents();
        io.github.mcheliceport.command.WingmanCommand.registerEvents();
        io.github.mcheliceport.command.ClientRequests.registerEvents();
        io.github.mcheliceport.effectrange.EffectsRange.registerEvents();
        io.github.mcheliceport.resources.ModPackFinders.register();

        Services.REGISTRAR.afterRegistration(() -> {
            AircraftDispenseBehavior.register();
            ThrowableDispenseBehavior.register();
            if (CommonConfig.DEBUG_LOG.getAsBoolean()) {
                ModRegistries.logSummary();
            }
        });
    }
}
