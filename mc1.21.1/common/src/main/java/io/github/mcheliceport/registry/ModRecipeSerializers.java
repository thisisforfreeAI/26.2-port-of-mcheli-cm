package io.github.mcheliceport.registry;

import com.mojang.serialization.MapCodec;
import io.github.mcheliceport.crafting.FuelRefillRecipe;
import io.github.mcheliceport.crafting.RangeFinderRechargeRecipe;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.RecipeSerializer;
import io.github.mcheliceport.platform.registry.DeferredHolder;

/**
 * Serializers of the special crafting recipes, named after upstream's recipe IDs
 * ({@code MCH_ItemRecipe}). They take no fields; the recipe JSON is just the type.
 */
public final class ModRecipeSerializers {

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<FuelRefillRecipe>> CHARGE_FUEL =
            ModRegistries.RECIPE_SERIALIZERS.register("charge_fuel", () -> serializer(FuelRefillRecipe.INSTANCE));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<RangeFinderRechargeRecipe>>
            CHARGE_POWER_RANGE_FINDER = ModRegistries.RECIPE_SERIALIZERS.register("charge_power_range_finder",
                    () -> serializer(RangeFinderRechargeRecipe.INSTANCE));

    private ModRecipeSerializers() {
    }

    /** A serializer for a recipe with no fields (26.2's {@code new RecipeSerializer<>(codec, streamCodec)}). */
    private static <R extends net.minecraft.world.item.crafting.Recipe<?>> RecipeSerializer<R> serializer(R instance) {
        MapCodec<R> codec = MapCodec.unit(instance);
        StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, R> stream = StreamCodec.unit(instance);
        return new RecipeSerializer<>() {
            @Override
            public MapCodec<R> codec() {
                return codec;
            }

            @Override
            public StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, R> streamCodec() {
                return stream;
            }
        };
    }

    static void init() {
    }
}
