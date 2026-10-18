package io.github.mcheliceport.gametest;

import static io.github.mcheliceport.gametest.GameTestSupport.check;

import io.github.mcheliceport.crafting.Example;
import io.github.mcheliceport.crafting.FuelRefillRecipe;
import io.github.mcheliceport.crafting.RangeFinderRechargeRecipe;
import io.github.mcheliceport.registry.ModItems;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;

/**
 * The fuel refill and rangefinder recharge (docs/M6_PLAN.md), against upstream's
 * {@code MCH_RecipeFuel} and {@code MCH_RecipeReloadRangeFinder}: what matches, what is made, the
 * loaded recipe files, real crafting in a crafting table menu and the examples shown in JEI.
 */
final class CraftingGameTests {

    private static final int FULL = 600;

    private CraftingGameTests() {
    }

    static void register() {
        ModGameTests.test("crafting_fuel_refill_matches", 20, CraftingGameTests::fuelMatches);
        ModGameTests.test("crafting_fuel_refill_amounts", 20, CraftingGameTests::fuelAmounts);
        ModGameTests.test("crafting_rangefinder_recharge", 20, CraftingGameTests::rangeFinder);
        ModGameTests.test("crafting_special_recipes_loaded", 20, CraftingGameTests::loaded);
        ModGameTests.test("crafting_table_refills_fuel", 20, CraftingGameTests::craftingTableFuel);
        ModGameTests.test("crafting_table_recharges_rangefinder", 20, CraftingGameTests::craftingTableRangeFinder);
        ModGameTests.test("crafting_jei_examples", 20, CraftingGameTests::examples);
    }

    private static ItemStack can(int damage) {
        return FuelRefillRecipe.can(damage);
    }

    private static ItemStack stack(Item item, int count) {
        return new ItemStack(item, count);
    }

    private static ItemStack coal() {
        return stack(Items.COAL, 1);
    }

    private static ItemStack charcoal() {
        return stack(Items.CHARCOAL, 1);
    }

    private static ItemStack finder(int damage) {
        ItemStack finder = new ItemStack(ModItems.RANGEFINDER.get());
        finder.setDamageValue(damage);
        return finder;
    }

    private static ItemStack[] repeat(ItemStack first, ItemStack rest, int count) {
        List<ItemStack> stacks = new ArrayList<>();
        stacks.add(first);
        for (int i = 0; i < count; i++) {
            stacks.add(rest.copy());
        }
        return stacks.toArray(ItemStack[]::new);
    }

    /** A 3×3 grid with {@code stacks} in the first slots. */
    private static CraftingInput grid(ItemStack... stacks) {
        List<ItemStack> items = new ArrayList<>(Collections.nCopies(9, ItemStack.EMPTY));
        for (int i = 0; i < stacks.length; i++) {
            items.set(i, stacks[i]);
        }
        return CraftingInput.of(3, 3, items);
    }

    private static boolean fuel(GameTestHelper h, ItemStack... stacks) {
        return FuelRefillRecipe.INSTANCE.matches(grid(stacks), h.getLevel());
    }

    private static void fuelMatches(GameTestHelper h) {
        check(h, ModItems.FUEL.get().getDefaultInstance().getMaxDamage() == FULL, "a can holds upstream's 600");
        check(h, fuel(h, can(FULL), coal()), "an empty can with coal");
        check(h, fuel(h, can(1), charcoal()), "a nearly full can with charcoal");
        check(h, fuel(h, coal(), can(300), stack(Items.CHARCOAL, 5)), "any order, mixed coal");
        check(h, !fuel(h, can(0), coal()), "a full can");
        check(h, !fuel(h, can(FULL)), "no coal");
        check(h, !fuel(h, coal()), "no can");
        check(h, !fuel(h, can(FULL), can(FULL), coal()), "two cans");
        check(h, !fuel(h, can(FULL), can(0), coal()), "a used and a full can");
        check(h, !fuel(h, can(FULL), coal(), stack(Items.IRON_INGOT, 1)), "something else");
        check(h, !fuel(h, can(FULL), stack(Items.COAL_BLOCK, 1)), "coal blocks are not coal");
        check(h, !fuel(h), "an empty grid");
        CraftingInput small = CraftingInput.of(2, 2, List.of(can(FULL), coal(), ItemStack.EMPTY, ItemStack.EMPTY));
        check(h, FuelRefillRecipe.INSTANCE.matches(small, h.getLevel()),
                "the inventory's 2x2 grid: 1.12 used canFit only for the recipe book");
        h.succeed();
    }

