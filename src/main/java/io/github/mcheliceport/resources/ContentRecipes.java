package io.github.mcheliceport.resources;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.content.ContentLocation;
import io.github.mcheliceport.content.ContentRegistries;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.content.legacy.LegacyItemNames;
import io.github.mcheliceport.content.throwable.ThrowableInfo;
import io.github.mcheliceport.registry.ModItems;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Crafting recipes for content items, from their {@code AddRecipe}/{@code AddShapelessRecipe} lines
 * (TXT) or {@code Recepie} block (YAML). Ported from upstream {@code MCH_ItemRecipe}, which turned the
 * same lines into 1.12 recipes; here they become recipe JSON in a generated data pack.
 *
 * <p>The line syntax is upstream's: shaped recipes are an optional result count, up to three quoted
 * pattern rows, then pairs of a key letter and a 1.12 item name with optional metadata
 * ({@code "XXX", " X ", "III", X, iron_block, I, iron_ingot}); shapeless recipes are a list of item
 * names with optional metadata. Item names go through {@link LegacyItemNames}; names in the
 * {@code mcheli} namespace refer to the port's items. A recipe with an item that has no
 * equivalent is skipped and logged, where upstream silently left that slot empty. Both parsers
 * upper-case recipe lines, so names are lower-cased here again.
 */
public final class ContentRecipes {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private ContentRecipes() {
    }

    /** Whether an item exists, so this class can be tested without the item registry. */
    @FunctionalInterface
    public interface ItemLookup {

        boolean exists(Identifier id);
    }

    /** A 1.12 item reference from a recipe line. */
    public record LegacyIngredient(String name, int meta) {
    }

    /** A recipe line, parsed but not yet resolved to items. */
    public record ParsedRecipe(boolean shaped, int count, List<String> pattern, Map<Character, LegacyIngredient> key,
            List<LegacyIngredient> ingredients) {
    }

    /** The result of {@link #convert}: a recipe JSON, or why there is none. */
    public record Conversion(@Nullable JsonObject recipe, @Nullable String error) {

        static Conversion ok(JsonObject recipe) {
            return new Conversion(recipe, null);
        }

        static Conversion fail(String error) {
            return new Conversion(null, error);
        }
    }

