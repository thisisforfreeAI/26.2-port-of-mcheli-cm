package io.github.mcheliceport.shim.client.event;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/**
 * Collects the port's HUD layers. They are drawn in registration order, all above vanilla's HUD;
 * the anchors are kept for NeoForge's ordering among the port's own layers.
 */
public final class RegisterGuiLayersEvent extends ClientEvent {
    public record Entry(ResourceLocation id, GuiLayer layer) {
    }

    private final List<Entry> below = new ArrayList<>();
    private final List<Entry> above = new ArrayList<>();

    public void registerBelow(ResourceLocation anchor, ResourceLocation id, GuiLayer layer) {
        below.add(new Entry(id, layer));
    }

    public void registerAbove(ResourceLocation anchor, ResourceLocation id, GuiLayer layer) {
        above.add(new Entry(id, layer));
    }

    public void registerAboveAll(ResourceLocation id, GuiLayer layer) {
        above.add(new Entry(id, layer));
    }

    /** Layers in drawing order: those registered below an anchor, then the rest. */
    public List<Entry> ordered() {
        List<Entry> all = new ArrayList<>(below);
        all.addAll(above);
        return all;
    }
}
