package io.github.mcheliceport.shim.client.event;

import io.github.mcheliceport.client.McheliCePortClient;
import java.util.function.Supplier;

/**
 * Posts each of the port's client registration events once, after the shared client set-up, and
 * keeps what the listeners registered for the loader module to hand to its own registries.
 */
public final class ClientRegistrations {
    private ClientRegistrations() {}

    private static <E extends ClientEvent> Supplier<E> once(Supplier<E> factory) {
        return new Supplier<>() {
            private E event;

            @Override
            public synchronized E get() {
                if (event == null) {
                    McheliCePortClient.init();
                    event = ClientBus.post(factory.get());
                }
                return event;
            }
        };
    }

    private static final Supplier<EntityRenderersEvent.RegisterRenderers> RENDERERS =
            once(EntityRenderersEvent.RegisterRenderers::new);
    private static final Supplier<EntityRenderersEvent.RegisterLayerDefinitions> LAYERS =
            once(EntityRenderersEvent.RegisterLayerDefinitions::new);
    private static final Supplier<RegisterMenuScreensEvent> SCREENS = once(RegisterMenuScreensEvent::new);
    private static final Supplier<RegisterKeyMappingsEvent> KEYS = once(RegisterKeyMappingsEvent::new);
    private static final Supplier<RegisterGuiLayersEvent> GUI_LAYERS = once(RegisterGuiLayersEvent::new);
    private static final Supplier<AddClientReloadListenersEvent> RELOAD = once(AddClientReloadListenersEvent::new);

    public static EntityRenderersEvent.RegisterRenderers renderers() {
        return RENDERERS.get();
    }

    public static EntityRenderersEvent.RegisterLayerDefinitions layers() {
        return LAYERS.get();
    }

    public static RegisterMenuScreensEvent screens() {
        return SCREENS.get();
    }

    public static RegisterKeyMappingsEvent keyMappings() {
        return KEYS.get();
    }

    public static RegisterGuiLayersEvent guiLayers() {
        return GUI_LAYERS.get();
    }

    public static AddClientReloadListenersEvent reloadListeners() {
        return RELOAD.get();
    }
}
