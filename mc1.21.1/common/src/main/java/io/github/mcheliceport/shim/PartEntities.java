package io.github.mcheliceport.shim;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * The parts of the {@link MultipartEntity multipart entities} that are in a level, by id: vanilla's
 * {@code ServerLevel.dragonParts}, kept for the port's parts by {@code mixin.EntityCallbacksMixin}
 * on both sides. Entity ids are unique across a side's levels, so one map per side is enough.
 */
public final class PartEntities {

    private static final Int2ObjectMap<PartEntity<?>> SERVER = new Int2ObjectOpenHashMap<>();
    private static final Int2ObjectMap<PartEntity<?>> CLIENT = new Int2ObjectOpenHashMap<>();

    private PartEntities() {
    }

    private static Int2ObjectMap<PartEntity<?>> map(Level level) {
        return level.isClientSide() ? CLIENT : SERVER;
    }

    public static void onTrackingStart(Entity entity) {
        if (entity instanceof MultipartEntity multipart && multipart.hasParts()) {
            Int2ObjectMap<PartEntity<?>> map = map(entity.level());
            for (PartEntity<?> part : multipart.parts()) {
                map.put(part.getId(), part);
            }
        }
    }

    public static void onTrackingEnd(Entity entity) {
        if (entity instanceof MultipartEntity multipart && multipart.hasParts()) {
            Int2ObjectMap<PartEntity<?>> map = map(entity.level());
            for (PartEntity<?> part : multipart.parts()) {
                map.remove(part.getId(), part);
            }
        }
    }

    public static @Nullable Entity get(Level level, int id) {
        PartEntity<?> part = map(level).get(id);
        return part != null && part.level() == level ? part : null;
    }

    /** Adds the level's parts in {@code area} that pass {@code filter}, as NeoForge's {@code Level#getEntities} does. */
    public static void addParts(Level level, List<Entity> found, @Nullable Entity except, AABB area,
            Predicate<? super Entity> filter) {
        Int2ObjectMap<PartEntity<?>> map = map(level);
        if (map.isEmpty()) {
            return;
        }
        for (PartEntity<?> part : map.values()) {
            if (part != except && part.level() == level && part.getBoundingBox().intersects(area) && filter.test(part)) {
                found.add(part);
            }
        }
    }

    /** The typed variant, which stops at {@code limit} entries. */
    public static <T extends Entity> void addParts(Level level, EntityTypeTest<Entity, T> test, AABB area,
            Predicate<? super T> filter, List<? super T> found, int limit) {
        Int2ObjectMap<PartEntity<?>> map = map(level);
        if (map.isEmpty()) {
            return;
        }
        for (PartEntity<?> part : map.values()) {
            if (found.size() >= limit) {
                return;
            }
            T t = test.tryCast(part);
            if (t != null && part.level() == level && t.getBoundingBox().intersects(area) && filter.test(t)) {
                found.add(t);
            }
        }
    }

    public static void clear(boolean client) {
        (client ? CLIENT : SERVER).clear();
    }
}
