package io.github.mcheliceport.gametest;

import static io.github.mcheliceport.gametest.GameTestSupport.AH64;
import static io.github.mcheliceport.gametest.GameTestSupport.check;
import static io.github.mcheliceport.gametest.GameTestSupport.player;
import static io.github.mcheliceport.gametest.GameTestSupport.stack;

import io.github.mcheliceport.config.CommonConfig;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.item.AircraftItem;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;

/** Exit criterion E5: placing helicopters from their items and from dispensers, as upstream. */
final class PlacementGameTests {

    private PlacementGameTests() {
    }

    static void register() {
        ModGameTests.test("placement_on_block", 20, PlacementGameTests::onBlock);
        ModGameTests.test("placement_obstructed", 20, PlacementGameTests::obstructed);
        ModGameTests.test("placement_creative_only", 20, PlacementGameTests::creativeOnly);
        ModGameTests.test("placement_sponge_only", 20, PlacementGameTests::spongeOnly);
        ModGameTests.test("placement_dispenser", 60, PlacementGameTests::dispenser);
    }

    /** A player at {@code (x, 1, z)} looking straight down at the floor with {@code yaw}, holding the AH-64. */
    private static Player placer(GameTestHelper helper, GameType mode, int x, int z, float yaw) {
        Player player = player(helper, mode, new Vec3(x + 0.5, 1.0, z + 0.5));
        player.setYRot(yaw);
        player.setXRot(90.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack(AH64));
        return player;
    }

    private static void use(GameTestHelper helper, Player player) {
        ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
        held.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
    }

    private static List<HeliEntity> helis(GameTestHelper helper) {
        // By class: the hit boxes are parts of the helicopter's entity type too.
        return helper.getLevel().getEntities(EntityTypeTest.forClass(HeliEntity.class), helper.getBounds(),
                Entity::isAlive);
    }

    private static void onBlock(GameTestHelper helper) {
        Player survival = placer(helper, GameType.SURVIVAL, 6, 6, 100.0F);
        use(helper, survival);
        List<HeliEntity> placed = helis(helper);
        check(helper, placed.size() == 1, "one helicopter placed, got " + placed.size());
        HeliEntity heli = placed.getFirst();
        Vec3 rel = helper.relativeVec(heli.position());
        check(helper, rel.distanceTo(new Vec3(6.5, 1.0, 6.5)) < 1.0E-6, "one block above the clicked block: " + rel);
        check(helper, heli.getYaw() == 0.0F, "yaw 100 snaps to 0 (upstream's quarter snap), got " + heli.getYaw());
        check(helper, survival.getMainHandItem().isEmpty(), "the item is used up in survival");

        Player creative = placer(helper, GameType.CREATIVE, 18, 18, -170.0F);
        use(helper, creative);
        check(helper, helis(helper).size() == 2, "a second helicopter placed");
        HeliEntity second = helis(helper).stream().filter(h -> h != heli).findFirst().orElseThrow();
        check(helper, second.getYaw() == 90.0F, "yaw -170 snaps to 90, got " + second.getYaw());
        check(helper, creative.getMainHandItem().getCount() == 1, "the item is kept in creative");
        check(helper, AircraftItem.snapYaw(0.0F) == -90.0F, "yaw 0 snaps to -90");
        helis(helper).forEach(GameTestSupport::cleanUp);
        helper.succeed();
    }

    private static void obstructed(GameTestHelper helper) {
        helper.setBlock(new BlockPos(13, 1, 12), Blocks.STONE);
        Player player = placer(helper, GameType.SURVIVAL, 12, 12, 0.0F);
        use(helper, player);
        check(helper, helis(helper).isEmpty(), "nothing is placed into blocks");
        check(helper, player.getMainHandItem().getCount() == 1, "the item is kept");
        helper.succeed();
    }

    private static void creativeOnly(GameTestHelper helper) {
        AircraftInfo info = GameTestSupport.item(AH64).getAircraftInfo();
        check(helper, info != null, "AH-64 content loaded");
        boolean old = info.creativeOnly;
        info.creativeOnly = true;
        try {
            Player survival = placer(helper, GameType.SURVIVAL, 12, 12, 0.0F);
            use(helper, survival);
            check(helper, helis(helper).isEmpty(), "a creative-only helicopter is refused in survival");
            check(helper, survival.getMainHandItem().getCount() == 1, "the item is kept");
            Player creative = placer(helper, GameType.CREATIVE, 12, 12, 0.0F);
            use(helper, creative);
            check(helper, helis(helper).size() == 1, "creative players may place it");
        } finally {
            info.creativeOnly = old;
        }
        helis(helper).forEach(GameTestSupport::cleanUp);
        helper.succeed();
    }

    private static void spongeOnly(GameTestHelper helper) {
        GameTestSupport.withConfig(CommonConfig.PLACEABLE_ON_SPONGE_ONLY, true, () -> {
            Player player = placer(helper, GameType.SURVIVAL, 6, 6, 0.0F);
            use(helper, player);
            check(helper, helis(helper).isEmpty(), "PlaceableOnSpongeOnly refuses stone");
            helper.setBlock(new BlockPos(18, 0, 18), Blocks.SPONGE);
            Player onSponge = placer(helper, GameType.SURVIVAL, 18, 18, 0.0F);
            use(helper, onSponge);
            check(helper, helis(helper).size() == 1, "PlaceableOnSpongeOnly accepts a sponge");
        });
        helis(helper).forEach(GameTestSupport::cleanUp);
        helper.succeed();
    }

    private static void dispenser(GameTestHelper helper) {
        BlockPos pos = new BlockPos(6, 1, 12);
        helper.setBlock(pos, Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.EAST));
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(pos)) instanceof DispenserBlockEntity dispenser)) {
            throw helper.assertionException(net.minecraft.network.chat.Component.literal("no dispenser"));
        }
        dispenser.setItem(0, stack(AH64));
        helper.setBlock(new BlockPos(6, 1, 11), Blocks.REDSTONE_BLOCK);
        helper.succeedWhen(() -> {
            List<HeliEntity> placed = helis(helper);
            check(helper, placed.size() == 1, "the dispenser placed a helicopter");
            Vec3 rel = helper.relativeVec(placed.getFirst().position());
            check(helper, Math.abs(rel.x - 8.5) < 1.0E-6 && Math.abs(rel.z - 12.5) < 1.0E-6,
                    "two blocks in front of the dispenser: " + rel);
            check(helper, dispenser.isEmpty(), "the dispenser used the item");
            placed.forEach(GameTestSupport::cleanUp);
        });
    }
}
