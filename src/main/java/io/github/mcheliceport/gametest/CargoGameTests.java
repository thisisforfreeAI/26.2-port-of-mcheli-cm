package io.github.mcheliceport.gametest;

import static io.github.mcheliceport.gametest.GameTestSupport.check;

import io.github.mcheliceport.config.CommonConfig;
import io.github.mcheliceport.entity.container.CargoContainerEntity;
import io.github.mcheliceport.registry.ModEntityTypes;
import io.github.mcheliceport.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** Cargo placement, persistence, inventory transactions, damage/drop lifecycle and motion. */
final class CargoGameTests {
    private CargoGameTests() { }
    static void register() {
        ModGameTests.test("cargo_item_placement", 30, CargoGameTests::placement);
        ModGameTests.test("cargo_inventory_persistence", 30, CargoGameTests::persistence);
        ModGameTests.test("cargo_menu_and_capability", 30, CargoGameTests::inventory);
        ModGameTests.test("cargo_damage_and_drop_once", 30, CargoGameTests::damage);
        ModGameTests.test("cargo_unload_transfer_lifecycle", 30, CargoGameTests::lifecycle);
        ModGameTests.test("cargo_motion_and_fluids", 30, CargoGameTests::motion);
        ModGameTests.test("cargo_soft_blocks_and_collision", 30, CargoGameTests::collision);
    }

