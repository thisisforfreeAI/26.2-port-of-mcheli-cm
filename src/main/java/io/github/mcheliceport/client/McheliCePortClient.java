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
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import io.github.mcheliceport.platform.ClientEvents;
import io.github.mcheliceport.platform.ClientRegistrars;
import io.github.mcheliceport.client.render.CargoContainerRenderer;

/**
 * The client's setup, for both loaders' client entry points (NeoForge: {@code NeoForgeClientMod},
 * Fabric: {@code FabricClientMod}), which register {@link ClientConfig} and then call {@link #init()}.
 */
public final class McheliCePortClient {

    private McheliCePortClient() {
    }

    public static void init() {
        ClientEvents.REGISTER_RENDERERS.add(McheliCePortClient::registerRenderers);
        ClientEvents.REGISTER_FLUID_MODELS.add(McheliCePortClient::registerFluidModels);
        ClientEvents.REGISTER_KEY_MAPPINGS.add(ModKeyMappings::register);
        ClientEvents.REGISTER_MENU_SCREENS.add(McheliCePortClient::registerScreens);
        // ThermalVision's render hook goes before RiderTilt's, so tilting and hiding act once, on its resubmit.
        io.github.mcheliceport.client.render.ThermalVision.register();
        RiderTilt.registerRenderHooks();
        RiderTilt.registerModifiers();
        io.github.mcheliceport.client.render.SearchLightRenderer.registerModifiers();
        io.github.mcheliceport.client.render.SceneLights.registerModifiers();
        ClientEvents.REGISTER_PIPELINES.add(io.github.mcheliceport.client.render.FarVehicles::registerPipelines);
        io.github.mcheliceport.client.render.baked.BakedVehicles.register();
        ClientEvents.REGISTER_PIPELINES.add(io.github.mcheliceport.client.render.SearchLightRenderer::registerPipelines);
        ClientEvents.REGISTER_LAYER_DEFINITIONS.add(io.github.mcheliceport.client.render.GunnerRenderer::registerLayer);
        ClientEvents.REGISTER_PIPELINES.add(AircraftHudLayer::registerPipelines);
        ClientEvents.REGISTER_PIPELINES.add(io.github.mcheliceport.client.particle.ParticleLayers::registerPipelines);
        ClientEvents.REGISTER_PARTICLE_GROUPS.add(io.github.mcheliceport.client.particle.BlastDustWedges::register);
        ClientEvents.REGISTER_PARTICLE_GROUPS.add(io.github.mcheliceport.client.particle.AirflowFireParticle::register);
        ClientEvents.REGISTER_PARTICLE_GROUPS.add(io.github.mcheliceport.client.particle.CondensationParticle::register);
        ClientEvents.REGISTER_PARTICLE_GROUPS.add(io.github.mcheliceport.client.particle.SupersonicConeParticle::register);
        ClientEvents.REGISTER_PIPELINES.add(io.github.mcheliceport.client.render.EngineHeat::registerPipelines);
        ClientEvents.REGISTER_PIPELINES.add(io.github.mcheliceport.client.render.GlowGeometry::registerPipelines);
        ClientEvents.REGISTER_PIPELINES.add(io.github.mcheliceport.client.render.DraftingModelRenderer::registerPipelines);
        ClientEvents.REGISTER_PICTURE_IN_PICTURE.add(io.github.mcheliceport.client.render.DraftingModelRenderer::register);
        ClientEvents.REGISTER_ITEM_MODELS.add(io.github.mcheliceport.client.render.ToolItemModel::register);
        ClientEvents.ADD_RELOAD_LISTENERS.add(event -> {
            event.addListener(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "aircraft_models"),
                    new AircraftModels.ReloadListener());
            event.addListener(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "hud_textures"),
                    (net.minecraft.server.packs.resources.ResourceManagerReloadListener) manager ->
                            GuiHudCanvas.clearTextureSizes());
            event.addListener(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "bullet_models"),
                    (net.minecraft.server.packs.resources.ResourceManagerReloadListener) manager ->
                            {
                                BulletModels.clear();
                                io.github.mcheliceport.client.model.PlaneExhaustTextures.clear();
                            });
        });
        ClientSetup.registerEvents();
        AircraftClientEvents.register();
        io.github.mcheliceport.command.CommandClientHooks.setTitle(io.github.mcheliceport.client.hud.TitleOverlay::show);
        io.github.mcheliceport.command.CommandClientHooks.setRequest(CommandRequestClient::answer);
        CommandRequestClient.register();
        McheliEffects.setSink(ClientEffects.INSTANCE);
        WeaponClient.register();
        io.github.mcheliceport.client.effect.EffectsRangeClient.register();
    }

    private static void registerRenderers(ClientRegistrars.Renderers event) {
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

    private static <T extends BaseBulletEntity> void bullet(ClientRegistrars.Renderers event,
            EntityType<T> type, BulletRenderer.Kind kind) {
        event.registerEntityRenderer(type, ctx -> new BulletRenderer<>(ctx, kind));
    }

    private static void registerScreens(ClientRegistrars.MenuScreenRegistrar event) {
        event.register(ModMenuTypes.AIRCRAFT.get(), AircraftScreen::new);
        event.register(ModMenuTypes.UAV_STATION.get(), io.github.mcheliceport.client.gui.UavStationScreen::new);
        event.register(ModMenuTypes.DRAFTING_TABLE.get(), io.github.mcheliceport.client.gui.DraftingTableScreen::new);
    }

    private static <T extends Entity> void registerNoop(ClientRegistrars.Renderers event,
            EntityType<T> type) {
        event.registerEntityRenderer(type, NoopRenderer::new);
    }

    /** Upstream drew {@code mch_fuel} with water's textures, tinted brown. */
    private static void registerFluidModels(ClientRegistrars.FluidModels event) {
        event.register(Identifier.withDefaultNamespace("block/water_still"),
                Identifier.withDefaultNamespace("block/water_flow"), ModFluids.FUEL_COLOR,
                ModFluids.FUEL.get(), ModFluids.FLOWING_FUEL.get());
    }
}
