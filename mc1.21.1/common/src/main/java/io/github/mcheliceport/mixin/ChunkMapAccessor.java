package io.github.mcheliceport.mixin;

import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Chunk sending internals that 26.2 made public, for the UAV remote view and /mcheli status. */
@Mixin(ChunkMap.class)
public interface ChunkMapAccessor {

    @Invoker("markChunkPendingToSend")
    void mcheli$markChunkPendingToSend(ServerPlayer player, ChunkPos pos);

    @Invoker("dropChunk")
    static void mcheli$dropChunk(ServerPlayer player, ChunkPos pos) {
        throw new AssertionError();
    }

    @Invoker("applyChunkTrackingView")
    void mcheli$applyChunkTrackingView(ServerPlayer player, ChunkTrackingView view);

    @Invoker("getChunks")
    Iterable<ChunkHolder> mcheli$getChunks();

    @Invoker("isChunkTracked")
    boolean mcheli$isChunkTracked(ServerPlayer player, int x, int z);
}
