package io.github.mcheliceport.gametest;

import static io.github.mcheliceport.gametest.GameTestSupport.check;

import io.github.mcheliceport.entity.aircraft.AircraftCamera;
import io.github.mcheliceport.entity.gltd.GltdEntity;
import io.github.mcheliceport.entity.weapon.A10Entity;
import io.github.mcheliceport.entity.weapon.BaseBulletEntity;
import io.github.mcheliceport.net.GltdControlPayload;
import io.github.mcheliceport.registry.ModEntityTypes;
import io.github.mcheliceport.registry.ModItems;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The ground laser target designator (docs/M6_PLAN.md), against upstream's {@code MCH_ItemGLTD},
 * {@code MCH_EntityGLTD} and {@code PacketGLTDControl}.
 */
final class GltdGameTests {

    private GltdGameTests() {
    }

    static void register() {
        ModGameTests.test("gltd_item_places_designator", 20, GltdGameTests::placement);
        ModGameTests.test("gltd_operator_mounts_and_leaves", 20, GltdGameTests::mounting);
        ModGameTests.test("gltd_night_vision_camera", 20, GltdGameTests::nightVision);
        ModGameTests.test("gltd_strike_calls_a10", 100, GltdGameTests::strike);
        ModGameTests.test("gltd_damage_drops_item", 20, GltdGameTests::damage);
    }

