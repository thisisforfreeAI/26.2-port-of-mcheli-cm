package io.github.mcheliceport.shim.client.event;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.KeyMapping;

/** Collects the port's key mappings for the loader to register. */
public final class RegisterKeyMappingsEvent extends ClientEvent {
    private final List<KeyMapping> mappings = new ArrayList<>();

    public void register(KeyMapping mapping) {
        mappings.add(mapping);
    }

    /** 26.2 registers categories as objects; 1.21.1 categories are plain translation keys. */
    public void registerCategory(Object category) {
    }

    public List<KeyMapping> mappings() {
        return mappings;
    }
}
