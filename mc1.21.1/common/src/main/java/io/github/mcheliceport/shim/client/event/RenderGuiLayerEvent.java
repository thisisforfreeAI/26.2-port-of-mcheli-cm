package io.github.mcheliceport.shim.client.event;

import net.minecraft.resources.ResourceLocation;

/** A vanilla HUD layer (see {@link VanillaGuiLayers}) is about to be drawn; cancelling skips it. */
public abstract class RenderGuiLayerEvent extends ClientEvent {
    private final ResourceLocation name;

    protected RenderGuiLayerEvent(ResourceLocation name) {
        this.name = name;
    }

    public ResourceLocation getName() {
        return name;
    }

    public static final class Pre extends RenderGuiLayerEvent {
        public Pre(ResourceLocation name) {
            super(name);
        }
    }
}
