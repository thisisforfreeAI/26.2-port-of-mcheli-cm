package io.github.mcheliceport.crafting;

import java.util.List;
import net.minecraft.world.item.ItemStack;

/**
 * One crafting grid of a special recipe and what it makes, shown by recipe viewers such as JEI,
 * which cannot list special recipes themselves. Tests check each output against the recipe.
 *
 * @param inputs the occupied slots, in order
 * @param output the crafted item
 */
public record Example(List<ItemStack> inputs, ItemStack output) {
}
