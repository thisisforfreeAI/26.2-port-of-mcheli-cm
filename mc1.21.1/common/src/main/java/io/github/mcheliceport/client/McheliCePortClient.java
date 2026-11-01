package io.github.mcheliceport.client;

import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.registry.ModFluids;
import io.github.mcheliceport.config.ClientConfig;
import io.github.mcheliceport.client.gui.AircraftScreen;
import io.github.mcheliceport.client.hud.AircraftHudLayer;
import io.github.mcheliceport.client.hud.GuiHudCanvas;
import io.github.mcheliceport.client.input.ModKeyMappings;
import io.github.mcheliceport.client.model.AircraftModels;
import io.github.mcheliceport.client.particle.ClientEffects;
import io.github.mcheliceport.effect.McheliEffects;
import io.github.mcheliceport.client.model.BulletModels;
import io.github.mcheliceport.client.render.A10Renderer;
import io.github.mcheliceport.client.render.BulletRenderer;
import io.github.mcheliceport.client.render.FlareRenderer;
import io.github.mcheliceport.client.render.HeliRenderer;
import io.github.mcheliceport.client.weapon.WeaponClient;
import io.github.mcheliceport.entity.weapon.BaseBulletEntity;
import java.util.Set;
import io.github.mcheliceport.client.render.RiderTilt;
import io.github.mcheliceport.client.render.ThrowableRenderer;
import io.github.mcheliceport.registry.ModEntityTypes;
import io.github.mcheliceport.registry.ModMenuTypes;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import io.github.mcheliceport.shim.client.event.AddClientReloadListenersEvent;
import io.github.mcheliceport.shim.client.event.EntityRenderersEvent;
import io.github.mcheliceport.shim.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import io.github.mcheliceport.client.render.CargoContainerRenderer;