    @SuppressWarnings("removal")
    private static ServerPlayer player(GameTestHelper h, GameType mode, Vec3 relative) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(mode);
        Vec3 pos = h.absoluteVec(relative);
        p.snapTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        return p;
    }

    private static GltdEntity gltd(GameTestHelper h, Vec3 relative, float yaw) {
        GltdEntity gltd = ModEntityTypes.GLTD.get().create(h.getLevel(), EntitySpawnReason.COMMAND);
        Vec3 pos = h.absoluteVec(relative);
        gltd.snapTo(pos.x, pos.y, pos.z, yaw, 0.0F);
        h.getLevel().addFreshEntity(gltd);
        return gltd;
    }

    private static void placement(GameTestHelper h) {
        ServerPlayer player = player(h, GameType.SURVIVAL, new Vec3(12.5, 1, 8.5));
        player.setYRot(0);
        player.setXRot(25);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.GLTD.get()));
        var item = ModItems.GLTD.get();
        check(h, item.use(h.getLevel(), player, InteractionHand.MAIN_HAND) == InteractionResult.SUCCESS,
                "the item sets up a designator");
        var list = h.getLevel().getEntitiesOfClass(GltdEntity.class, h.getBounds());
        check(h, list.size() == 1 && player.getMainHandItem().isEmpty(), "one designator and the item used up");
        GltdEntity placed = list.getFirst();
        Vec3 relative = h.relativeVec(placed.position());
        check(h, relative.equals(new Vec3(12.5, 1.0, 11.5)) && placed.getYRot() == 0.0F,
                "on top of the block in view, centred, facing the player's way: " + relative);
        GltdEntity copy = (GltdEntity) EntityType.loadEntityRecursive(save(h, placed), h.getLevel(),
                EntitySpawnReason.LOAD, e -> e);
        check(h, copy != null && copy.position().equals(placed.position()), "the designator is saved with the world");
        placed.discard();

        player.setXRot(-60);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
        check(h, item.use(h.getLevel(), player, InteractionHand.MAIN_HAND) == InteractionResult.PASS,
                "nothing in reach places nothing");
        player.setXRot(25);
        GltdEntity enclosing = gltd(h, h.relativeVec(player.getEyePosition()).subtract(0, 0.25, 0), 0.0F);
        check(h, item.use(h.getLevel(), player, InteractionHand.MAIN_HAND) == InteractionResult.FAIL
                && player.getMainHandItem().getCount() == 1, "eyes inside another entity refuse placement");
        enclosing.discard();

        ServerPlayer creative = player(h, GameType.CREATIVE, new Vec3(10.5, 1, 8.5));
        creative.setYRot(0);
        creative.setXRot(25);
        creative.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
        check(h, item.use(h.getLevel(), creative, InteractionHand.MAIN_HAND) == InteractionResult.SUCCESS
                && creative.getMainHandItem().getCount() == 1, "creative keeps the item");
        h.getLevel().getEntitiesOfClass(GltdEntity.class, h.getBounds()).forEach(Entity::discard);
        h.succeed();
    }

    private static ValueInput save(GameTestHelper h, Entity entity) {
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, h.getLevel().registryAccess());
        check(h, entity.save(output), "the designator is persistent");
        return TagValueInput.create(ProblemReporter.DISCARDING, h.getLevel().registryAccess(),
                output.buildResult());
    }

    private static void mounting(GameTestHelper h) {
        GltdEntity gltd = gltd(h, new Vec3(12.5, 1, 12.5), 90.0F);
        ServerPlayer player = player(h, GameType.SURVIVAL, new Vec3(14.5, 1, 12.5));
        player.setYRot(10.0F);
        check(h, gltd.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO) == InteractionResult.SUCCESS
                && player.getVehicle() == gltd, "using the designator sits the player at it");
        check(h, player.getYRot() == 90.0F, "the operator faces the designator's way");
        gltd.positionRider(player);
        Vec3 offset = player.position().subtract(gltd.position());
        check(h, Math.abs(offset.x - 0.5) < 1.0E-6 && Math.abs(offset.y + 0.65) < 1.0E-6 && Math.abs(offset.z) < 1.0E-6,
                "the operator sits half a block behind: " + offset);
        ServerPlayer other = player(h, GameType.SURVIVAL, new Vec3(10.5, 1, 12.5));
        check(h, gltd.interact(other, InteractionHand.MAIN_HAND, Vec3.ZERO) == InteractionResult.PASS
                && other.getVehicle() == null, "a second player cannot take the seat");
        GltdEntity.handleControl(other, new GltdControlPayload((byte) -1, false, true));
        check(h, player.getVehicle() == gltd, "another player's controls do nothing");

        player.setYRot(200.0F);
        player.setXRot(85.0F);
        gltd.clampView(player);
        check(h, player.getYRot() == 160.0F && player.getXRot() == 70.0F,
                "the view stays within 70 degrees of the heading and level: " + player.getYRot());

        GltdEntity.handleControl(player, new GltdControlPayload((byte) -1, false, true));
        check(h, player.getVehicle() == null, "the unmount key leaves");
        Vec3 exit = gltd.getDismountLocationForPassenger(player).subtract(gltd.position());
        check(h, Math.abs(exit.x - 1.2) < 1.0E-6 && Math.abs(exit.z) < 1.0E-6, "leaving puts the operator 1.2 behind");
        gltd.discard();
        h.succeed();
    }

    private static void nightVision(GameTestHelper h) {
        GltdEntity gltd = gltd(h, new Vec3(12.5, 1, 12.5), 0.0F);
        ServerPlayer player = player(h, GameType.SURVIVAL, new Vec3(12.5, 1, 10.5));
        gltd.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO);
        GltdEntity.handleControl(player, new GltdControlPayload((byte) 1, false, false));
        check(h, gltd.camera.getMode(0) == AircraftCamera.MODE_NIGHTVISION, "the camera switches to night vision");
        gltd.tick();
        check(h, player.hasEffect(MobEffects.NIGHT_VISION), "night vision gives the operator the effect");
        player.stopRiding();
        check(h, gltd.camera.getMode(0) == AircraftCamera.MODE_NORMAL && !player.hasEffect(MobEffects.NIGHT_VISION),
                "leaving switches the camera back and ends the effect");
        gltd.discard();
        h.succeed();
    }

    private static void strike(GameTestHelper h) {
        GltdEntity gltd = gltd(h, new Vec3(12.5, 1, 4.5), 0.0F);
        ServerPlayer player = player(h, GameType.SURVIVAL, new Vec3(12.5, 1, 2.5));
        gltd.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO);
        player.setYRot(0.0F);
        player.setXRot(10.0F);
        gltd.updateCameraPosition(true);
        check(h, gltd.weapon() != null, "the A-10 strike is defined");
        // The A-10 comes in from 90 blocks to one side of the target; keep both sides loaded.
        var chunks = new java.util.ArrayList<net.minecraft.world.level.ChunkPos>();
        for (int side = -1; side <= 1; side += 2) {
            for (int dz = 0; dz <= 16; dz += 16) {
                var chunk = net.minecraft.world.level.ChunkPos.containing(net.minecraft.core.BlockPos.containing(
                        gltd.getX() + side * 90.0, gltd.getY(), gltd.getZ() + dz));
                chunks.add(chunk);
                h.getLevel().setChunkForced(chunk.x(), chunk.z(), true);
            }
        }
        GltdEntity.handleControl(player, new GltdControlPayload((byte) -1, true, false));
        check(h, gltd.countWait == GltdEntity.SERVER_INTERVAL, "the strike starts the 450-tick wait: " + gltd.countWait);
        check(h, !gltd.useCurrentWeapon(), "no second strike while it waits");
        h.runAfterDelay(52, () -> {
            AABB around = h.getBounds().inflate(200.0);
            // Other tests may call in A-10s nearby; count this operator's only.
            var a10s = h.getLevel().getEntitiesOfClass(A10Entity.class, around, a10 -> a10.shootingEntity == player);
            check(h, a10s.size() == 1,
                    "the first A-10 arrives 50 ticks later, flown for the operator: " + a10s.size());
            check(h, gltd.countWait < 0 && gltd.countWait > GltdEntity.SERVER_INTERVAL, "the wait counts down");
            a10s.forEach(Entity::discard);
            chunks.forEach(chunk -> h.getLevel().setChunkForced(chunk.x(), chunk.z(), false));
            gltd.discard();
            h.getLevel().getEntitiesOfClass(BaseBulletEntity.class, around, b -> b.shootingEntity == player)
                    .forEach(Entity::discard);
            h.succeed();
        });
    }

    private static void damage(GameTestHelper h) {
        GltdEntity gltd = gltd(h, new Vec3(12.5, 1, 12.5), 0.0F);
        ServerPlayer rider = player(h, GameType.SURVIVAL, new Vec3(12.5, 1, 10.5));
        gltd.interact(rider, InteractionHand.MAIN_HAND, Vec3.ZERO);
        gltd.hurtServer(h.getLevel(), h.getLevel().damageSources().generic(), 1.0F);
        check(h, gltd.isRemoved() && rider.getVehicle() == null, "a hit breaks the designator and the operator leaves");
        var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, h.getBounds(),
                e -> e.getItem().is(ModItems.GLTD.get()));
        check(h, drops.size() == 1, "it drops the item");
        drops.forEach(Entity::discard);
        GltdEntity second = gltd(h, new Vec3(12.5, 1, 12.5), 0.0F);
        // The mock player counts as creative whatever its game mode.
        ServerPlayer attacker = player(h, GameType.CREATIVE, new Vec3(14.5, 1, 12.5));
        second.hurtServer(h.getLevel(), h.getLevel().damageSources().playerAttack(attacker), 1.0F);
        check(h, second.isRemoved() && h.getLevel().getEntitiesOfClass(ItemEntity.class, h.getBounds()).isEmpty(),
                "a creative player's hit drops nothing");
        h.succeed();
    }
}
