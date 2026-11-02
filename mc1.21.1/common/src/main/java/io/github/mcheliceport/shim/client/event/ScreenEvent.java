package io.github.mcheliceport.shim.client.event;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;

/** NeoForge's screen events. */
public abstract class ScreenEvent extends ClientEvent {
    private final Screen screen;

    protected ScreenEvent(Screen screen) {
        this.screen = screen;
    }

    public Screen getScreen() {
        return screen;
    }

    public abstract static class Init extends ScreenEvent {
        private final List<GuiEventListener> listeners;
        private final Consumer<GuiEventListener> add;

        protected Init(Screen screen, List<GuiEventListener> listeners, Consumer<GuiEventListener> add) {
            super(screen);
            this.listeners = listeners;
            this.add = add;
        }

        public List<GuiEventListener> getListenersList() {
            return listeners;
        }

        public void addListener(GuiEventListener listener) {
            add.accept(listener);
        }

        public static final class Post extends Init {
            public Post(Screen screen, List<GuiEventListener> listeners, Consumer<GuiEventListener> add) {
                super(screen, listeners, add);
            }
        }
    }
}
