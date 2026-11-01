package io.github.mcheliceport.platform.fabric;

import io.github.mcheliceport.platform.ChunkTickets;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

/**
 * NeoForge's owner-tracked {@code TicketController} on vanilla tickets. Vanilla holds one ticket
 * per type and chunk, so the owners are counted here: the ticket goes when its last owner does.
 * The ticket types do not persist, so a world loads again without them, as on NeoForge.
 */
final class FabricChunkTickets implements ChunkTickets {
    /** Entity-ticking radius: the ticket's chunk is at entity-ticking level, its neighbours load. */
    private static final int TICKING_RADIUS = 2;

    private final TicketType loading;
    private final TicketType ticking;
    private final Map<ServerLevel, Long2ObjectOpenHashMap<Set<UUID>>> loadingOwners = new WeakHashMap<>();
    private final Map<ServerLevel, Long2ObjectOpenHashMap<Set<UUID>>> tickingOwners = new WeakHashMap<>();

    FabricChunkTickets(Identifier id) {
        loading = Registry.register(BuiltInRegistries.TICKET_TYPE, id,
                new TicketType(TicketType.NO_TIMEOUT, TicketType.FLAG_LOADING | TicketType.FLAG_KEEP_DIMENSION_ACTIVE));
        ticking = Registry.register(BuiltInRegistries.TICKET_TYPE, id.withSuffix("_ticking"),
                new TicketType(TicketType.NO_TIMEOUT, TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION
                        | TicketType.FLAG_KEEP_DIMENSION_ACTIVE));
    }

    @Override
    public synchronized boolean forceChunk(ServerLevel level, UUID owner, int chunkX, int chunkZ, boolean add,
            boolean tick) {
        var owners = (tick ? tickingOwners : loadingOwners).computeIfAbsent(level, k -> new Long2ObjectOpenHashMap<>());
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        long key = pos.pack();
        TicketType type = tick ? ticking : loading;
        int radius = tick ? TICKING_RADIUS : 0;
        if (add) {
            Set<UUID> set = owners.computeIfAbsent(key, k -> new HashSet<>());
            if (!set.add(owner)) {
                return false;
            }
            if (set.size() == 1) {
                level.getChunkSource().addTicketWithRadius(type, pos, radius);
            }
            return true;
        }
        Set<UUID> set = owners.get(key);
        if (set == null || !set.remove(owner)) {
            return false;
        }
        if (set.isEmpty()) {
            owners.remove(key);
            level.getChunkSource().removeTicketWithRadius(type, pos, radius);
        }
        return true;
    }
}
