package io.github.mcheliceport.entity.aircraft;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Whether the terrain a vehicle is about to touch is already loaded, asked without loading it.
 *
 * <p>A fast aircraft can reach chunks that the server has scheduled but not finished generating.
 * Vanilla's (and Lithium's) block collision then waits on the main thread for that generation, and
 * with slow world generation (C2ME, Distant Horizons generating on the server, heavy worldgen mods)
 * that wait can pass the 60 second watchdog and stop the server. Only {@code getChunkNow} answers
 * without waiting: {@code hasChunk} is true for a chunk that is still generating.
 */
public final class LoadedTerrain {

    /** More chunks than this across one move is never a real flight step; treat it as not loaded. */
    private static final int MAX_CHUNKS_PER_AXIS = 16;

    private LoadedTerrain() {
    }

    /**
     * Whether every chunk under {@code box}, grown by a block for the collision margin, is fully
     * loaded on this server. Always true on the client and off the server thread's level.
     */
    public static boolean isReady(Level level, AABB box) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return true;
        }
        int minX = Mth.floor(box.minX - 1.0) >> 4;
        int minZ = Mth.floor(box.minZ - 1.0) >> 4;
        int maxX = Mth.floor(box.maxX + 1.0) >> 4;
        int maxZ = Mth.floor(box.maxZ + 1.0) >> 4;
        if (maxX - minX >= MAX_CHUNKS_PER_AXIS || maxZ - minZ >= MAX_CHUNKS_PER_AXIS) {
            return false;
        }
        var chunks = serverLevel.getChunkSource();
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                if (chunks.getChunkNow(cx, cz) == null) {
                    return false;
                }
            }
        }
        return true;
    }
}
