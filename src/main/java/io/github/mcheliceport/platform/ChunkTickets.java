package io.github.mcheliceport.platform;

import java.util.UUID;
import net.minecraft.server.level.ServerLevel;

/** Force-loaded chunks owned by entity UUIDs, dropped when the world loads again. See {@link Platform#chunkTickets}. */
public interface ChunkTickets {

    /**
     * Adds or removes the owner's ticket on a chunk. {@code ticking}: the chunk also ticks its
     * entities and blocks. Returns whether anything changed.
     */
    boolean forceChunk(ServerLevel level, UUID owner, int chunkX, int chunkZ, boolean add, boolean ticking);
}
