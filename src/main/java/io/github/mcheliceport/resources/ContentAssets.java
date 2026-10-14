package io.github.mcheliceport.resources;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.content.ContentLocation;
import io.github.mcheliceport.content.ContentRegistries;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.content.sound.SoundDefinitions;
import io.github.mcheliceport.content.throwable.ThrowableInfo;
import io.github.mcheliceport.registry.ModItems;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;

/**
 * The client resources generated from the loaded content (upstream wrote them to
 * {@code mcheli_addons/generated/}):
 * <ul>
 *   <li>an item model definition and an item model for every content item, showing the texture
 *       {@code mcheli:items/<name>} as upstream's {@code LegacyModelLoader} did;</li>
 *   <li>lang files with the content's display names ({@code DisplayName} in YAML,
 *       {@code DisplayName}/{@code AddDisplayName} in TXT);</li>
 *   <li>{@code assets/mcheli/sounds.json} for every sound event the content uses. Events named in
 *       content whose sound file exists in neither the mod nor an addon pack are left out (a
 *       resource pack can still define them in its own {@code sounds.json}).</li>
 * </ul>
 */
public final class ContentAssets {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String DEFAULT_LANGUAGE = "en_us";

    private ContentAssets() {
    }

    /**
     * Builds the files.
     *
     * @param soundFileExists whether {@code assets/mcheli/sounds/<name>.ogg} exists, for upstream's
     *                        {@code _snd} fallback
     */
    public static Map<Identifier, byte[]> build(List<ModItems.ContentItem> items, ContentRegistries.Snapshot content,
            Predicate<String> soundFileExists) {
        Map<Identifier, byte[]> files = new LinkedHashMap<>();
        Map<String, Map<String, String>> lang = new TreeMap<>();
        for (ModItems.ContentItem item : items) {
            Identifier id = item.id();
            files.put(id.withPath("items/" + id.getPath() + ".json"), json(itemModelDefinition(id)));
            files.put(id.withPath("models/item/" + id.getPath() + ".json"), json(itemModel(id.getPath())));
            addDisplayNames(lang, "item." + id.toLanguageKey(), item, content);
        }
        lang.forEach((code, entries) -> files.put(
                Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "lang/" + code + ".json"), json(GSON.toJsonTree(entries))));
        files.put(Identifier.fromNamespaceAndPath(ContentLocation.NAMESPACE, "sounds.json"),
                json(soundsJson(SoundDefinitions.snapshot(), soundFileExists)));
        return files;
    }

    static JsonObject itemModelDefinition(Identifier itemId) {
        JsonObject model = new JsonObject();
        model.addProperty("type", "minecraft:model");
        model.addProperty("model", itemId.withPrefix("item/").toString());
        JsonObject root = new JsonObject();
        root.add("model", model);
        return root;
    }

    /** A flat item model with the content's item texture, as upstream's template model. */
    static JsonObject itemModel(String itemPath) {
        JsonObject textures = new JsonObject();
        textures.addProperty("layer0", ContentLocation.NAMESPACE + ":items/" + itemPath);
        JsonObject root = new JsonObject();
        root.addProperty("parent", "minecraft:item/generated");
        root.add("textures", textures);
        return root;
    }

    private static void addDisplayNames(Map<String, Map<String, String>> lang, String key, ModItems.ContentItem item,
            ContentRegistries.Snapshot content) {
        String displayName;
        Map<String, String> byLanguage;
        switch (item.type()) {
            case THROWABLE -> {
                ThrowableInfo info = content.throwable().get(item.contentName());
                if (info == null) {
                    return;
                }
                displayName = info.displayName;
                byLanguage = info.displayNameLang;
            }
            case HELICOPTER, PLANE, SHIP, TANK, VEHICLE -> {
                AircraftInfo info = aircraft(item, content);
                if (info == null) {
                    return;
                }
                displayName = info.displayName;
                byLanguage = info.displayNameLang;
            }
            default -> {
                return;
            }
        }
        // As upstream: the default name goes to en_us, then each language name (which may replace it).
        lang.computeIfAbsent(DEFAULT_LANGUAGE, k -> new TreeMap<>()).put(key, displayName);
        byLanguage.forEach((code, name) -> lang
                .computeIfAbsent(code.trim().toLowerCase(Locale.ROOT), k -> new TreeMap<>()).put(key, name));
    }

    private static AircraftInfo aircraft(ModItems.ContentItem item, ContentRegistries.Snapshot content) {
        return switch (item.type()) {
            case HELICOPTER -> content.heli().get(item.contentName());
            case PLANE -> content.plane().get(item.contentName());
            case SHIP -> content.ship().get(item.contentName());
            case TANK -> content.tank().get(item.contentName());
            case VEHICLE -> content.vehicle().get(item.contentName());
            default -> null;
        };
    }

    /** {@code sounds.json} for the {@code mcheli} namespace. */
    static JsonObject soundsJson(Map<String, SoundDefinitions.Definition> definitions,
            Predicate<String> soundFileExists) {
        JsonObject root = new JsonObject();
        definitions.forEach((event, definition) -> {
            if (!Identifier.isValidPath(event)) {
                LOGGER.warn("Skipping sound event '{}': not a valid resource path", event);
                return;
            }
            JsonArray sounds = new JsonArray();
            switch (definition) {
                case SoundDefinitions.Auto auto -> {
                    String file = auto.resolveFile(soundFileExists);
                    if (!soundFileExists.test(file)) {
                        // Some stock weapons name sounds that have no file; upstream played nothing.
                        LOGGER.debug("No sound file for sound event '{}'", event);
                        return;
                    }
                    sounds.add(ContentLocation.NAMESPACE + ":" + file);
                }
                case SoundDefinitions.Explicit explicit -> {
                    for (SoundDefinitions.Variant variant : explicit.variants()) {
                        if (Identifier.tryParse(variant.location()) == null) {
                            LOGGER.warn("Skipping sound file '{}' of event '{}': not a valid resource location",
                                    variant.location(), event);
                            continue;
                        }
                        sounds.add(soundEntry(variant));
                    }
                }
            }
            JsonObject entry = new JsonObject();
            entry.add("sounds", sounds);
            root.add(event, entry);
        });
        return root;
    }

    private static JsonElement soundEntry(SoundDefinitions.Variant variant) {
        if (variant.volume() == 1f && variant.pitch() == 1f && variant.weight() == 1 && !variant.stream()) {
            return GSON.toJsonTree(variant.location());
        }
        JsonObject entry = new JsonObject();
        entry.addProperty("name", variant.location());
        if (variant.volume() != 1f) {
            entry.addProperty("volume", variant.volume());
        }
        if (variant.pitch() != 1f) {
            entry.addProperty("pitch", variant.pitch());
        }
        if (variant.weight() != 1) {
            entry.addProperty("weight", variant.weight());
        }
        if (variant.stream()) {
            entry.addProperty("stream", true);
        }
        return entry;
    }

    private static byte[] json(JsonElement element) {
        return (GSON.toJson(element) + "\n").getBytes(StandardCharsets.UTF_8);
    }
}
