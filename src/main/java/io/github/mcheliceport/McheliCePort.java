package io.github.mcheliceport;

import com.mojang.logging.LogUtils;
import io.github.mcheliceport.config.CommonConfig;
import io.github.mcheliceport.config.ServerConfig;
import io.github.mcheliceport.item.AircraftDispenseBehavior;
import io.github.mcheliceport.item.ThrowableDispenseBehavior;
import io.github.mcheliceport.gametest.ModGameTests;
import io.github.mcheliceport.net.ModNetwork;
import io.github.mcheliceport.registry.ModRegistries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;
import io.github.mcheliceport.registry.ModBlocks;
import io.github.mcheliceport.registry.ModEntityTypes;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;

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
        modBus.addListener((net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent event) -> event.put(
                ModEntityTypes.GUNNER.get(), net.minecraft.world.entity.LivingEntity.createLivingAttributes().build()));
        modBus.addListener((RegisterCapabilitiesEvent event) -> {
            event.registerEntity(Capabilities.Item.ENTITY,
                    ModEntityTypes.CONTAINER.get(),
                    (cargo, context) -> VanillaContainerWrapper.of(cargo));
            event.registerEntity(Capabilities.Item.ENTITY_AUTOMATION,
                    ModEntityTypes.CONTAINER.get(),
                    (cargo, side) -> VanillaContainerWrapper.of(cargo));
            event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlocks.CHARGER_ENTITY.get(),
                    (charger, side) -> charger.energyHandler());
            for (var type : java.util.List.of(ModEntityTypes.HELICOPTER, ModEntityTypes.PLANE, ModEntityTypes.SHIP,
                    ModEntityTypes.TANK, ModEntityTypes.VEHICLE)) {
                event.registerEntity(Capabilities.Fluid.ENTITY, type.get(), (aircraft, side) -> aircraft.fluidHandler());
            }
            event.registerItem(Capabilities.Fluid.ITEM,
                    (stack, access) -> new net.neoforged.neoforge.transfer.fluid.BucketResourceHandler(access),
                    io.github.mcheliceport.registry.ModItems.FUEL_BUCKET.get());
        });
        ModNetwork.register(modBus);
        io.github.mcheliceport.entity.uav.UavRemoteSession.register(modBus);
        io.github.mcheliceport.wingman.handler.MissionChunkLoader.register(modBus);
        io.github.mcheliceport.wingman.handler.ProjectileChunkLoader.register(modBus);
        ModGameTests.register(modBus);
        io.github.mcheliceport.item.ChainItem.register();
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.TagsUpdatedEvent event) -> io.github.mcheliceport.weapon.BlockList.clearCaches());
        io.github.mcheliceport.radar.EntityInfoFeed.register();
        io.github.mcheliceport.wingman.handler.WingmanTickHandler.register();
        io.github.mcheliceport.combat.LagCompensation.register();
        io.github.mcheliceport.combat.WatcherMotion.register();
        io.github.mcheliceport.combat.AntiCheat.register();
        io.github.mcheliceport.damage.ComponentEffects.register();
        io.github.mcheliceport.weather.FlightWeather.register();
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(AircraftDispenseBehavior::register);
        event.enqueueWork(ThrowableDispenseBehavior::register);
        if (CommonConfig.DEBUG_LOG.getAsBoolean()) {
            ModRegistries.logSummary();
        }
    }
}
