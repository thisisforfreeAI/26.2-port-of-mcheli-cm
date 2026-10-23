package io.github.mcheliceport.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import io.github.mcheliceport.content.ContentRegistries;
import io.github.mcheliceport.content.ContentTestSupport;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.content.legacy.LegacyItemNames;
import io.github.mcheliceport.registry.ModItems;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.crafting.Recipe;
import org.junit.jupiter.api.Test;

/**
 * Content recipes: the 1.12 item name table, upstream's recipe line syntax, and every recipe of
 * upstream's legacy TXT stock content converted and decoded with the game's recipe codec.
 */
class ContentRecipesTest {

    private static final ContentRecipes.ItemLookup GAME_ITEMS = BuiltInRegistries.ITEM::containsKey;
    private static final Identifier RESULT = Identifier.fromNamespaceAndPath("mcheli_ce_port", "ah-64");

    @Test
    void flattensLegacyItemNames() {
        assertEquals("minecraft:white_wool", LegacyItemNames.flatten("wool", 0));
        assertEquals("minecraft:light_blue_wool", LegacyItemNames.flatten("wool", 3));
        assertEquals("minecraft:red_dye", LegacyItemNames.flatten("dye", 1));
        assertEquals("minecraft:lapis_lazuli", LegacyItemNames.flatten("dye", 4));
        assertEquals("minecraft:bone_meal", LegacyItemNames.flatten("dye", 15));
        assertEquals("minecraft:birch_planks", LegacyItemNames.flatten("planks", 2));
        assertEquals("minecraft:spruce_log", LegacyItemNames.flatten("log", 5), "upper bits are orientation");
        assertEquals("minecraft:charcoal", LegacyItemNames.flatten("coal", 1));
        assertEquals("minecraft:cod", LegacyItemNames.flatten("fish", 0));
        assertEquals("minecraft:oak_boat", LegacyItemNames.flatten("boat", 0));
        assertEquals("minecraft:iron_ingot", LegacyItemNames.flatten("minecraft:iron_ingot", 0));
        assertEquals("minecraft:music_disc_cat", LegacyItemNames.flatten("record_cat", 0));
        assertNull(LegacyItemNames.flatten("wool", 16));
        assertNull(LegacyItemNames.flatten("potion", 8193), "potion types are item data now");
        assertEquals("minecraft:potion", LegacyItemNames.flatten("potion", 0));
        assertEquals("othermod:thing", LegacyItemNames.flatten("othermod:thing", 3));
    }

    @Test
    void parsesUpstreamRecipeSyntax() {
        ContentRecipes.ParsedRecipe shaped = ContentRecipes.parseShaped(
                "2, \"X X\",  \"XYX\", \" Z \", X, iron_ingot, Y, dye, 4, Z, redstone, Q, unused");
        assertEquals(2, shaped.count());
        assertEquals(List.of("X X", "XYX", " Z "), shaped.pattern());
        assertEquals(List.of('X', 'Y', 'Z'), new ArrayList<>(shaped.key().keySet()), "unused keys are dropped");
        assertEquals(new ContentRecipes.LegacyIngredient("dye", 4), shaped.key().get('Y'));

        assertThrows(IllegalArgumentException.class, () -> ContentRecipes.parseShaped("\"XX\", \"X\", X, stone"));
        assertThrows(IllegalArgumentException.class, () -> ContentRecipes.parseShaped("\"XY\", X, stone, Q, stone"));
        assertThrows(IllegalArgumentException.class, () -> ContentRecipes.parseShaped("\"X\", X, 1, Y, 2"));
        assertEquals(2, ContentRecipes.parseShapeless("IRON_BLOCK,IRON_BLOCK").ingredients().size());
        assertThrows(IllegalArgumentException.class, () -> ContentRecipes.parseShapeless("1, 2"));
    }