    private static int refilled(GameTestHelper h, ItemStack... stacks) {
        CraftingInput input = grid(stacks);
        check(h, FuelRefillRecipe.INSTANCE.matches(input, h.getLevel()), "matches");
        ItemStack out = FuelRefillRecipe.INSTANCE.assemble(input);
        check(h, out.is(ModItems.FUEL.get()) && out.getCount() == 1, "one fuel can: " + out);
        check(h, FuelRefillRecipe.INSTANCE.getRemainingItems(input).stream().allMatch(ItemStack::isEmpty),
                "nothing is handed back");
        return out.getDamageValue();
    }

    private static void expect(GameTestHelper h, int expected, int actual, String what) {
        check(h, expected == actual, what + ": " + actual + " missing fuel, expected " + expected);
    }

    private static void fuelAmounts(GameTestHelper h) {
        expect(h, FULL - 100, refilled(h, can(FULL), coal()), "one coal");
        expect(h, FULL - 75, refilled(h, can(FULL), charcoal()), "one charcoal");
        expect(h, FULL - 100, refilled(h, can(FULL), stack(Items.COAL, 64)), "a stack of coal counts once");
        expect(h, FULL - 175, refilled(h, can(FULL), coal(), charcoal()), "coal and charcoal");
        expect(h, FULL - 300, refilled(h, repeat(can(FULL), coal(), 3)), "three coal");
        expect(h, 0, refilled(h, repeat(can(FULL), coal(), 6)), "six coal fill an empty can");
        expect(h, 0, refilled(h, repeat(can(FULL), coal(), 8)), "more coal than needed");
        expect(h, 0, refilled(h, repeat(can(FULL), charcoal(), 8)), "eight charcoal fill an empty can");
        expect(h, 0, refilled(h, can(50), charcoal()), "capped at full");
        expect(h, 0, refilled(h, can(75), charcoal()), "exactly full");
        expect(h, 1, refilled(h, can(76), charcoal()), "one short");
        h.succeed();
    }

    private static void rangeFinder(GameTestHelper h) {
        RangeFinderRechargeRecipe recipe = RangeFinderRechargeRecipe.INSTANCE;
        var level = h.getLevel();
        check(h, recipe.matches(grid(finder(10), stack(Items.REDSTONE, 1)), level), "a used finder and redstone");
        check(h, recipe.matches(grid(stack(Items.REDSTONE, 64), finder(1)), level), "a stack is one slot");
        check(h, !recipe.matches(grid(finder(0), stack(Items.REDSTONE, 1)), level), "a charged finder");
        check(h, !recipe.matches(grid(finder(10)), level), "no redstone");
        check(h, !recipe.matches(grid(finder(10), stack(Items.REDSTONE, 1), stack(Items.REDSTONE, 1)), level),
                "two redstone slots");
        check(h, !recipe.matches(grid(finder(10), finder(5), stack(Items.REDSTONE, 1)), level), "two finders");
        check(h, !recipe.matches(grid(finder(10), stack(Items.REDSTONE_BLOCK, 1)), level), "a redstone block");
        check(h, !recipe.matches(grid(finder(10), stack(Items.REDSTONE, 1), stack(Items.STICK, 1)), level),
                "something else");
        check(h, recipe.matches(CraftingInput.of(2, 2, List.of(finder(3), stack(Items.REDSTONE, 1),
                ItemStack.EMPTY, ItemStack.EMPTY)), level), "the inventory's 2x2 grid");
        ItemStack out = recipe.assemble(grid(finder(7), stack(Items.REDSTONE, 1)));
        check(h, out.is(ModItems.RANGEFINDER.get()) && out.getDamageValue() == 0, "fully charged: " + out);
        h.succeed();
    }

    /** The recipe files load with their serializers, and the recipe manager picks them. */
    private static void loaded(GameTestHelper h) {
        var manager = h.getLevel().getServer().getRecipeManager();
        var fuel = manager.getRecipeFor(RecipeType.CRAFTING, grid(can(FULL), coal()), h.getLevel());
        check(h, fuel.isPresent() && fuel.get().value() == FuelRefillRecipe.INSTANCE, "fuel refill found: " + fuel);
        check(h, fuel.get().id().identifier().toString().equals("mcheli_ce_port:charge_fuel"), "upstream's name");
        var finder = manager.getRecipeFor(RecipeType.CRAFTING, grid(finder(10), stack(Items.REDSTONE, 1)),
                h.getLevel());
        check(h, finder.isPresent() && finder.get().value() == RangeFinderRechargeRecipe.INSTANCE,
                "rangefinder recharge found: " + finder);
        check(h, manager.getRecipeFor(RecipeType.CRAFTING, grid(can(0), coal()), h.getLevel()).isEmpty(),
                "a full can and coal craft nothing");
        h.succeed();
    }

