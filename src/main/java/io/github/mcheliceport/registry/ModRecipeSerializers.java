package io.github.mcheliceport.registry;

import com.mojang.serialization.MapCodec;
import io.github.mcheliceport.crafting.FuelRefillRecipe;
import io.github.mcheliceport.crafting.RangeFinderRechargeRecipe;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * Serializers of the special crafting recipes, named after upstream's recipe IDs
 * ({@code MCH_ItemRecipe}). They take no fields; the recipe JSON is just the type.
 */
public final class ModRecipeSerializers {

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<FuelRefillRecipe>> CHARGE_FUEL =
            ModRegistries.RECIPE_SERIALIZERS.register("charge_fuel", () -> new RecipeSerializer<>(
                    MapCodec.unit(FuelRefillRecipe.INSTANCE), StreamCodec.unit(FuelRefillRecipe.INSTANCE)));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<RangeFinderRechargeRecipe>>
            CHARGE_POWER_RANGE_FINDER = ModRegistries.RECIPE_SERIALIZERS.register("charge_power_range_finder",
                    () -> new RecipeSerializer<>(MapCodec.unit(RangeFinderRechargeRecipe.INSTANCE),
                            StreamCodec.unit(RangeFinderRechargeRecipe.INSTANCE)));

    private ModRecipeSerializers() {
    }

    static void init() {
    }
}
