package io.github.mcheliceport.wingman.handler;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** Upstream's path: the projectile's chunk and the line ahead of it, longer for fast rounds. */
class ProjectileChunkLoaderTest {

    private static long[] line(int fromX, int toX, int z) {
        long[] chunks = new long[toX - fromX + 1];
        for (int x = fromX; x <= toX; x++) {
            chunks[x - fromX] = ChunkPos.pack(x, z);
        }
        return chunks;
    }

    @Test
    void stillOrVerticalProjectilesHoldTheirOwnChunk() {
        assertArrayEquals(new long[] {ChunkPos.pack(-1, 2)}, ProjectileChunkLoader.pathChunks(-0.5, 40.0, Vec3.ZERO));
        assertArrayEquals(new long[] {ChunkPos.pack(0, 0)},
                ProjectileChunkLoader.pathChunks(1.0, 1.0, new Vec3(0.0, -5.0, 0.0)));
    }

    @Test
    void theLineAheadGrowsWithSpeedUpToTwoChunks() {
        // 4 chunks plus 8 ticks at 1 block a tick: 72 blocks ahead.
        assertArrayEquals(line(0, 4, 0), ProjectileChunkLoader.pathChunks(0.5, 0.5, new Vec3(1.0, 0.0, 0.0)));
        // Fast rounds: 4 chunks plus at most 2 more, 96 blocks ahead.
        assertArrayEquals(line(0, 6, 0), ProjectileChunkLoader.pathChunks(0.5, 0.5, new Vec3(10.0, 3.0, 0.0)));
        long[] west = ProjectileChunkLoader.pathChunks(0.5, 0.5, new Vec3(-1.0, 0.0, 0.0));
        assertArrayEquals(new long[] {ChunkPos.pack(0, 0), ChunkPos.pack(-1, 0), ChunkPos.pack(-2, 0),
                ChunkPos.pack(-3, 0), ChunkPos.pack(-4, 0), ChunkPos.pack(-5, 0)}, west);
    }
}
