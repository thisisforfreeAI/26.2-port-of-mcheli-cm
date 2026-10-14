package io.github.mcheliceport.client;

import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.config.ClientConfig;
import io.github.mcheliceport.client.gui.AircraftScreen;
import io.github.mcheliceport.client.input.ModKeyMappings;
import io.github.mcheliceport.client.render.StandInAircraftRenderer;
import io.github.mcheliceport.registry.ModEntityTypes;
import io.github.mcheliceport.registry.ModMenuTypes;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
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
        AircraftClientEvents.register();
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // Helicopters get the stand-in renderer until M4 (decision D11); the rest draw nothing yet.
        event.registerEntityRenderer(ModEntityTypes.HELICOPTER.get(), StandInAircraftRenderer::new);
        ModEntityTypes.all().stream()
                .filter(type -> type != ModEntityTypes.HELICOPTER)
                .forEach(type -> registerNoop(event, type.get()));
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.AIRCRAFT.get(), AircraftScreen::new);
    }

    private static <T extends Entity> void registerNoop(EntityRenderersEvent.RegisterRenderers event,
            EntityType<T> type) {
        event.registerEntityRenderer(type, NoopRenderer::new);
    }
}
