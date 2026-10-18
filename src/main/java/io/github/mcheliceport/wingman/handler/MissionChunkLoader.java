package io.github.mcheliceport.wingman.handler;

import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.wingman.wingman.WingmanEntry;
import io.github.mcheliceport.wingman.wingman.WingmanRegistry;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import org.jspecify.annotations.Nullable;

/**
 * Upstream's {@code ChunkLoadHandler}, for aircraft on a mission: keeps the 5 by 5 chunks around
 * each one loaded and ticking, so a mission flies on away from players. When the aircraft is not
 * found the chunks at its last position stay loaded, as upstream; they are released when its order
 * ends. Upstream loaded chunks only with its {@code forceChunkload} option, off by default, and then
 * around every aircraft; the port always loads them, and only for missions (KNOWN_ISSUES 98).
 * Forced chunks are dropped when the world loads again, since the missions are not saved.
 */
public final class MissionChunkLoader {

    /** Upstream's radius: 5 by 5 chunks. */
    private static final int CHUNK_RADIUS = 2;

    public static final TicketController TICKETS = new TicketController(
            Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "wingman_mission"),
            (level, helper) -> helper.getEntityTickets().keySet().forEach(helper::removeAllTickets));

    private record Loaded(ResourceKey<Level> dimension, ChunkPos center) {
    }

    private static final Map<UUID, Loaded> LOADED = new HashMap<>();

    private MissionChunkLoader() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener((RegisterTicketControllersEvent e) -> e.register(TICKETS));
    }

    /** Before the aircraft move, as upstream: loads the chunks around each mission aircraft in the level. */
    public static void tick(ServerLevel level) {
        for (Map.Entry<UUID, WingmanEntry> e : WingmanRegistry.snapshot().entrySet()) {
            if (!e.getValue().isAutonomous()) {
                continue;
            }
            var entity = level.getEntity(e.getKey());
            if (entity == null || entity.isRemoved()) {
                continue;
            }
            ChunkPos center = entity.chunkPosition();
            Loaded last = LOADED.get(e.getKey());
            if (last != null && last.dimension() == level.dimension() && last.center().equals(center)) {
                continue;
            }
            // Load the new area before releasing the old one, so the aircraft keeps ticking.
            force(level, e.getKey(), center, true);
            if (last != null && last.dimension() == level.dimension()) {
                release(level, e.getKey(), last.center(), center);
            }
            LOADED.put(e.getKey(), new Loaded(level.dimension(), center));
        }
        Iterator<Map.Entry<UUID, Loaded>> it = LOADED.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Loaded> e = it.next();
            if (e.getValue().dimension() != level.dimension()) {
                continue;
            }
            WingmanEntry entry = WingmanRegistry.get(e.getKey());
            var entity = level.getEntity(e.getKey());
            if (entry == null || !entry.isAutonomous() || entity != null && entity.isRemoved()) {
                force(level, e.getKey(), e.getValue().center(), false);
                it.remove();
            }
        }
    }

    /** The chunks loaded for the aircraft, or null. */
    public static @Nullable ChunkPos loadedCenter(UUID id) {
        Loaded loaded = LOADED.get(id);
        return loaded != null ? loaded.center() : null;
    }

    private static void force(ServerLevel level, UUID owner, ChunkPos center, boolean add) {
        for (int dx = -CHUNK_RADIUS; dx <= CHUNK_RADIUS; dx++) {
            for (int dz = -CHUNK_RADIUS; dz <= CHUNK_RADIUS; dz++) {
                TICKETS.forceChunk(level, owner, center.x() + dx, center.z() + dz, add, true);
            }
        }
    }

    /** Releases the chunks around the old centre that are not around the new one. */
    private static void release(ServerLevel level, UUID owner, ChunkPos old, ChunkPos now) {
        for (int dx = -CHUNK_RADIUS; dx <= CHUNK_RADIUS; dx++) {
            for (int dz = -CHUNK_RADIUS; dz <= CHUNK_RADIUS; dz++) {
                int x = old.x() + dx;
                int z = old.z() + dz;
                if (Math.abs(x - now.x()) > CHUNK_RADIUS || Math.abs(z - now.z()) > CHUNK_RADIUS) {
                    TICKETS.forceChunk(level, owner, x, z, false, true);
                }
            }
        }
    }

    public static void clear() {
        LOADED.clear();
    }
}
