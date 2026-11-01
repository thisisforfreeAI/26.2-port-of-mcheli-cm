package io.github.mcheliceport.platform.fabric;

import io.github.mcheliceport.platform.loader.PartEntityBase;
import io.github.mcheliceport.platform.loader.SpawnDataEntity;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The multipart entities of each level, so the level mixins can include their parts in entity
 * lookups as NeoForge does. Unlike NeoForge's part map it reads each entity's parts when asked,
 * so parts replaced after a type change are seen at once.
 */
public final class FabricParts {
    private static final Map<Level, Set<SpawnDataEntity>> OWNERS = new WeakHashMap<>();

    private FabricParts() {
    }

    static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> add(entity));
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> remove(entity));
    }

    static synchronized void add(Entity entity) {
        if (entity instanceof SpawnDataEntity owner) {
            OWNERS.computeIfAbsent(entity.level(), level -> Collections.newSetFromMap(new java.util.IdentityHashMap<>()))
                    .add(owner);
        }
    }

    static synchronized void remove(Entity entity) {
        if (entity instanceof SpawnDataEntity owner) {
            Set<SpawnDataEntity> owners = OWNERS.get(entity.level());
            if (owners != null) {
                owners.remove(owner);
            }
        }
    }

    /** Calls {@code action} for every part in {@code level}. */
    public static synchronized void forEachPart(Level level, Consumer<PartEntityBase<?>> action) {
        Set<SpawnDataEntity> owners = OWNERS.get(level);
        if (owners == null || owners.isEmpty()) {
            return;
        }
        for (SpawnDataEntity owner : owners.toArray(SpawnDataEntity[]::new)) {
            for (PartEntityBase<?> part : owner.partEntities()) {
                action.accept(part);
            }
        }
    }

    /** The part with this entity id in {@code level}, if any. */
    public static synchronized @Nullable PartEntityBase<?> find(Level level, int id) {
        Set<SpawnDataEntity> owners = OWNERS.get(level);
        if (owners == null) {
            return null;
        }
        for (SpawnDataEntity owner : owners) {
            for (PartEntityBase<?> part : owner.partEntities()) {
                if (part.getId() == id) {
                    return part;
                }
            }
        }
        return null;
    }
}