    @Test
    void convertsToRecipeJson() {
        ContentRecipes.Conversion conversion = ContentRecipes.convert(
                "\"XXX\", \" X \", \"III\", X, IRON_BLOCK, I, WOOL, 14", true, RESULT, GAME_ITEMS);
        JsonObject json = conversion.recipe();
        assertNotNull(json, conversion.error());
        assertEquals("minecraft:crafting_shaped", json.get("type").getAsString());
        assertEquals("minecraft:red_wool", json.getAsJsonObject("key").get("I").getAsString());
        assertEquals("mcheli_ce_port:ah-64", json.getAsJsonObject("result").get("id").getAsString());
        assertDecodes(json);

        assertNull(ContentRecipes.convert("\"X\", X, POTION, 8193", true, RESULT, GAME_ITEMS).recipe(),
                "1.12 potions by metadata have no equivalent");
        assertNull(ContentRecipes.convert("\"X\", X, NO_SUCH_ITEM", true, RESULT, GAME_ITEMS).recipe());
    }

    /** Every recipe of upstream's legacy TXT stock content, against the real item registry. */
    @Test
    void convertsAllLegacyStockRecipes() {
        ContentRegistries.Snapshot legacy = ContentTestSupport.load(ContentTestSupport.legacyPack());
        int lines = 0;
        for (AircraftInfo info : legacy.aircraft()) {
            lines += (int) info.recipeString.stream().filter(s -> s.length() >= 3).count();
        }
        for (var info : legacy.throwable().values()) {
            lines += (int) info.recipeString.stream().filter(s -> s.length() >= 3).count();
        }

        Map<Identifier, byte[]> files = ContentRecipes.build(ModItems.contentItems(), legacy, GAME_ITEMS);
        List<String> failures = new ArrayList<>();
        for (AircraftInfo info : legacy.aircraft()) {
            for (String line : info.recipeString) {
                Identifier result = Identifier.fromNamespaceAndPath("mcheli_ce_port", ModItems.toItemPath(info.getName()));
                if (ContentRecipes.convert(line, info.isShapedRecipe, result, GAME_ITEMS).recipe() == null) {
                    failures.add(info.getName() + ": " + line);
                }
            }
        }
        // Three stock recipes cannot be converted. One names a 1.12 potion by metadata; the other two
        // use a pattern letter without a key, which 1.12 rejected as well.
        assertEquals(List.of("pzl-m18", "robinson_r44", "us-2"),
                failures.stream().map(f -> f.substring(0, f.indexOf(':'))).sorted().toList(),
                String.join("\n", failures));
        assertEquals(lines - failures.size(), files.size());
        for (byte[] file : files.values()) {
            assertDecodes(JsonParser.parseString(new String(file, StandardCharsets.UTF_8)));
        }
    }

    /** Exercise the shipped YAML, rather than only the legacy fixtures that retained recipes. */
    @Test
    void shippedStockRecipesArePresentAndDecode() {
        ContentRegistries.Snapshot stock = ContentTestSupport.load(ContentTestSupport.stockPack());
        assertEquals(List.of("ammo_box", "fuel_truck"), stock.aircraft().stream()
                .filter(info -> info.recipeString.isEmpty()).map(AircraftInfo::getName).sorted().toList(),
                "only the infinite supply props deliberately have no survival recipe");
        assertTrue(stock.throwable().values().stream().allMatch(info -> !info.recipeString.isEmpty()));
        Map<Identifier, byte[]> files = ContentRecipes.build(ModItems.contentItems(), stock, GAME_ITEMS);
        assertEquals(119, files.size(), "115 vehicle recipes and four throwable recipes, with none skipped");
        for (byte[] file : files.values()) {
            assertDecodes(JsonParser.parseString(new String(file, StandardCharsets.UTF_8)));
        }
        JsonObject apache = JsonParser.parseString(new String(files.get(Identifier.fromNamespaceAndPath(
                "mcheli_ce_port", "recipe/content/ah-64.json")), StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("minecraft:iron_block", apache.getAsJsonObject("key").get("X").getAsString());
        assertEquals(List.of("X X", " X ", "X X"), apache.getAsJsonArray("pattern").asList().stream()
                .map(JsonElement::getAsString).toList());
    }

    private static void assertDecodes(JsonElement json) {
        // Ingredients name items, so decoding needs the item registry.
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE,
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        DataResult<Recipe<?>> result = Recipe.CODEC.parse(ops, json);
        assertTrue(result.isSuccess(), () -> result.error().orElseThrow().message() + "\n" + json);
    }
}
