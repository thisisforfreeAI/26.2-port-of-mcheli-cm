package io.github.mcheliceport.shim.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.KeyMapping;
import org.jspecify.annotations.Nullable;

/**
 * NeoForge's key conflict context, for the port's own mappings: a mod key is live only while its
 * context is active, and it shares its key with vanilla's in-game controls without conflict.
 * NeoForge hands it to its own {@code IKeyConflictContext}; Fabric's input mixin consults it.
 */
@FunctionalInterface
public interface KeyContext {
    boolean isActive();

    Map<KeyMapping, KeyContext> CONTEXTS = new ConcurrentHashMap<>();

    static KeyMapping bind(KeyMapping mapping, KeyContext context) {
        CONTEXTS.put(mapping, context);
        return mapping;
    }

    static @Nullable KeyContext of(KeyMapping mapping) {
        return CONTEXTS.get(mapping);
    }
}
