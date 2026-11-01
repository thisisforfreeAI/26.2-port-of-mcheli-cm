package io.github.mcheliceport.crafting;

import io.github.mcheliceport.registry.ModItems;
import io.github.mcheliceport.registry.ModRecipeSerializers;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Recharges a used rangefinder with redstone, ported from upstream
 * {@code MCH_RecipeReloadRangeFinder} ({@code mcheli:charge_power_range_finder}). The grid holds
 * exactly one damaged rangefinder and one slot of redstone dust, and nothing else; the result is a
 * fully charged rangefinder. Upstream's {@code canFit} (2×2) was only used by the recipe book.
 */
public final class RangeFinderRechargeRecipe extends CustomRecipe {

    public static final RangeFinderRechargeRecipe INSTANCE = new RangeFinderRechargeRecipe();

    private RangeFinderRechargeRecipe() {
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return matches(input.items());
    }

    /** {@link #matches(CraftingInput, Level)} on the grid's stacks, empty ones included. */
    public static boolean matches(List<ItemStack> grid) {
        int finders = 0;
        int redstone = 0;
        for (ItemStack stack : grid) {
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.is(ModItems.RANGEFINDER.get())) {
                if (stack.getDamageValue() == 0 || ++finders > 1) {
                    return false;
                }
            } else if (!stack.is(Items.REDSTONE) || ++redstone > 1) {
                return false;
            }
        }
        return finders == 1 && redstone == 1;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        return new ItemStack(ModItems.RANGEFINDER.get());
    }

    /** The example grid for recipe viewers: a used-up rangefinder and redstone. */
    public static List<Example> examples() {
        ItemStack used = new ItemStack(ModItems.RANGEFINDER.get());
        used.setDamageValue(used.getMaxDamage());
        return List.of(new Example(List.of(used, new ItemStack(Items.REDSTONE)),
                new ItemStack(ModItems.RANGEFINDER.get())));
    }

    @Override
    public RecipeSerializer<RangeFinderRechargeRecipe> getSerializer() {
        return ModRecipeSerializers.CHARGE_POWER_RANGE_FINDER.get();
    }
}