/** Client-only entry point. FML never loads this class on a dedicated server. */
@Mod(value = McheliCePort.MOD_ID, dist = Dist.CLIENT)
public final class McheliCePortClient {
    public McheliCePortClient(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        // Adds a Config button for the mod on the Mods screen.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        io.github.mcheliceport.shim.client.event.ClientBus.listen(McheliCePortClient.class, "registerRenderers");
        io.github.mcheliceport.shim.client.event.ClientBus.listen(McheliCePortClient.class, "registerFluidModels");
        io.github.mcheliceport.shim.client.event.ClientBus.listen(ModKeyMappings.class, "register");
        io.github.mcheliceport.shim.client.event.ClientBus.listen(McheliCePortClient.class, "registerScreens");
        io.github.mcheliceport.shim.client.event.ClientBus.listen(RiderTilt.class, "registerModifiers");
        io.github.mcheliceport.client.render.ThermalVision.register();
        io.github.mcheliceport.shim.client.event.ClientBus.listen(io.github.mcheliceport.client.render.FarVehicles.class, "registerPipelines");
        io.github.mcheliceport.client.render.baked.BakedVehicles.register(modBus);
        io.github.mcheliceport.shim.client.event.ClientBus.listen(io.github.mcheliceport.client.render.FarVehicles.class, "onClientTick");
        io.github.mcheliceport.shim.client.event.ClientBus.listen(io.github.mcheliceport.client.render.SearchLightRenderer.class, "registerModifiers");
        io.github.mcheliceport.shim.client.event.ClientBus.listen(io.github.mcheliceport.client.render.SceneLights.class, "registerModifiers");
        io.github.mcheliceport.shim.client.event.ClientBus.listen(io.github.mcheliceport.client.render.SearchLightRenderer.class, "registerPipelines");
        io.github.mcheliceport.shim.client.event.ClientBus.listen(AircraftHudLayer.class, "register");
        io.github.mcheliceport.shim.client.event.ClientBus.listen(io.github.mcheliceport.client.hud.ToolHudLayer.class, "register");
        io.github.mcheliceport.shim.client.event.ClientBus.listen(io.github.mcheliceport.client.hud.SpawnGunnerHudLayer.class, "register");
        io.github.mcheliceport.shim.client.event.ClientBus.listen(io.github.mcheliceport.client.hud.InteractSeatHudLayer.class, "register");
        io.github.mcheliceport.shim.client.event.ClientBus.listen(io.github.mcheliceport.client.hud.LockMarkerHudLayer.class, "register");
        io.github.mcheliceport.shim.client.event.ClientBus.listen(io.github.mcheliceport.client.hud.RwrHudLayer.class, "register");
        io.github.mcheliceport.shim.client.event.ClientBus.listen(io.github.mcheliceport.client.hud.BvrHudLayer.class, "register");
        io.github.mcheliceport.shim.client.event.ClientBus.listen(io.github.mcheliceport.client.hud.MortarRadarHudLayer.class, "register");
        io.github.mcheliceport.shim.client.event.ClientBus.listen(io.github.mcheliceport.client.hud.GpsHudLayer.class, "register");
        io.github.mcheliceport.shim.client.event.ClientBus.listen(io.github.mcheliceport.client.hud.WingmanKeyHintLayer.class, "register");
        io.github.mcheliceport.shim.client.event.ClientBus.listen(io.github.mcheliceport.client.hud.ComponentStatusLayer.class, "register");
        io.github.mcheliceport.shim.client.event.ClientBus.listen(io.github.mcheliceport.client.hud.CrewHudLayer.class, "register");
        io.github.mcheliceport.shim.client.event.ClientBus.listen(io.github.mcheliceport.client.hud.GroundCrewHudLayer.class, "register");
        io.github.mcheliceport.shim.client.event.ClientBus.listen(io.github.mcheliceport.client.hud.WeatherHudLayer.class, "register");
        io.github.mcheliceport.shim.client.event.ClientBus.listen(io.github.mcheliceport.client.effect.EffectsRangeClient.class, "registerGui");
        io.github.mcheliceport.shim.client.event.ClientBus.listen(io.github.mcheliceport.client.hud.CockpitFeedbackLayer.class, "register");
        io.github.mcheliceport.shim.client.event.ClientBus.listen(io.github.mcheliceport.client.render.GunnerRenderer.class, "registerLayer");
        io.github.mcheliceport.shim.client.event.ClientBus.listen(io.github.mcheliceport.client.hud.TeamListOverlay.class, "register");
        io.github.mcheliceport.shim.client.event.ClientBus.listen(io.github.mcheliceport.client.hud.GltdHudLayer.class, "register");
        io.github.mcheliceport.shim.client.event.ClientBus.listen(io.github.mcheliceport.client.hud.LightWeaponHudLayer.class, "register");
        io.github.mcheliceport.shim.client.event.ClientBus.listen(io.github.mcheliceport.client.hud.TitleOverlay.class, "register");
        io.github.mcheliceport.shim.client.event.ClientBus.listen(AircraftHudLayer.class, "registerPipelines");
        io.github.mcheliceport.shim.client.event.ClientBus.listen(io.github.mcheliceport.client.particle.ParticleLayers.class, "registerPipelines");
        io.github.mcheliceport.shim.client.event.ClientBus.listen(io.github.mcheliceport.client.particle.BlastDustWedges.class, "register");
        io.github.mcheliceport.shim.client.event.ClientBus.listen(io.github.mcheliceport.client.particle.AirflowFireParticle.class, "register");
        io.github.mcheliceport.shim.client.event.ClientBus.listen(io.github.mcheliceport.client.particle.CondensationParticle.class, "register");
        io.github.mcheliceport.shim.client.event.ClientBus.listen(io.github.mcheliceport.client.particle.SupersonicConeParticle.class, "register");
        io.github.mcheliceport.shim.client.event.ClientBus.listen(io.github.mcheliceport.client.render.EngineHeat.class, "registerPipelines");
        io.github.mcheliceport.shim.client.event.ClientBus.listen(io.github.mcheliceport.client.render.GlowGeometry.class, "registerPipelines");
        io.github.mcheliceport.shim.client.event.ClientBus.listen(io.github.mcheliceport.client.render.DraftingModelRenderer.class, "registerPipelines");
        io.github.mcheliceport.shim.client.event.ClientBus.listen(io.github.mcheliceport.client.render.DraftingModelRenderer.class, "register");
        io.github.mcheliceport.shim.client.event.ClientBus.listen(io.github.mcheliceport.client.render.ToolItemModel.class, "register");
        io.github.mcheliceport.shim.client.event.ClientBus.addListener(AddClientReloadListenersEvent.class, (AddClientReloadListenersEvent event) -> {
            event.addListener(ResourceLocation.fromNamespaceAndPath(McheliCePort.MOD_ID, "aircraft_models"),
                    new AircraftModels.ReloadListener());
            event.addListener(ResourceLocation.fromNamespaceAndPath(McheliCePort.MOD_ID, "hud_textures"),
                    (net.minecraft.server.packs.resources.ResourceManagerReloadListener) manager ->
                            GuiHudCanvas.clearTextureSizes());
            event.addListener(ResourceLocation.fromNamespaceAndPath(McheliCePort.MOD_ID, "bullet_models"),
                    (net.minecraft.server.packs.resources.ResourceManagerReloadListener) manager ->
                            {
                                BulletModels.clear();
                                io.github.mcheliceport.client.model.PlaneExhaustTextures.clear();
                            });
        });
        AircraftClientEvents.register();
        io.github.mcheliceport.command.CommandClientHooks.setTitle(io.github.mcheliceport.client.hud.TitleOverlay::show);
        io.github.mcheliceport.command.CommandClientHooks.setRequest(CommandRequestClient::answer);
        CommandRequestClient.register();
        McheliEffects.setSink(ClientEffects.INSTANCE);
        WeaponClient.register();
        io.github.mcheliceport.client.effect.EffectsRangeClient.register();
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntityTypes.UAV_STATION.get(), io.github.mcheliceport.client.render.UavStationRenderer::new);
        event.registerEntityRenderer(ModEntityTypes.HELICOPTER.get(), HeliRenderer::new);
        event.registerEntityRenderer(ModEntityTypes.PLANE.get(), io.github.mcheliceport.client.render.PlaneRenderer::new);
        event.registerEntityRenderer(ModEntityTypes.SHIP.get(), io.github.mcheliceport.client.render.ShipRenderer::new);
        event.registerEntityRenderer(ModEntityTypes.TANK.get(), context -> new io.github.mcheliceport.client.render.GroundRenderer<>(context, "tanks"));
        event.registerEntityRenderer(ModEntityTypes.VEHICLE.get(), context -> new io.github.mcheliceport.client.render.GroundRenderer<>(context, "vehicles"));
        bullet(event, ModEntityTypes.BULLET.get(), BulletRenderer.Kind.BULLET);
        bullet(event, ModEntityTypes.ROCKET.get(), BulletRenderer.Kind.BULLET);
        bullet(event, ModEntityTypes.TORPEDO.get(), BulletRenderer.Kind.BULLET);
        bullet(event, ModEntityTypes.MARKER_ROCKET.get(), BulletRenderer.Kind.BULLET);
        bullet(event, ModEntityTypes.DISPENSED_ITEM.get(), BulletRenderer.Kind.BULLET);
        bullet(event, ModEntityTypes.AA_MISSILE.get(), BulletRenderer.Kind.AA_MISSILE);
        bullet(event, ModEntityTypes.AS_MISSILE.get(), BulletRenderer.Kind.AS_MISSILE);
        bullet(event, ModEntityTypes.TV_MISSILE.get(), BulletRenderer.Kind.TV_MISSILE);
        bullet(event, ModEntityTypes.AT_MISSILE.get(), BulletRenderer.Kind.TV_MISSILE);
        bullet(event, ModEntityTypes.BOMB.get(), BulletRenderer.Kind.BOMB);
        event.registerEntityRenderer(ModEntityTypes.A10.get(), A10Renderer::new);
        event.registerEntityRenderer(ModEntityTypes.FLARE.get(), FlareRenderer::flare);
        event.registerEntityRenderer(ModEntityTypes.CHAFF.get(), FlareRenderer::chaff);
        event.registerEntityRenderer(ModEntityTypes.THROWABLE.get(), ThrowableRenderer::new);
        event.registerEntityRenderer(ModEntityTypes.PARACHUTE.get(), io.github.mcheliceport.client.render.ParachuteRenderer::new);
        event.registerEntityRenderer(ModEntityTypes.CONTAINER.get(), CargoContainerRenderer::new);
        event.registerEntityRenderer(ModEntityTypes.CHAIN.get(), io.github.mcheliceport.client.render.ChainRenderer::new);
        event.registerEntityRenderer(ModEntityTypes.GLTD.get(), io.github.mcheliceport.client.render.GltdRenderer::new);
        event.registerEntityRenderer(ModEntityTypes.GUNNER.get(), io.github.mcheliceport.client.render.GunnerRenderer::new);
        event.registerBlockEntityRenderer(io.github.mcheliceport.registry.ModBlocks.DRAFTING_TABLE_ENTITY.get(),
                io.github.mcheliceport.client.render.DraftingTableRenderer::new);
        // Remaining vehicles and support entities draw nothing until their M6 implementation.
        Set<EntityType<?>> drawn = Set.of(ModEntityTypes.HELICOPTER.get(), ModEntityTypes.PLANE.get(), ModEntityTypes.SHIP.get(), ModEntityTypes.BULLET.get(),
                ModEntityTypes.ROCKET.get(), ModEntityTypes.TORPEDO.get(), ModEntityTypes.MARKER_ROCKET.get(),
                ModEntityTypes.DISPENSED_ITEM.get(), ModEntityTypes.AA_MISSILE.get(),
                ModEntityTypes.AS_MISSILE.get(), ModEntityTypes.TV_MISSILE.get(), ModEntityTypes.AT_MISSILE.get(),
                ModEntityTypes.BOMB.get(), ModEntityTypes.A10.get(), ModEntityTypes.FLARE.get(),
                ModEntityTypes.CHAFF.get(), ModEntityTypes.THROWABLE.get(), ModEntityTypes.PARACHUTE.get(),
                ModEntityTypes.TANK.get(), ModEntityTypes.VEHICLE.get(), ModEntityTypes.CONTAINER.get(), ModEntityTypes.CHAIN.get(), ModEntityTypes.UAV_STATION.get(),
                ModEntityTypes.GLTD.get(), ModEntityTypes.GUNNER.get());
        ModEntityTypes.all().stream()
                .filter(type -> !drawn.contains(type.get()))
                .forEach(type -> registerNoop(event, type.get()));
    }

    private static <T extends BaseBulletEntity> void bullet(EntityRenderersEvent.RegisterRenderers event,
            EntityType<T> type, BulletRenderer.Kind kind) {
        event.registerEntityRenderer(type, ctx -> new BulletRenderer<>(ctx, kind));
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.AIRCRAFT.get(), AircraftScreen::new);
        event.register(ModMenuTypes.UAV_STATION.get(), io.github.mcheliceport.client.gui.UavStationScreen::new);
        event.register(ModMenuTypes.DRAFTING_TABLE.get(), io.github.mcheliceport.client.gui.DraftingTableScreen::new);
    }

    private static <T extends Entity> void registerNoop(EntityRenderersEvent.RegisterRenderers event,
            EntityType<T> type) {
        event.registerEntityRenderer(type, NoopRenderer::new);
    }

    /** Upstream drew {@code mch_fuel} with water's textures, tinted brown. */
    private static void registerFluidModels(net.neoforged.neoforge.client.event.RegisterFluidModelsEvent event) {
        event.register(new net.minecraft.client.renderer.block.FluidModel.Unbaked(
                new net.minecraft.client.resources.model.sprite.Material(
                        net.minecraft.resources.ResourceLocation.withDefaultNamespace("block/water_still")),
                new net.minecraft.client.resources.model.sprite.Material(
                        net.minecraft.resources.ResourceLocation.withDefaultNamespace("block/water_flow")),
                null,
                (net.neoforged.neoforge.client.fluid.FluidTintSource) state -> ModFluids.FUEL_COLOR),
                ModFluids.FUEL, ModFluids.FLOWING_FUEL);
    }
}
