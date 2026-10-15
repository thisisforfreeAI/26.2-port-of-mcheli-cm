package io.github.mcheliceport.client;

import io.github.mcheliceport.McheliCePort;
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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** Client-only entry point. FML never loads this class on a dedicated server. */
@Mod(value = McheliCePort.MOD_ID, dist = Dist.CLIENT)
public final class McheliCePortClient {
    public McheliCePortClient(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        // Adds a Config button for the mod on the Mods screen.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modBus.addListener(McheliCePortClient::registerRenderers);
        modBus.addListener(ModKeyMappings::register);
        modBus.addListener(McheliCePortClient::registerScreens);
        modBus.addListener(RiderTilt::registerModifiers);
        modBus.addListener(AircraftHudLayer::register);
        modBus.addListener(AircraftHudLayer::registerPipelines);
        modBus.addListener((AddClientReloadListenersEvent event) -> {
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
        AircraftClientEvents.register();
        McheliEffects.setSink(ClientEffects.INSTANCE);
        WeaponClient.register();
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntityTypes.HELICOPTER.get(), HeliRenderer::new);
        event.registerEntityRenderer(ModEntityTypes.PLANE.get(), io.github.mcheliceport.client.render.PlaneRenderer::new);
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
        // Remaining vehicles and support entities draw nothing until their M6 implementation.
        Set<EntityType<?>> drawn = Set.of(ModEntityTypes.HELICOPTER.get(), ModEntityTypes.PLANE.get(), ModEntityTypes.BULLET.get(),
                ModEntityTypes.ROCKET.get(), ModEntityTypes.TORPEDO.get(), ModEntityTypes.MARKER_ROCKET.get(),
                ModEntityTypes.DISPENSED_ITEM.get(), ModEntityTypes.AA_MISSILE.get(),
                ModEntityTypes.AS_MISSILE.get(), ModEntityTypes.TV_MISSILE.get(), ModEntityTypes.AT_MISSILE.get(),
                ModEntityTypes.BOMB.get(), ModEntityTypes.A10.get(), ModEntityTypes.FLARE.get(),
                ModEntityTypes.CHAFF.get(), ModEntityTypes.THROWABLE.get(), ModEntityTypes.PARACHUTE.get());
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
    }

    private static <T extends Entity> void registerNoop(EntityRenderersEvent.RegisterRenderers event,
            EntityType<T> type) {
        event.registerEntityRenderer(type, NoopRenderer::new);
    }
}
