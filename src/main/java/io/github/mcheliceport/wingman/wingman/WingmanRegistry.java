package io.github.mcheliceport.wingman.wingman;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * Upstream's {@code WingmanRegistry}: the wingman aircraft's UUID to its entry, in assignment
 * order. As upstream it lives only while the server runs; the port also empties it when the
 * server stops, so a world reopened in the same game starts without wingmen.
 */
public final class WingmanRegistry {

    private static final Map<UUID, WingmanEntry> ENTRIES = new LinkedHashMap<>();

    private WingmanRegistry() {
    }

    public static synchronized void put(UUID id, WingmanEntry entry) {
        ENTRIES.put(id, entry);
    }

    public static synchronized void remove(UUID id) {
        ENTRIES.remove(id);
    }

    public static synchronized @Nullable WingmanEntry get(UUID id) {
        return ENTRIES.get(id);
    }

    public static synchronized void clear() {
        ENTRIES.clear();
    }

    public static synchronized Map<UUID, WingmanEntry> snapshot() {
        return new LinkedHashMap<>(ENTRIES);
    }

    /** The wingmen already following this leader. */
    public static synchronized int countForLeader(Entity leader) {
        int n = 0;
        for (WingmanEntry e : ENTRIES.values()) {
            if (e.leader == leader) {
                n++;
            }
        }
        return n;
    }

    public static synchronized void removeForLeader(Entity leader) {
        ENTRIES.values().removeIf(e -> e.leader == leader);
    }

    public static synchronized List<Map.Entry<UUID, WingmanEntry>> snapshotForLeader(Entity leader) {
        List<Map.Entry<UUID, WingmanEntry>> result = new ArrayList<>();
        for (Map.Entry<UUID, WingmanEntry> e : ENTRIES.entrySet()) {
            if (e.getValue().leader == leader) {
                result.add(Map.entry(e.getKey(), e.getValue()));
            }
        }
        return result;
    }
}
