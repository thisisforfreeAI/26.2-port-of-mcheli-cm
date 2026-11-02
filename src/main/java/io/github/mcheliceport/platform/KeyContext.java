package io.github.mcheliceport.platform;

/**
 * When a key binding acts (NeoForge's {@code IKeyConflictContext}). On NeoForge two bindings on
 * the same key conflict only where their contexts overlap; vanilla, and so Fabric, sets every
 * binding on a pressed key, and the mod's handlers check {@link #isActive()} themselves.
 */
public interface KeyContext {
    boolean isActive();
}
