package io.github.mcheliceport.gametest;

import static io.github.mcheliceport.gametest.GameTestSupport.check;

import io.github.mcheliceport.crafting.DraftingTableRecipes;
import io.github.mcheliceport.item.AircraftItem;
import io.github.mcheliceport.menu.DraftingTableMenu;
import io.github.mcheliceport.net.DraftingTableRecipesPayload;
import io.github.mcheliceport.registry.ModBlocks;
import io.github.mcheliceport.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * The drafting table's recipe browser (docs/M6_PLAN.md), against upstream's
 * {@code MCH_DraftingTableGuiContainer}, {@code PacketDrafttingTableCreate} and
 * {@code MCH_Recipes#canCraft}/{@code consumeInventory}.
 */
final class DraftingTableGameTests {

    private static final Identifier CHAIN = Identifier.fromNamespaceAndPath("mcheli_ce_port", "chain");

    private DraftingTableGameTests() {
    }

    static void register() {
        ModGameTests.test("drafting_table_lists_recipes", 20, DraftingTableGameTests::lists);
        ModGameTests.test("drafting_table_creates_from_inventory", 20, DraftingTableGameTests::creates);
        ModGameTests.test("drafting_table_needs_every_ingredient", 20, DraftingTableGameTests::needsEveryIngredient);
        ModGameTests.test("drafting_table_output_and_closing", 20, DraftingTableGameTests::outputAndClosing);
        ModGameTests.test("drafting_table_stock_vehicles_craft_in_survival", 20, DraftingTableGameTests::stockVehicles);
    }

    private static ServerPlayer player(GameTestHelper h, GameType mode) {
        @SuppressWarnings("removal")
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(mode);
        Vec3 pos = h.absoluteVec(new Vec3(2.5, 1, 2.5));
        p.snapTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        p.getInventory().clearContent();
        return p;
    }

    private static DraftingTableMenu open(GameTestHelper h, ServerPlayer player) {
        BlockPos pos = h.absolutePos(new BlockPos(1, 1, 1));
        h.getLevel().setBlockAndUpdate(pos, ModBlocks.DRAFTING_TABLE.get().defaultBlockState());
        DraftingTableMenu menu = new DraftingTableMenu(1, player.getInventory(), pos,
                DraftingTableRecipes.collect(h.getLevel().getServer().getRecipeManager()));
        player.containerMenu = menu;
        return menu;
    }

    /** Fixed items first in upstream's order, vehicles in their own lists, special recipes left out. */
    private static void lists(GameTestHelper h) {
        List<DraftingTableRecipes.Entry> entries = DraftingTableRecipes.collect(h.getLevel().getServer()
                .getRecipeManager());
        check(h, !entries.isEmpty() && entries.getFirst().result().is(ModItems.FUEL.get()), "fuel first: "
                + (entries.isEmpty() ? "none" : entries.getFirst().id()));
        DraftingTableRecipes.Entry chain = entries.stream().filter(e -> e.id().equals(CHAIN)).findFirst().orElse(null);
        check(h, chain != null && chain.list() == DraftingTableRecipes.ITEM, "the chain is in the item list");
        check(h, chain.grid().size() == 9 && chain.grid().get(1).isEmpty() && chain.ingredients().size() == 7,
                "the chain's grid keeps its gap: " + chain.grid());
        check(h, entries.stream().anyMatch(e -> e.result().is(ModItems.DRAFTING_TABLE.get())), "the table itself");
        check(h, entries.stream().noneMatch(e -> e.id().getPath().equals("charge_fuel")), "no special recipes");
        check(h, entries.stream().map(DraftingTableRecipes.Entry::list).collect(Collectors.toSet())
                .equals(Set.of(0, 1, 2, 3, 4, 5)), "all six recipe lists contain stock items");
        var buf = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),
                h.getLevel().registryAccess());
        var sent = new DraftingTableRecipesPayload(3, entries);
        DraftingTableRecipesPayload.CODEC.encode(buf, sent);
        var read = DraftingTableRecipesPayload.CODEC.decode(buf);
        check(h, read.menuId() == 3 && read.recipes().size() == entries.size()
                && read.recipes().getFirst().id().equals(entries.getFirst().id())
                && ItemStack.matches(read.recipes().getFirst().result(), entries.getFirst().result()),
                "the list reaches the client");
        for (DraftingTableRecipes.Entry e : entries) {
            boolean vehicle = e.result().getItem() instanceof AircraftItem;
            check(h, vehicle == (e.list() != DraftingTableRecipes.ITEM), "list of " + e.id());
        }
        h.succeed();
    }

    /** The shipped pack must supply real recipes, not just creative vehicle registrations. */
    private static void stockVehicles(GameTestHelper h) {
        ServerPlayer p = player(h, GameType.SURVIVAL);
        DraftingTableMenu menu = open(h, p);
        var manager = h.getLevel().getServer().getRecipeManager();
        int crafted = 0;
        for (var entry : menu.recipes()) {
            if (!(entry.result().getItem() instanceof AircraftItem)) continue;
            p.getInventory().clearContent();
            menu.getSlot(menu.outputSlotIndex).set(ItemStack.EMPTY);
            List<ItemStack> grid = new ArrayList<>();
            for (int i = 0; i < entry.grid().size(); i++) {
                ItemStack ingredient = entry.grid().get(i).map(value -> new ItemStack(value.items()
                        .findFirst().orElseThrow().value())).orElse(ItemStack.EMPTY);
                grid.add(ingredient.copy());
                p.getInventory().setItem(i, ingredient);
            }
            check(h, manager.getRecipeFor(RecipeType.CRAFTING, CraftingInput.of(3, 3, grid), h.getLevel()).isPresent(),
                    "the crafting table can match " + entry.id());
            check(h, menu.create(entry.id()), "survival crafting succeeds for " + entry.id());
            check(h, ItemStack.matches(menu.getSlot(menu.outputSlotIndex).getItem(), entry.result()),
                    "the selected vehicle is produced: " + entry.id());
            check(h, p.getInventory().isEmpty(), "exactly one item per ingredient consumed: " + entry.id());
            crafted++;
        }
        check(h, crafted == 115, "all 115 stock vehicle recipes craft, found " + crafted);
        h.succeed();
    }

    /** Create uses one item per ingredient from the inventory and fills the output slot. */
    private static void creates(GameTestHelper h) {
        ServerPlayer p = player(h, GameType.SURVIVAL);
        DraftingTableMenu menu = open(h, p);
        p.getInventory().setItem(5, new ItemStack(Items.IRON_INGOT, 10));
        check(h, menu.create(CHAIN), "created");
        check(h, menu.getSlot(menu.outputSlotIndex).getItem().is(ModItems.CHAIN.get()), "the chain waits");
        check(h, p.getInventory().getItem(5).getCount() == 3, "seven ingots used: " + p.getInventory().getItem(5));
        check(h, !menu.create(CHAIN), "not while the output is full");
        check(h, p.getInventory().getItem(5).getCount() == 3, "nothing more used");
        check(h, !menu.create(Identifier.fromNamespaceAndPath("minecraft", "oak_planks")), "not a vanilla recipe");
        check(h, !menu.create(Identifier.fromNamespaceAndPath("mcheli_ce_port", "charge_fuel")), "not a special one");
        h.succeed();
    }

    /** Upstream let one ingot stand for all seven; here a short inventory makes nothing and keeps everything. */
    private static void needsEveryIngredient(GameTestHelper h) {
        ServerPlayer p = player(h, GameType.SURVIVAL);
        DraftingTableMenu menu = open(h, p);
        p.getInventory().setItem(0, new ItemStack(Items.IRON_INGOT, 4));
        p.getInventory().setItem(9, new ItemStack(Items.IRON_INGOT, 2));
        check(h, !menu.create(CHAIN), "six ingots are not enough");
        check(h, !menu.getSlot(menu.outputSlotIndex).hasItem(), "no chain");
        check(h, p.getInventory().getItem(0).getCount() == 4 && p.getInventory().getItem(9).getCount() == 2,
                "nothing used");
        p.getInventory().setItem(20, new ItemStack(Items.IRON_INGOT, 1));
        check(h, menu.create(CHAIN), "seven across three stacks");
        check(h, p.getInventory().countItem(Items.IRON_INGOT) == 0, "all used");
        h.succeed();
    }

    /** Creative needs no items and may replace the output; closing drops it; the table must be near. */
    private static void outputAndClosing(GameTestHelper h) {
        ServerPlayer p = player(h, GameType.CREATIVE);
        DraftingTableMenu menu = open(h, p);
        check(h, menu.create(CHAIN) && menu.create(CHAIN), "creative makes it twice with nothing");
        check(h, menu.quickMoveStack(p, 0).isEmpty(), "inventory slots do not shift-click");
        check(h, !menu.quickMoveStack(p, menu.outputSlotIndex).isEmpty()
                && p.getInventory().countItem(ModItems.CHAIN.get()) == 1, "the output shift-clicks in");
        check(h, menu.create(CHAIN), "made again");
        menu.removed(p);
        check(h, !h.getLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(4.0),
                e -> e.getItem().is(ModItems.CHAIN.get())).isEmpty(), "dropped on closing");
        check(h, menu.stillValid(p), "valid beside the table");
        h.getLevel().setBlockAndUpdate(h.absolutePos(new BlockPos(1, 1, 1)), Blocks.AIR.defaultBlockState());
        check(h, !menu.stillValid(p), "not once the table is gone");
        h.succeed();
    }
}
