package io.github.mcheliceport.crafting;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import org.junit.jupiter.api.Test;

/**
 * The special recipe files decode with the game's recipe codec to the ported recipes. Their
 * behaviour needs item stacks, which need a running server, so it is tested in
 * {@code CraftingGameTests}.
 */
class SpecialRecipesTest {

    @Test
    void recipeFilesDecodeToTheSpecialRecipes() throws IOException {
        assertSame(FuelRefillRecipe.INSTANCE, decode("charge_fuel"));
        assertSame(RangeFinderRechargeRecipe.INSTANCE, decode("charge_power_range_finder"));
        assertTrue(FuelRefillRecipe.INSTANCE.isSpecial(), "kept out of the recipe book, as upstream");
        assertTrue(RangeFinderRechargeRecipe.INSTANCE.isSpecial());
    }

    private static Recipe<?> decode(String name) throws IOException {
        String path = "data/mcheli_ce_port/recipe/" + name + ".json";
        String text;
        try (var in = SpecialRecipesTest.class.getClassLoader().getResourceAsStream(path)) {
            assertTrue(in != null, path);
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Recipe<?> recipe = Recipe.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(text)).getOrThrow();
        assertTrue(recipe instanceof CraftingRecipe);
        return recipe;
    }
}
