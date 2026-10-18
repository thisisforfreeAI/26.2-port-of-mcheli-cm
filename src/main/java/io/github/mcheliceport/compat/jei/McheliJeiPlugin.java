package io.github.mcheliceport.compat.jei;

import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.crafting.Example;
import io.github.mcheliceport.crafting.FuelRefillRecipe;
import io.github.mcheliceport.crafting.RangeFinderRechargeRecipe;
import io.github.mcheliceport.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;

/**
 * Optional Just Enough Items support. JEI finds this class by its annotation and loads it only
 * when JEI is installed; nothing else in the mod refers to JEI.
 *
 * <p>The fixed recipes and the content recipes are ordinary shaped and shapeless crafting recipes,
 * which JEI lists by itself. It skips special recipes, so the fuel refill and rangefinder recharge
 * are shown as example crafting grids, each made by the real recipe, plus an information page
 * that explains the variable fuel refill. The drafting table is shown as a crafting station.
 */
@JeiPlugin
public final class McheliJeiPlugin implements IModPlugin {

    private static final Identifier UID = Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "jei_plugin");

    @Override
    public Identifier getPluginUid() {
        return UID;
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        List<RecipeHolder<CraftingRecipe>> recipes = new ArrayList<>();
        addExamples(recipes, "charge_fuel", FuelRefillRecipe.examples());
        addExamples(recipes, "charge_power_range_finder", RangeFinderRechargeRecipe.examples());
        registration.addRecipes(RecipeTypes.CRAFTING, recipes);
        registration.addItemStackInfo(new ItemStack(ModItems.FUEL.get()),
                Component.translatable("jei.mcheli_ce_port.info.charge_fuel",
                        FuelRefillRecipe.COAL_FUEL, FuelRefillRecipe.CHARCOAL_FUEL,
                        ModItems.FUEL.get().getDefaultInstance().getMaxDamage()));
        registration.addItemStackInfo(new ItemStack(ModItems.RANGEFINDER.get()),
                Component.translatable("jei.mcheli_ce_port.info.charge_power_range_finder"));
    }

    /**
     * The drafting table makes the port's crafting recipes (all of them ordinary crafting
     * recipes), so JEI lists it beside the crafting table for them.
     */
    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addCraftingStation(RecipeTypes.CRAFTING, ModItems.DRAFTING_TABLE.get());
    }

    private static void addExamples(List<RecipeHolder<CraftingRecipe>> recipes, String name, List<Example> examples) {
        for (int i = 0; i < examples.size(); i++) {
            Example example = examples.get(i);
            List<Ingredient> ingredients = example.inputs().stream().map(McheliJeiPlugin::ingredient).toList();
            ShapelessRecipe recipe = new ShapelessRecipe(new Recipe.CommonInfo(false),
                    new CraftingRecipe.CraftingBookInfo(CraftingBookCategory.MISC, name),
                    ItemStackTemplate.fromNonEmptyStack(example.output()), ingredients);
            Identifier id = Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "jei/" + name + "_" + (i + 1));
            recipes.add(new RecipeHolder<>(ResourceKey.create(Registries.RECIPE, id), recipe));
        }
    }

    /** The stack as shown, damage included; a plain item when it has no changed data. */
    private static Ingredient ingredient(ItemStack stack) {
        DataComponentPatch patch = stack.getComponentsPatch();
        return patch.isEmpty() ? Ingredient.of(stack.getItem()) : DataComponentIngredient.of(false, stack);
    }
}
