package io.github.mcheliceport.gametest;

import static io.github.mcheliceport.gametest.GameTestSupport.check;
import static io.github.mcheliceport.gametest.GameTestSupport.cleanUp;
import static io.github.mcheliceport.gametest.GameTestSupport.spawn;

import io.github.mcheliceport.config.ServerConfig;
import io.github.mcheliceport.entity.heli.HeliEntity;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.phys.Vec3;

/** Upstream's long-distance tracking: vehicles reach players past their view distance. */
final class LongDistanceGameTests {

    private LongDistanceGameTests() {
    }

    static void register() {
        ModGameTests.test("long_distance_tracking", 200, LongDistanceGameTests::tracking);
    }

    private static boolean seenBy(ServerLevel level, Entity entity, ServerPlayer player) {
        ChunkMap.TrackedEntity tracked = level.getChunkSource().chunkMap.entityMap.get(entity.getId());
        return tracked != null && tracked.seenBy.contains(player.connection);
    }

    private static boolean tracked(ServerLevel level, Entity entity) {
        return level.getChunkSource().chunkMap.entityMap.containsKey(entity.getId());
    }

    private static void update(ServerLevel level, Entity entity) {
        level.getChunkSource().chunkMap.entityMap.get(entity.getId()).updatePlayers(level.players());
    }

    private static void awaitTracked(GameTestHelper helper, ServerLevel level, Entity a, Entity b, Runnable then) {
        if (tracked(level, a) && tracked(level, b)) {
            then.run();
        } else {
            helper.runAfterDelay(1, () -> awaitTracked(helper, level, a, b, then));
        }
    }

    private static void tracking(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // A mock player asks for the smallest view distance, two chunks; the vehicle is ten away.
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 near = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
        player.snapTo(near.x, near.y, near.z);
        Vec3 far = near.add(160.0, 30.0, 0.0);
        int chunkX = SectionPos.blockToSectionCoord(far.x);
        int chunkZ = SectionPos.blockToSectionCoord(far.z);
        boolean wasForced = level.getForceLoadedChunks().contains(new net.minecraft.world.level.ChunkPos(chunkX, chunkZ).pack());
        level.setChunkForced(chunkX, chunkZ, true);
        HeliEntity heli = spawn(helper, "ah-64", new Vec3(2.5 + 160.0, 32.0, 2.5), 0.0F);
        Pig pig = EntityTypes.PIG.create(level, EntitySpawnReason.COMMAND);
        pig.snapTo(far.x + 2.0, far.y, far.z);
        level.addFreshEntity(pig);
        // The forced chunk loads over the next ticks; its entities are tracked once it ticks them.
        awaitTracked(helper, level, heli, pig, () -> {
            boolean setting = ServerConfig.LONG_DISTANCE_TRACKING.get();
            try {
                ServerConfig.LONG_DISTANCE_TRACKING.set(true);
                update(level, heli);
                update(level, pig);
                check(helper, seenBy(level, heli, player), "the vehicle is sent 160 blocks away, past the view distance");
                check(helper, !seenBy(level, pig, player), "a pig there is not");
                ServerConfig.LONG_DISTANCE_TRACKING.set(false);
                update(level, heli);
                check(helper, !seenBy(level, heli, player), "with the setting off, the view distance limits vehicles too");
            } finally {
                ServerConfig.LONG_DISTANCE_TRACKING.set(setting);
                pig.discard();
                cleanUp(heli);
                if (!wasForced) {
                    level.setChunkForced(chunkX, chunkZ, false);
                }
            }
            helper.succeed();
        });
    }
}
