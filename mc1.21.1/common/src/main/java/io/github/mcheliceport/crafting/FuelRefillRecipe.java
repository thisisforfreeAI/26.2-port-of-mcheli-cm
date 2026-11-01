package io.github.mcheliceport.crafting;

import io.github.mcheliceport.registry.ModItems;
import io.github.mcheliceport.registry.ModRecipeSerializers;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Refills a used fuel can with coal or charcoal, ported from upstream {@code MCH_RecipeFuel}
 * ({@code mcheli:charge_fuel}). The grid holds exactly one damaged fuel can and at least one slot
 * of coal or charcoal, and nothing else. Each coal slot restores {@value #COAL_FUEL} fuel and each
 * charcoal slot {@value #CHARCOAL_FUEL}, up to a full can; a stack in a slot counts once and loses
 * one item, as with any crafting ingredient.
 *
 * <p>Upstream's {@code canFit} asked for a 3×3 grid, but 1.12 only used it for the recipe book,
 * so the refill also worked in the inventory's 2×2 grid. 26.2 recipes have no grid size check and
 * the port keeps that behaviour.
 */
public final class FuelRefillRecipe extends CustomRecipe {

    public static final FuelRefillRecipe INSTANCE = new FuelRefillRecipe();
    public static final int COAL_FUEL = 100;
    public static final int CHARCOAL_FUEL = 75;

    private FuelRefillRecipe() {
    }

    /** The fuel one slot of {@code stack} restores, or 0 if it is not coal or charcoal. */
    public static int fuelOf(ItemStack stack) {
        if (stack.is(Items.COAL)) {
            return COAL_FUEL;
        }
        return stack.is(Items.CHARCOAL) ? CHARCOAL_FUEL : 0;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return matches(input.items());
    }

    /** {@link #matches(CraftingInput, Level)} on the grid's stacks, empty ones included. */
    public static boolean matches(List<ItemStack> grid) {
        int cans = 0;
        int coal = 0;
        for (ItemStack stack : grid) {
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.is(ModItems.FUEL.get())) {
                if (stack.getDamageValue() == 0 || ++cans > 1) {
                    return false;
                }
            } else if (fuelOf(stack) > 0) {
                coal++;
            } else {
                return false;
            }
        }
        return cans == 1 && coal > 0;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        return assemble(input.items());
    }

    /** The refilled can for a grid that {@linkplain #matches(List) matches}. */
    public static ItemStack assemble(List<ItemStack> grid) {
        int damage = 0;
        for (ItemStack stack : grid) {
            if (stack.is(ModItems.FUEL.get())) {
                damage = stack.getDamageValue();
                break;
            }
        }
        for (ItemStack stack : grid) {
            damage = Math.max(0, damage - fuelOf(stack));
        }
        return can(damage);
    }

    /** A fuel can with {@code damage} fuel missing; upstream's output drops other data, as here. */
    public static ItemStack can(int damage) {
        ItemStack can = new ItemStack(ModItems.FUEL.get());
        can.setDamageValue(damage);
        return can;
    }

    /** Example grids for recipe viewers: an empty can with one or enough coal or charcoal. */
    public static List<Example> examples() {
        ItemStack empty = can(ModItems.FUEL.get().getDefaultInstance().getMaxDamage());
        List<Example> examples = new ArrayList<>();
        for (ItemStack fuel : List.of(new ItemStack(Items.COAL), new ItemStack(Items.CHARCOAL))) {
            int perSlot = fuelOf(fuel);
            int toFill = Math.min(8, (empty.getDamageValue() + perSlot - 1) / perSlot);
            for (int slots : toFill > 1 ? new int[] {1, toFill} : new int[] {1}) {
                List<ItemStack> grid = new ArrayList<>();
                grid.add(empty.copy());
                grid.addAll(Collections.nCopies(slots, fuel));
                examples.add(new Example(grid, assemble(grid)));
            }
        }
        return examples;
    }

    @Override
    public RecipeSerializer<FuelRefillRecipe> getSerializer() {
        return ModRecipeSerializers.CHARGE_FUEL.get();
    }
}