    /**
     * Recipe files for all content items that have recipes, keyed as
     * {@code mcheli_ce_port:recipe/content/<item>[_<n>].json}.
     */
    public static Map<Identifier, byte[]> build(List<ModItems.ContentItem> items, ContentRegistries.Snapshot content,
            ItemLookup lookup) {
        Map<Identifier, byte[]> files = new LinkedHashMap<>();
        int skipped = 0;
        for (ModItems.ContentItem item : items) {
            RecipeSource source = recipeSource(item, content);
            if (source == null) {
                continue;
            }
            int n = 0;
            for (String line : source.lines()) {
                if (line.length() < 3) {
                    continue;
                }
                Conversion conversion = convert(line, source.shaped(), item.id(), lookup);
                if (conversion.recipe() == null) {
                    LOGGER.warn("Skipping a recipe of {} {}: {} (recipe: {})", item.type().typeName,
                            item.contentName(), conversion.error(), line);
                    skipped++;
                    continue;
                }
                n++;
                String path = "recipe/content/" + item.id().getPath() + (n > 1 ? "_" + n : "") + ".json";
                files.put(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, path),
                        (GSON.toJson(conversion.recipe()) + "\n").getBytes(StandardCharsets.UTF_8));
            }
        }
        if (!files.isEmpty() || skipped > 0) {
            LOGGER.info("Generated {} content recipes ({} skipped)", files.size(), skipped);
        }
        return files;
    }

    private record RecipeSource(List<String> lines, boolean shaped) {
    }

    private static @Nullable RecipeSource recipeSource(ModItems.ContentItem item, ContentRegistries.Snapshot content) {
        return switch (item.type()) {
            case THROWABLE -> {
                ThrowableInfo info = content.throwable().get(item.contentName());
                yield info == null ? null : new RecipeSource(info.recipeString, info.isShapedRecipe);
            }
            case HELICOPTER, PLANE, SHIP, TANK, VEHICLE -> {
                AircraftInfo info = switch (item.type()) {
                    case HELICOPTER -> content.heli().get(item.contentName());
                    case PLANE -> content.plane().get(item.contentName());
                    case SHIP -> content.ship().get(item.contentName());
                    case TANK -> content.tank().get(item.contentName());
                    default -> content.vehicle().get(item.contentName());
                };
                yield info == null ? null : new RecipeSource(info.recipeString, info.isShapedRecipe);
            }
            default -> null;
        };
    }

    /** Converts one recipe line to recipe JSON with {@code result} as the crafted item. */
    public static Conversion convert(String line, boolean shaped, Identifier result, ItemLookup lookup) {
        ParsedRecipe recipe;
        try {
            recipe = shaped ? parseShaped(line) : parseShapeless(line);
        } catch (IllegalArgumentException e) {
            return Conversion.fail(e.getMessage());
        }
        JsonObject json = new JsonObject();
        if (recipe.shaped()) {
            json.addProperty("type", "minecraft:crafting_shaped");
            json.addProperty("category", "misc");
            JsonObject key = new JsonObject();
            for (Map.Entry<Character, LegacyIngredient> entry : recipe.key().entrySet()) {
                String id = resolve(entry.getValue(), lookup);
                if (id == null) {
                    return Conversion.fail("no item for '" + describe(entry.getValue()) + "'");
                }
                key.addProperty(String.valueOf(entry.getKey()), id);
            }
            json.add("key", key);
            JsonArray pattern = new JsonArray();
            recipe.pattern().forEach(pattern::add);
            json.add("pattern", pattern);
        } else {
            json.addProperty("type", "minecraft:crafting_shapeless");
            json.addProperty("category", "misc");
            JsonArray ingredients = new JsonArray();
            for (LegacyIngredient ingredient : recipe.ingredients()) {
                String id = resolve(ingredient, lookup);
                if (id == null) {
                    return Conversion.fail("no item for '" + describe(ingredient) + "'");
                }
                ingredients.add(id);
            }
            json.add("ingredients", ingredients);
        }
        JsonObject resultJson = new JsonObject();
        resultJson.addProperty("id", result.toString());
        resultJson.addProperty("count", recipe.count());
        json.add("result", resultJson);
        return Conversion.ok(json);
    }

    /** Parses a shaped recipe line as upstream's {@code MCH_ItemRecipe#addShapedRecipe}. */
    public static ParsedRecipe parseShaped(String line) {
        String[] s = line.split("\\s*,\\s*");
        if (s.length < 3) {
            throw new IllegalArgumentException("too few values");
        }
        int idx = 0;
        int count = 1;
        if (isNumber(s[0])) {
            idx = 1;
            count = Math.max(1, Math.min(99, Integer.parseInt(s[0])));
        }
        List<String> pattern = new ArrayList<>();
        while (pattern.size() < 3 && idx < s.length && isQuoted(s[idx])) {
            pattern.add(s[idx].substring(1, s[idx].length() - 1).toUpperCase(Locale.ROOT));
            idx++;
        }
        if (pattern.isEmpty()) {
            throw new IllegalArgumentException("no pattern rows");
        }
        int width = pattern.getFirst().length();
        if (width == 0 || width > 3 || pattern.stream().anyMatch(row -> row.length() != width)) {
            throw new IllegalArgumentException("pattern rows must have the same width, 1 to 3");
        }
        Set<Character> used = new HashSet<>();
        pattern.forEach(row -> row.chars().filter(c -> c != ' ').forEach(c -> used.add((char) c)));

        Map<Character, LegacyIngredient> key = new LinkedHashMap<>();
        while (idx < s.length) {
            String symbol = s[idx++];
            if (symbol.length() != 1 || !Character.isLetter(symbol.charAt(0))) {
                throw new IllegalArgumentException("expected a key letter, found '" + symbol + "'");
            }
            char c = Character.toUpperCase(symbol.charAt(0));
            if (idx >= s.length || s[idx].isEmpty()) {
                throw new IllegalArgumentException("key '" + c + "' has no item");
            }
            String name = s[idx++].trim().toLowerCase(Locale.ROOT);
            int meta = 0;
            if (idx < s.length && isNumber(s[idx])) {
                meta = Integer.parseInt(s[idx++]);
            }
            if (isNumber(name)) {
                throw new IllegalArgumentException("numeric item IDs are not supported in shaped recipes");
            }
            if (!used.contains(c)) {
                // Upstream warned and dropped keys the pattern does not use.
                LOGGER.debug("Recipe key '{}' is not used in the pattern: {}", c, line);
                continue;
            }
            key.put(c, new LegacyIngredient(name, meta));
        }
        for (char c : used) {
            if (!key.containsKey(c)) {
                throw new IllegalArgumentException("pattern letter '" + c + "' has no key");
            }
        }
        return new ParsedRecipe(true, count, List.copyOf(pattern), key, List.of());
    }

    /** Parses a shapeless recipe line as upstream's {@code MCH_ItemRecipe#addShapelessRecipe}. */
    public static ParsedRecipe parseShapeless(String line) {
        String[] s = line.split("\\s*,\\s*");
        List<LegacyIngredient> ingredients = new ArrayList<>();
        for (int idx = 0; idx < s.length; idx++) {
            if (s[idx].isEmpty()) {
                throw new IllegalArgumentException("empty item name");
            }
            String name = s[idx].trim().toLowerCase(Locale.ROOT);
            int meta = 0;
            if (idx + 1 < s.length && isNumber(s[idx + 1])) {
                meta = Integer.parseInt(s[++idx]);
            }
            if (isNumber(name)) {
                throw new IllegalArgumentException("numeric item IDs (" + name + ") are not supported");
            }
            ingredients.add(new LegacyIngredient(name, meta));
        }
        if (ingredients.isEmpty() || ingredients.size() > 9) {
            throw new IllegalArgumentException("a shapeless recipe needs 1 to 9 items");
        }
        return new ParsedRecipe(false, 1, List.of(), Map.of(), List.copyOf(ingredients));
    }

    /** The flattened item ID of a 1.12 item reference, or null if there is none. */
    public static @Nullable String resolve(LegacyIngredient ingredient, ItemLookup lookup) {
        String name = ingredient.name();
        if (name.startsWith(ContentLocation.NAMESPACE + ":")) {
            String path = ModItems.toItemPath(name.substring(ContentLocation.NAMESPACE.length() + 1));
            Identifier id = Identifier.tryBuild(McheliCePort.MOD_ID, path);
            return id != null && lookup.exists(id) ? id.toString() : null;
        }
        String flattened = LegacyItemNames.flatten(name, ingredient.meta());
        Identifier id = flattened == null ? null : Identifier.tryParse(flattened);
        if (id == null || !lookup.exists(id)) {
            return null;
        }
        // Metadata on an item without variants was a damage value, which recipes no longer check.
        return id.toString();
    }

    private static String describe(LegacyIngredient ingredient) {
        return ingredient.meta() == 0 ? ingredient.name() : ingredient.name() + ", " + ingredient.meta();
    }

    private static boolean isQuoted(String s) {
        return s.length() >= 2 && s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"';
    }

    /** Upstream's {@code isNumber}: only ASCII digits. */
    static boolean isNumber(@Nullable String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }
}