    private static CargoContainerEntity cargo(GameTestHelper h, Vec3 pos) {
        var cargo = new CargoContainerEntity(ModEntityTypes.CONTAINER.get(), h.getLevel());
        cargo.setPos(h.absoluteVec(pos)); h.getLevel().addFreshEntity(cargo); return cargo;
    }
    private static void done(GameTestHelper h, CargoContainerEntity... entities) {
        for (var entity : entities) { entity.clearContent(); entity.discard(); }
        h.succeed();
    }
    private static int drops(GameTestHelper h, CargoContainerEntity cargo, net.minecraft.world.item.Item item) {
        return h.getLevel().getEntitiesOfClass(ItemEntity.class, cargo.getBoundingBox().inflate(3)).stream()
                .filter(e -> e.getItem().is(item)).mapToInt(e -> e.getItem().getCount()).sum();
    }
    private static void placement(GameTestHelper h) {
        var player = GameTestSupport.player(h, GameType.SURVIVAL, new Vec3(12.5, 1, 8.5));
        player.setYRot(0); player.setXRot(25);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.CONTAINER.get()));
        var item = ModItems.CONTAINER.get();
        check(h, item.use(h.getLevel(), player, InteractionHand.MAIN_HAND) == InteractionResult.SUCCESS, "cargo placed by item");
        var list = h.getLevel().getEntitiesOfClass(CargoContainerEntity.class, h.getBounds());
        check(h, list.size() == 1 && player.getMainHandItem().isEmpty(), "one server entity and one item consumed");
        var cargo = list.getFirst(); var relative = h.relativeVec(cargo.position());
        check(h, relative.equals(new Vec3(12.5, 1.5, 11.5)) && cargo.getYRot() == -90, "upstream half-block offset and quarter-turn yaw: " + relative);
        cargo.discard();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
        h.setBlock(13, 2, 11, Blocks.STONE);
        check(h, item.use(h.getLevel(), player, InteractionHand.MAIN_HAND) == InteractionResult.FAIL && player.getMainHandItem().getCount() == 1,
                "side obstruction refuses placement without consuming item");
        h.setBlock(13, 2, 11, Blocks.AIR);
        var creative = GameTestSupport.player(h, GameType.CREATIVE, new Vec3(12.5, 1, 8.5));
        creative.setXRot(25); creative.setYRot(0); creative.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
        check(h, item.use(h.getLevel(), creative, InteractionHand.MAIN_HAND) == InteractionResult.SUCCESS && creative.getMainHandItem().getCount() == 1,
                "creative placement preserves item");
        h.getLevel().getEntitiesOfClass(CargoContainerEntity.class, h.getBounds()).forEach(Entity::discard);
        player.setXRot(-60);
        check(h, item.use(h.getLevel(), player, InteractionHand.MAIN_HAND) == InteractionResult.PASS, "miss does not place");
        var enclosing = cargo(h, new Vec3(12.5, 2, 8.5)); player.setXRot(25);
        check(h, item.use(h.getLevel(), player, InteractionHand.MAIN_HAND) == InteractionResult.FAIL && player.getMainHandItem().getCount() == 1,
                "eye inside a pickable entity refuses placement");
        done(h, enclosing);
    }
    private static CargoContainerEntity copy(GameTestHelper h, CargoContainerEntity cargo) {
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, h.getLevel().registryAccess());
        check(h, cargo.save(output), "cargo is persistent");
        return (CargoContainerEntity) EntityType.loadEntityRecursive(TagValueInput.create(ProblemReporter.DISCARDING,
                h.getLevel().registryAccess(), output.buildResult()), h.getLevel(), EntitySpawnReason.LOAD, e -> e);
    }
    private static void persistence(GameTestHelper h) {
        var cargo = cargo(h, new Vec3(12, 1, 12));
        for (int i = 0; i < 54; i++) { cargo.setItem(i, new ItemStack(i % 2 == 0 ? Items.DIAMOND : Items.EMERALD, i + 1)); }
        cargo.getItem(53).set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                net.minecraft.network.chat.Component.literal("Medical cargo"));
        cargo.setCustomName(net.minecraft.network.chat.Component.literal("Flight cargo"));
        var copy = copy(h, cargo);
        check(h, copy.getContainerSize() == 54 && copy.getName().getString().equals("Flight cargo"), "54 slots and custom name reload");
        for (int i = 0; i < 54; i++) {
            check(h, ItemStack.matches(cargo.getItem(i), copy.getItem(i)), "slot " + i + " survives with count and position");
        }
        copy.removeItem(53, 10);
        check(h, cargo.getItem(53).getCount() == 54 && copy.getItem(53).getCount() == 44, "loaded stacks are independent");
        done(h, cargo, copy);
    }
    private static void inventory(GameTestHelper h) {
        var cargo = cargo(h, new Vec3(12, 1, 12));
        var player = GameTestSupport.player(h, GameType.SURVIVAL, new Vec3(12, 1, 9));
        var other = GameTestSupport.player(h, GameType.SURVIVAL, new Vec3(12, 1, 10));
        var menu = (ChestMenu) cargo.createMenu(1, player.getInventory(), player);
        var peer = (ChestMenu) cargo.createMenu(2, other.getInventory(), other);
        check(h, menu.getRowCount() == 6 && menu.slots.size() == 90 && menu.stillValid(player), "standard six-row menu within reach");
        player.getInventory().setItem(9, new ItemStack(Items.DIAMOND, 32));
        menu.quickMoveStack(player, 54);
        check(h, cargo.getItem(0).getCount() == 32 && player.getInventory().getItem(9).isEmpty() && peer.getSlot(0).getItem().getCount() == 32,
                "shift-click moves items into the shared cargo inventory");
        peer.quickMoveStack(other, 0);
        check(h, cargo.isEmpty() && other.getInventory().countItem(Items.DIAMOND) == 32, "second viewer extracts the same items once");
        var handler = cargo.getCapability(Capabilities.Item.ENTITY);
        var automation = cargo.getCapability(Capabilities.Item.ENTITY_AUTOMATION, Direction.DOWN);
        check(h, handler != null && automation != null && handler.size() == 54, "current NeoForge item capabilities exposed");
        try (var transaction = Transaction.openRoot()) {
            check(h, handler.insert(53, ItemResource.of(Items.EMERALD), 12, transaction) == 12, "capability insertion simulated");
        }
        check(h, cargo.getItem(53).isEmpty(), "uncommitted transaction rolls back");
        try (var transaction = Transaction.openRoot()) {
            automation.insert(53, ItemResource.of(Items.EMERALD), 12, transaction); transaction.commit();
        }
        check(h, cargo.getItem(53).getCount() == 12 && handler.getAmountAsInt(53) == 12, "committed automation reaches the last slot");
        player.setPos(h.absoluteVec(new Vec3(12, 1, 23)));
        check(h, !menu.stillValid(player) && cargo.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO) == InteractionResult.PASS,
                "remote player cannot open or retain menu");
        cargo.discard(); check(h, !peer.stillValid(other), "removed cargo invalidates all viewers"); h.succeed();
    }
    private static void damage(GameTestHelper h) {
        var cargo = cargo(h, new Vec3(12, 1, 12));
        cargo.setItem(0, new ItemStack(Items.DIAMOND, 37)); cargo.setItem(53, new ItemStack(Items.EMERALD, 9));
        var player = GameTestSupport.player(h, GameType.SURVIVAL, new Vec3(12, 1, 9));
        check(h, !cargo.hurtServer(h.getLevel(), cargo.damageSources().generic(), 100) && cargo.isAlive(), "non-player damage ignored");
        cargo.setInvulnerable(true);
        check(h, !cargo.hurtServer(h.getLevel(), cargo.damageSources().playerAttack(player), 3), "invulnerable cargo rejects attacks");
        cargo.setInvulnerable(false);
        check(h, cargo.hurtServer(h.getLevel(), cargo.damageSources().playerAttack(player), 2) && cargo.getDamageTaken() == 40 && cargo.isAlive(), "exactly 40 damage is not enough");
        cargo.tick(); check(h, cargo.getDamageTaken() == 39, "damage decays once per tick");
        cargo.hurtServer(h.getLevel(), cargo.damageSources().playerAttack(player), 1);
        cargo.discard(); cargo.remove(Entity.RemovalReason.KILLED);
        check(h, cargo.isRemoved() && drops(h, cargo, ModItems.CONTAINER.get()) == 1
                && drops(h, cargo, Items.DIAMOND) == 37 && drops(h, cargo, Items.EMERALD) == 9 && cargo.isEmpty(), "survival breaks into one empty container and all contents exactly once");
        var creative = GameTestSupport.player(h, GameType.CREATIVE, new Vec3(4, 1, 4));
        var second = cargo(h, new Vec3(5, 1, 5)); second.setItem(0, new ItemStack(Items.GOLD_INGOT, 7));
        second.hurtServer(h.getLevel(), second.damageSources().playerAttack(creative), 0);
        check(h, second.isRemoved() && drops(h, second, ModItems.CONTAINER.get()) == 0 && drops(h, second, Items.GOLD_INGOT) == 7,
                "creative removal drops contents but no container item"); h.succeed();
    }
    private static void lifecycle(GameTestHelper h) {
        for (var reason : new Entity.RemovalReason[] {Entity.RemovalReason.UNLOADED_TO_CHUNK, Entity.RemovalReason.CHANGED_DIMENSION}) {
            var cargo = cargo(h, new Vec3(12, 2, 12)); cargo.setItem(53, new ItemStack(Items.IRON_INGOT, 23));
            var copy = copy(h, cargo); cargo.remove(reason);
            check(h, drops(h, cargo, Items.IRON_INGOT) == 0 && copy.getItem(53).getCount() == 23, "unload/dimension transfer preserves contents without world drops");
            copy.clearContent(); copy.discard();
        }
        h.succeed();
    }
    private static void motion(GameTestHelper h) {
        var cargo = cargo(h, new Vec3(12, 10, 12)); cargo.setDeltaMovement(1, 0, 0);
        var start = cargo.position(); cargo.tick();
        check(h, Math.abs(cargo.getX() - start.x - 0.35) < 1e-6 && Math.abs(cargo.getY() - start.y + 0.04) < 1e-6,
                "single movement step caps speed at .35 and applies gravity .04");
        check(h, Math.abs(cargo.getDeltaMovement().x - .3465) < 1e-6 && Math.abs(cargo.getDeltaMovement().y + .038) < 1e-6 && cargo.getYRot() == -5,
                "upstream damping and five-degree yaw limit");
        cargo.discard();
        for (var fluid : new net.minecraft.world.level.block.Block[] {Blocks.WATER, Blocks.LAVA}) {
            // Remove the previous fluid first so water/lava neighbor reactions cannot form stone.
            for (int x = 8; x <= 16; x++) for (int y = 1; y <= 5; y++) for (int z = 8; z <= 16; z++) { h.setBlock(x, y, z, Blocks.AIR); }
            for (int x = 8; x <= 16; x++) for (int y = 1; y <= 5; y++) for (int z = 8; z <= 16; z++) { h.setBlock(x, y, z, fluid); }
            var floating = cargo(h, new Vec3(12, 3, 12)); floating.setDeltaMovement(0, -.2, 0);
            check(h, floating.fluidDepth() == 1, "all five " + fluid + " slices submerged: " + floating.fluidDepth());
            double y = floating.getY(); floating.tick();
            check(h, Math.abs(floating.getY() - y + .093) < 1e-6 && floating.fireImmune(), "submerged descent halves before buoyancy in water/lava");
            floating.discard();
        }
        h.succeed();
    }
    private static void collision(GameTestHelper h) {
        var cargo = cargo(h, new Vec3(12, 2.2, 12.5));
        h.setBlock(12, 1, 12, Blocks.STONE); h.setBlock(12, 2, 12, Blocks.SNOW);
        h.setBlock(11, 1, 12, Blocks.WATER); h.setBlock(11, 2, 12, Blocks.LILY_PAD);
        GameTestSupport.withConfig(CommonConfig.COLLISION_DESTROY_BLOCK, false, cargo::tick);
        check(h, h.getBlockState(new BlockPos(12, 2, 12)).is(Blocks.SNOW), "soft-block clearing respects global config");
        cargo.setPos(h.absoluteVec(new Vec3(12, 2.2, 12.5))); cargo.setDeltaMovement(Vec3.ZERO);
        GameTestSupport.withConfig(CommonConfig.COLLISION_DESTROY_BLOCK, true, cargo::tick);
        check(h, h.getBlockState(new BlockPos(12, 2, 12)).isAir() && h.getBlockState(new BlockPos(11, 2, 12)).isAir(), "snow and lily pads clear at sampled corners");
        var second = cargo(h, new Vec3(13.9, 2.2, 12.5)); second.setDeltaMovement(Vec3.ZERO); cargo.tick();
        check(h, second.getDeltaMovement().horizontalDistanceSqr() > 0 && cargo.isPushable() && cargo.canCollideWith(second), "nearby cargo containers push each other and collide");
        done(h, cargo, second);
    }
}
