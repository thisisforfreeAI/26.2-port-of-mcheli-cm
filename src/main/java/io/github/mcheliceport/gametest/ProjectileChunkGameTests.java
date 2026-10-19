package io.github.mcheliceport.gametest;

import static io.github.mcheliceport.gametest.GameTestSupport.check;

import io.github.mcheliceport.config.ServerConfig;
import io.github.mcheliceport.entity.weapon.RocketEntity;
import io.github.mcheliceport.registry.ModEntityTypes;
import io.github.mcheliceport.wingman.handler.ProjectileChunkLoader;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/** Upstream's {@code ProjectileChunkLoader}: leased chunks along a projectile's path. */
final class ProjectileChunkGameTests {

    private ProjectileChunkGameTests() {
    }

    static void register() {
        ModGameTests.testAlone("projectile_chunk_leases", 60, ProjectileChunkGameTests::leases);
    }

    private static RocketEntity rocket(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        RocketEntity rocket = ModEntityTypes.ROCKET.get().create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        Vec3 pos = h.absoluteVec(new Vec3(1.5, 6.0, 1.5));
        rocket.setPos(pos.x, pos.y, pos.z);
        rocket.setName("hydra70");
        rocket.setNoGravity(true);
        rocket.setDeltaMovement(1.0, 0.0, 0.0);
        level.addFreshEntity(rocket);
        return rocket;
    }

    /**
     * With {@code projectiles.chunkLoad} off a stock rocket (no {@code CanLoadChunks}) leases
     * nothing; with it on, the rocket leases its chunk and the line ahead, and the chunks are
     * released once their lease runs out after the rocket is gone.
     */
    private static void leases(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        boolean chunkLoad = ServerConfig.PROJECTILE_CHUNK_LOAD.get();
        int leaseTicks = ServerConfig.PROJECTILE_CHUNK_LEASE_TICKS.get();
        ServerConfig.PROJECTILE_CHUNK_LOAD.set(false);
        ServerConfig.PROJECTILE_CHUNK_LEASE_TICKS.set(5);
        Runnable restore = () -> {
            ServerConfig.PROJECTILE_CHUNK_LOAD.set(chunkLoad);
            ServerConfig.PROJECTILE_CHUNK_LEASE_TICKS.set(leaseTicks);
        };
        // The rockets are ticked here: in a batch whose chunks do not tick entities yet they would not move.
        try {
            RocketEntity off = rocket(h);
            off.tick();
            off.tick();
            check(h, ProjectileChunkLoader.leased(level) == 0, "a rocket leased chunks with chunkLoad off");
            off.discard();
            ServerConfig.PROJECTILE_CHUNK_LOAD.set(true);
            RocketEntity on = rocket(h);
            Vec3 start = on.position();
            on.tick();
            ChunkPos own = ChunkPos.containing(net.minecraft.core.BlockPos.containing(start));
            ChunkPos ahead = new ChunkPos(own.x() + 4, own.z());
            check(h, ProjectileChunkLoader.isLeased(level, own) && ProjectileChunkLoader.isLeased(level, ahead)
                    && !ProjectileChunkLoader.isLeased(level, new ChunkPos(own.x() - 1, own.z())),
                    "the rocket did not lease its chunk and the line ahead");
            on.discard();
        } catch (RuntimeException e) {
            restore.run();
            throw e;
        }
        h.runAfterDelay(8, () -> {
            restore.run();
            check(h, ProjectileChunkLoader.leased(level) == 0, "the leases did not run out");
            h.succeed();
        });
    }
}
