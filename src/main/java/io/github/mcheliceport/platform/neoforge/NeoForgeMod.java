package io.github.mcheliceport.platform.neoforge;

import io.github.mcheliceport.McheliCePort;

import io.github.mcheliceport.config.CommonConfig;
import io.github.mcheliceport.config.ServerConfig;
import io.github.mcheliceport.gametest.ModGameTests;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import io.github.mcheliceport.registry.ModBlocks;
import io.github.mcheliceport.registry.ModEntityTypes;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;

/**
 * NeoForge entry point of McHeli CE (26.2 Port); the loader-neutral setup is in {@link McheliCePort}.
 *
 * <p>Client-only setup lives in {@link io.github.mcheliceport.client.McheliCePortClient}.
 */
@Mod(McheliCePort.MOD_ID)
public final class NeoForgeMod {

    public NeoForgeMod(IEventBus modBus, ModContainer container) {
        McheliCePort.init(container.getModInfo().getDisplayName(), NeoForgePlatform.modFiles(container),
                container.getModInfo().getVersion().toString());
        NeoForgePlatform.bind(modBus);
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
                    (charger, side) -> new ChargerEnergyHandler(charger));
            for (var type : java.util.List.of(ModEntityTypes.HELICOPTER, ModEntityTypes.PLANE, ModEntityTypes.SHIP,
                    ModEntityTypes.TANK, ModEntityTypes.VEHICLE)) {
                event.registerEntity(Capabilities.Fluid.ENTITY, type.get(), (aircraft, side) -> aircraft.hasFuelTank() ? new AircraftFluidHandler(aircraft) : null);
            }
            event.registerItem(Capabilities.Fluid.ITEM,
                    (stack, access) -> new net.neoforged.neoforge.transfer.fluid.BucketResourceHandler(access),
                    io.github.mcheliceport.registry.ModItems.FUEL_BUCKET.get());
        });
        NeoForgeNetwork.register(modBus);
        NeoForgeEvents.register(modBus);
        ModGameTests.register(modBus);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(McheliCePort::setup);
    }
}