    private static CraftingMenu table(GameTestHelper h, ServerPlayer player, ItemStack... stacks) {
        CraftingMenu menu = new CraftingMenu(1, player.getInventory(),
                ContainerLevelAccess.create(h.getLevel(), h.absolutePos(BlockPos.ZERO)));
        player.containerMenu = menu;
        for (int i = 0; i < stacks.length; i++) {
            menu.getSlot(1 + i).set(stacks[i]);
        }
        return menu;
    }

    private static ServerPlayer crafter(GameTestHelper h) {
        @SuppressWarnings("removal")
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    /** Taking the result in a crafting table uses the can and one coal or charcoal per slot. */
    private static void craftingTableFuel(GameTestHelper h) {
        ServerPlayer player = crafter(h);
        CraftingMenu menu = table(h, player, can(FULL), stack(Items.COAL, 5), stack(Items.CHARCOAL, 3));
        ItemStack shown = menu.getSlot(0).getItem();
        check(h, shown.is(ModItems.FUEL.get()) && shown.getDamageValue() == FULL - 175, "result shown: " + shown);
        menu.clicked(0, 0, ContainerInput.PICKUP, player);
        ItemStack carried = menu.getCarried();
        check(h, carried.is(ModItems.FUEL.get()) && carried.getDamageValue() == FULL - 175, "crafted: " + carried);
        check(h, menu.getSlot(1).getItem().isEmpty(), "the can was used");
        check(h, menu.getSlot(2).getItem().is(Items.COAL) && menu.getSlot(2).getItem().getCount() == 4,
                "one coal used: " + menu.getSlot(2).getItem());
        check(h, menu.getSlot(3).getItem().is(Items.CHARCOAL) && menu.getSlot(3).getItem().getCount() == 2,
                "one charcoal used: " + menu.getSlot(3).getItem());
        check(h, menu.getSlot(0).getItem().isEmpty(), "coal alone crafts nothing");
        // Put the refilled can back: it refills again with the remaining coal and charcoal.
        menu.clicked(1, 0, ContainerInput.PICKUP, player);
        ItemStack again = menu.getSlot(0).getItem();
        check(h, again.getDamageValue() == FULL - 350, "refilled twice: " + again);
        player.discard();
        h.succeed();
    }

    private static void craftingTableRangeFinder(GameTestHelper h) {
        ServerPlayer player = crafter(h);
        CraftingMenu menu = table(h, player, finder(10), stack(Items.REDSTONE, 2));
        menu.quickMoveStack(player, 0);
        check(h, player.getInventory().contains(s -> s.is(ModItems.RANGEFINDER.get()) && s.getDamageValue() == 0),
                "a charged rangefinder went to the inventory");
        check(h, menu.getSlot(1).getItem().isEmpty(), "the used finder was consumed");
        check(h, menu.getSlot(2).getItem().getCount() == 1, "one redstone used: " + menu.getSlot(2).getItem());
        check(h, menu.getSlot(0).getItem().isEmpty(), "redstone alone crafts nothing");
        player.discard();
        h.succeed();
    }

    /** Every example JEI shows is exactly what the recipe makes. */
    private static void examples(GameTestHelper h) {
        List<Example> fuel = FuelRefillRecipe.examples();
        check(h, fuel.size() == 4, "one and enough coal, one and enough charcoal: " + fuel.size());
        check(h, fuel.get(1).output().getDamageValue() == 0 && fuel.get(1).inputs().size() == 7, "six coal fill");
        check(h, fuel.get(3).output().getDamageValue() == 0 && fuel.get(3).inputs().size() == 9, "eight charcoal");
        List<Example> all = new ArrayList<>(fuel);
        all.addAll(RangeFinderRechargeRecipe.examples());
        var manager = h.getLevel().getServer().getRecipeManager();
        for (Example example : all) {
            CraftingInput input = grid(example.inputs().toArray(ItemStack[]::new));
            var recipe = manager.getRecipeFor(RecipeType.CRAFTING, input, h.getLevel());
            check(h, recipe.isPresent(), "example matches a recipe: " + example.inputs());
            ItemStack made = recipe.get().value().assemble(input);
            check(h, ItemStack.matches(made, example.output()), "example output " + example.output() + " is " + made);
        }
        h.succeed();
    }
}
