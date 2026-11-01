package io.github.mcheliceport.shim.client.event;

import net.minecraft.client.DeltaTracker;

/** NeoForge's frame events, around each rendered frame. */
public abstract class RenderFrameEvent extends ClientEvent {
    private final DeltaTracker delta;

    protected RenderFrameEvent(DeltaTracker delta) {
        this.delta = delta;
    }

    public DeltaTracker getPartialTick() {
        return delta;
    }

    public static final class Pre extends RenderFrameEvent {
        public Pre(DeltaTracker delta) {
            super(delta);
        }
    }

    public static final class Post extends RenderFrameEvent {
        public Post(DeltaTracker delta) {
            super(delta);
        }
    }
}
