package io.github.mcheliceport.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.mcheliceport.content.ContentRegistries;
import io.github.mcheliceport.content.ContentTestSupport;
import io.github.mcheliceport.item.AircraftItem;
import io.github.mcheliceport.item.ThrowableItem;
import io.github.mcheliceport.registry.ModItems;
import io.github.mcheliceport.registry.ModRegistries;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.junit.jupiter.api.Test;

/**
 * Every item of the port has an item model whose textures exist and an English name: the fixed
 * items through datagen output, the content items through the generated resource pack. Also
 * checks that every sound the stock content uses resolves to a sound file.
 */
class ContentAssetsTest {

    private static final Path RESOURCES = ContentTestSupport.projectDir().resolve("src/main/resources");
    private static final Path GENERATED = ContentTestSupport.projectDir().resolve("src/generated/resources");
    private static final Path MAIN = ContentTestSupport.projectDir().resolve("src/main/resources");

    private static Map<Identifier, byte[]> stockAssets() {
        ContentRegistries.Snapshot stock = ContentTestSupport.load(ContentTestSupport.stockPack());
        return ContentAssets.build(ModItems.contentItems(), stock, stockSoundExists());
    }

    private static Predicate<String> stockSoundExists() {
        return name -> Files.isRegularFile(RESOURCES.resolve("assets/mcheli/sounds/" + name + ".ogg"));
    }

    @Test
    void everyContentItemHasAModelTextureAndName() {
        Map<Identifier, byte[]> files = stockAssets();
        JsonObject english = json(files.get(Identifier.fromNamespaceAndPath("mcheli_ce_port", "lang/en_us.json")))
                .getAsJsonObject();
        assertEquals(119, ModItems.contentItems().size());
        for (ModItems.ContentItem item : ModItems.contentItems()) {
            Identifier id = item.id();
            assertNotNull(files.get(id.withPath("items/" + id.getPath() + ".json")), "item model definition of " + id);
            JsonObject model = json(files.get(id.withPath("models/item/" + id.getPath() + ".json"))).getAsJsonObject();
            assertTextureExists(model.getAsJsonObject("textures").get("layer0").getAsString(), id);
            String name = english.has("item." + id.toLanguageKey())
                    ? english.get("item." + id.toLanguageKey()).getAsString() : "";
            assertFalse(name.isBlank(), "English name of " + id);
        }
        JsonObject japanese = json(files.get(Identifier.fromNamespaceAndPath("mcheli_ce_port", "lang/ja_jp.json")))
                .getAsJsonObject();
        assertEquals("AH-64D アパッチ・ロングボウ", japanese.get("item.mcheli_ce_port.ah-64").getAsString());
    }

    @Test
    void handToolsDrawTheir3dModelWithAFlatFallback() throws IOException {
        Path items = MAIN.resolve("assets/mcheli_ce_port/items");
        List<String> tools = new ArrayList<>();
        try (Stream<Path> files = Files.list(items)) {
            for (Path file : files.sorted().toList()) {
                JsonObject model = json(Files.readAllBytes(file)).getAsJsonObject().getAsJsonObject("model");
                String name = file.getFileName().toString().replace(".json", "");
                tools.add(name);
                assertEquals("mcheli_ce_port:tool", model.get("type").getAsString(), name);
                assertEquals(name, model.get("tool").getAsString());
                assertEquals("mcheli_ce_port:item/" + name + "_hand", model.get("base").getAsString());
                assertTrue(Files.isRegularFile(MAIN.resolve("assets/mcheli_ce_port/models/item/" + name + "_hand.json")),
                        "display transforms of " + name);
                // The hand tools fall back to their flat item, the drafting table to its block model.
                String fallback = model.getAsJsonObject("fallback").get("model").getAsString();
                String folder = name.equals("drafting_table") ? "block/" : "item/";
                assertEquals("mcheli_ce_port:" + folder + name, fallback);
                assertTrue(Files.isRegularFile(GENERATED.resolve("assets/mcheli_ce_port/models/" + folder + name + ".json")),
                        "fallback model of " + name);
            }
        }
        assertEquals(List.of("drafting_table", "fgm148", "fim92", "gltd", "rangefinder", "wrench"), tools);
    }

    @Test
    void everyFixedItemHasAModelTextureAndName() throws IOException {
        JsonObject english = json(Files.readAllBytes(GENERATED.resolve("assets/mcheli_ce_port/lang/en_us.json")))
                .getAsJsonObject();
        List<Identifier> fixed = new ArrayList<>();
        for (DeferredHolder<Item, ? extends Item> holder : ModRegistries.ITEMS.getEntries()) {
            Item item = holder.get();
            if (item instanceof AircraftItem || item instanceof ThrowableItem) {
                continue;
            }
            Identifier id = holder.getId();
            fixed.add(id);
            // The hand tools' definitions are hand-written (ToolItemModel), the rest generated.
            String definition = "assets/mcheli_ce_port/items/" + id.getPath() + ".json";
            assertTrue(Files.isRegularFile(GENERATED.resolve(definition)) || Files.isRegularFile(MAIN.resolve(definition)),
                    "item model definition of " + id);
            String key = (item instanceof BlockItem ? "block." : "item.") + id.toLanguageKey();
            assertTrue(english.has(key), "English name of " + id);
        }
        assertEquals(22, fixed.size());
        try (Stream<Path> models = Files.walk(GENERATED.resolve("assets/mcheli_ce_port/models"))) {
            for (Path file : models.filter(Files::isRegularFile).toList()) {
                JsonObject model = json(Files.readAllBytes(file)).getAsJsonObject();
                if (model.has("textures")) {
                    for (Map.Entry<String, JsonElement> texture : model.getAsJsonObject("textures").entrySet()) {
                        assertTextureExists(texture.getValue().getAsString(), file.getFileName().toString());
                    }
                }
            }
        }
    }

    @Test
    void everyStockSoundEventHasASoundFile() {
        JsonObject sounds = json(stockAssets().get(Identifier.fromNamespaceAndPath("mcheli", "sounds.json")))
                .getAsJsonObject();
        assertTrue(sounds.size() > 60, "built-in and content sound events: " + sounds.keySet());
        assertFalse(sounds.has("railgun"), "stock content names sounds that have no file");
        assertEquals(3, sounds.getAsJsonObject("wrench").getAsJsonArray("sounds").size());
        List<String> missing = new ArrayList<>();
        for (Map.Entry<String, JsonElement> event : sounds.entrySet()) {
            for (JsonElement sound : event.getValue().getAsJsonObject().getAsJsonArray("sounds")) {
                String name = sound.isJsonObject() ? sound.getAsJsonObject().get("name").getAsString()
                        : sound.getAsString();
                Identifier id = Identifier.parse(name);
                if (!Files.isRegularFile(RESOURCES.resolve("assets/" + id.getNamespace() + "/sounds/" + id.getPath()
                        + ".ogg"))) {
                    missing.add(event.getKey() + " -> " + name);
                }
            }
        }
        assertEquals(List.of(), missing);
    }

    private static void assertTextureExists(String texture, Object owner) {
        Identifier id = Identifier.parse(texture);
        Path file = RESOURCES.resolve("assets/" + id.getNamespace() + "/textures/" + id.getPath() + ".png");
        assertTrue(Files.isRegularFile(file), "texture " + texture + " of " + owner);
    }

    private static JsonElement json(byte[] bytes) {
        assertNotNull(bytes);
        return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
    }
}
