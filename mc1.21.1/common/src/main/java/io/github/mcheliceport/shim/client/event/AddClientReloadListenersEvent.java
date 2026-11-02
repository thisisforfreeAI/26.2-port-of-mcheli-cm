package io.github.mcheliceport.shim.client.event;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;

/** Collects the port's client resource reload listeners. */
public final class AddClientReloadListenersEvent extends ClientEvent {
    public record Entry(ResourceLocation id, PreparableReloadListener listener) {
    }

    private final List<Entry> listeners = new ArrayList<>();

    public void addListener(ResourceLocation id, PreparableReloadListener listener) {
        listeners.add(new Entry(id, listener));
    }

    public List<Entry> listeners() {
        return listeners;
    }
}
