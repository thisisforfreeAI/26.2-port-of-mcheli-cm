package io.github.mcheliceport.gametest;

import static io.github.mcheliceport.gametest.GameTestSupport.AH64;
import static io.github.mcheliceport.gametest.GameTestSupport.check;

import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.net.EntityInfoSyncPayload;
import io.github.mcheliceport.net.ModNetwork;
import io.github.mcheliceport.radar.EntityInfo;
import io.github.mcheliceport.radar.EntityInfoFeed;
import io.github.mcheliceport.weapon.WeaponSet;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** Upstream's entity-info feed ({@code EntityInfoManagerAsync}) and BVR lock ({@code PacketLockTargetBVR}). */
final class RadarFeedGameTests {

    private RadarFeedGameTests() {
    }

    static void register() {
        ModGameTests.test("entity_info_feed_tracks_as_upstream", 20, RadarFeedGameTests::tracks);
        ModGameTests.test("bvr_lock_sets_the_seeker_target", 20, RadarFeedGameTests::bvrLock);
    }

    /**
     * Players and vehicles are tracked; a helicopter only 30 blocks above the ground and moving, and a
     * husk never.
     * A snapshot survives the payload's codec.
     */
    private static void tracks(GameTestHelper h) {
        HeliEntity heli = GameTestSupport.spawn(h, AH64, new Vec3(8.5, 1, 8.5), 0.0F);
        Player player = GameTestSupport.player(h, GameType.SURVIVAL, new Vec3(10.5, 1, 8.5));
        Husk husk = EntityTypes.HUSK.create(h.getLevel(), EntitySpawnReason.COMMAND);
        Vec3 at = h.absoluteVec(new Vec3(4.5, 1, 4.5));
        husk.setNoAi(true);
        husk.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
        h.getLevel().addFreshEntity(husk);
        h.runAfterDelay(2, () -> {
            check(h, EntityInfoFeed.shouldTrack(h.getLevel(), player), "a player is not tracked");
            check(h, !EntityInfoFeed.shouldTrack(h.getLevel(), husk), "a husk is tracked");
            check(h, !EntityInfoFeed.shouldTrack(h.getLevel(), heli), "a helicopter on the ground is tracked");
            heli.setNoGravity(true);
            // Measured from the highest block in its column, as upstream.
            int ground = h.getLevel().getHeight(Heightmap.Types.MOTION_BLOCKING, heli.getBlockX(), heli.getBlockZ());
            heli.setPos(heli.getX(), ground + 31.0, heli.getZ());
            heli.setDeltaMovement(0.0, 0.0, 0.3);
            check(h, !EntityInfoFeed.shouldTrack(h.getLevel(), heli), "a slow helicopter is tracked");
            heli.setDeltaMovement(0.0, 0.0, 0.6);
            check(h, EntityInfoFeed.shouldTrack(h.getLevel(), heli), "a fast helicopter high up is not tracked");

            EntityInfo info = EntityInfo.of(heli);
            check(h, info.kind() == EntityInfo.Kind.HELI && info.name().equals(AH64), "the helicopter's info: " + info);
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            EntityInfoSyncPayload.CODEC.encode(buf, new EntityInfoSyncPayload(List.of(info, EntityInfo.of(player)), 7L));
            EntityInfoSyncPayload back = EntityInfoSyncPayload.CODEC.decode(buf);
            check(h, back.snapshotSeq() == 7L && back.entities().get(0).equals(info)
                    && back.entities().get(1).kind() == EntityInfo.Kind.PLAYER, "the snapshot did not survive: " + back);
            husk.discard();
            GameTestSupport.cleanUp(heli);
            h.succeed();
        });
    }

    /** The BVR lock gives the rider's current seeker the target, and 0 takes it away. */
    private static void bvrLock(GameTestHelper h) {
        HeliEntity heli = GameTestSupport.spawn(h, AH64, new Vec3(8.5, 1, 8.5), 0.0F);
        HeliEntity target = GameTestSupport.spawn(h, AH64, new Vec3(8.5, 1, 20.5), 0.0F);
        Player pilot = GameTestSupport.player(h, GameType.SURVIVAL, new Vec3(10.5, 1, 8.5));
        h.runAfterDelay(2, () -> {
            heli.interact(pilot, InteractionHand.MAIN_HAND, Vec3.ZERO);
            check(h, heli.getPilot() == pilot, "the pilot did not board");
            int seeker = -1;
            for (int i = 0; i < heli.getWeaponNum() && seeker < 0; i++) {
                WeaponSet ws = heli.getWeapon(i);
                if (ws.getCurrentWeapon() != null && ws.getCurrentWeapon().getGuidanceSystem() != null) {
                    seeker = i;
                }
            }
            check(h, seeker >= 0, "the AH-64 has no seeker");
            heli.weapons().switchWeapon(pilot, seeker);
            var gs = heli.getCurrentWeapon(pilot).getCurrentWeapon().getGuidanceSystem();
            ModNetwork.applyBvrLock(pilot, target.getId());
            check(h, gs.getTarget() == target, "the seeker did not take the BVR target");
            ModNetwork.applyBvrLock(pilot, 0);
            check(h, gs.getTarget() == null, "the seeker kept the BVR target");
            GameTestSupport.cleanUp(heli);
            GameTestSupport.cleanUp(target);
            h.succeed();
        });
    }
}
