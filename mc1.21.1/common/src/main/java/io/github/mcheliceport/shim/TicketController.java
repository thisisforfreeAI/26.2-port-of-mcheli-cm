package io.github.mcheliceport.shim;

import java.util.Comparator;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

/**
 * Loader-neutral stand-in for NeoForge's {@code TicketController}, on vanilla region tickets.
 * Vanilla tickets are not saved, which is what the port's controllers want anyway (they all drop
 * their tickets when a world loads).
 */
public final class TicketController {
    /** Ticket level 31 at the centre: blocks and entities there tick, as with a forced chunk. */
    private static final int RADIUS = 2;

    private final ResourceLocation id;
    private final TicketType<UUID> type;

    public TicketController(ResourceLocation id) {
        this.id = id;
        this.type = TicketType.create(id.toString(), Comparator.<UUID>naturalOrder());
    }

    public ResourceLocation id() {
        return id;
    }

    /** Adds or removes {@code owner}'s ticket on chunk {@code (x, z)}. {@code ticking} is accepted for NeoForge parity. */
    public boolean forceChunk(ServerLevel level, UUID owner, int x, int z, boolean add, boolean ticking) {
        ChunkPos pos = new ChunkPos(x, z);
        if (add) {
            level.getChunkSource().addRegionTicket(type, pos, RADIUS, owner);
        } else {
            level.getChunkSource().removeRegionTicket(type, pos, RADIUS, owner);
        }
        return true;
    }
}
