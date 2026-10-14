package io.github.mcheliceport.content.addon;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;

/**
 * A content pack in {@code mcheli_addons/}: a folder, zip or jar with {@code assets/mcheli/...}
 * content and an optional {@code pack.mcmeta}.
 *
 * <p>Ported from upstream {@code com.norwood.mcheli.helper.addon.AddonPack}. The {@code addon}
 * block of {@code pack.mcmeta} is read as upstream does: {@code domain}, {@code version},
 * {@code loader_version}, {@code credits}, {@code description} and {@code authors} (or
 * {@code author}). A pack without a {@code domain} gets one from its file name.
 */
public class AddonPack {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final String addonDomain;
    private final String addonName;
    private final String addonVersion;
    private final Path addonFile;
    private final String credits;
    private final List<String> authors;
    private final String description;
    private final String loaderVersion;
    private final JsonObject packMeta;

    public AddonPack(String addonDomain, String addonName, String addonVersion, Path addonFile, String credits,
            List<String> authors, String description, String loaderVersion, JsonObject packMeta) {
        this.addonDomain = addonDomain;
        this.addonName = addonName;
        this.addonVersion = addonVersion;
        this.addonFile = addonFile;
        this.credits = credits;
        this.authors = List.copyOf(authors);
        this.description = description;
        this.loaderVersion = loaderVersion;
        this.packMeta = packMeta;
    }

    /** Reads a pack's metadata. */
    public static AddonPack create(Path addonFile) {
        JsonObject packMetaJson = loadPackMeta(addonFile);
        JsonObject packJson = getObject(packMetaJson, "pack");
        JsonObject addonJson = getObject(packMetaJson, "addon");
        boolean hasPackMeta = !packMetaJson.entrySet().isEmpty();
        String fileName = addonFile.getFileName().toString();
        String addonDomain = getAddonDomain(addonJson, fileName);
        String packName = getString(packJson, "description", stripExtension(fileName));
        String version = getString(addonJson, "version", "0.0");
        String credits = getString(addonJson, "credits", "");
        String description = getString(addonJson, "description", "");
        // Packs with pack.mcmeta default to loader 1 (content only under assets/ at the root),
        // packs without it to loader 2 (assets/ may be nested, for example in a wrapping folder).
        String loaderVersion = getString(addonJson, "loader_version", hasPackMeta ? "1" : "2");
        return new AddonPack(addonDomain, packName, version, addonFile, credits, getAuthors(addonJson), description,
                loaderVersion, packMetaJson);
    }

    private static String getAddonDomain(JsonObject addonJson, String fileName) {
        String configured = getString(addonJson, "domain", "");
        if (!configured.isEmpty()) {
            return configured.toLowerCase(Locale.ROOT);
        }

        String fallback = stripExtension(fileName).toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]+", "_")
                .replaceAll("^_+|_+$", "");
        if (fallback.isEmpty()) {
            fallback = "addon_" + Integer.toHexString(fileName.hashCode());
        }

        LOGGER.warn("Addon '{}' has no domain metadata, using fallback domain '{}'", fileName, fallback);
        return fallback;
    }

    static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static List<String> getAuthors(JsonObject jsonObject) {
        List<String> list = new ArrayList<>();
        if (jsonObject.has("authors")) {
            JsonElement element = jsonObject.get("authors");
            if (element.isJsonArray()) {
                for (JsonElement author : element.getAsJsonArray()) {
                    list.add(author.getAsString());
                }
            }
        } else if (jsonObject.has("author")) {
            JsonElement element = jsonObject.get("author");
            if (element.isJsonPrimitive()) {
                list.add(element.getAsString());
            }
        }
        return list;
    }

    private static JsonObject loadPackMeta(Path addonFile) {
        try (PackFiles files = PackFiles.open(addonFile)) {
            Path meta = files.resolve("pack.mcmeta");
            if (meta == null) {
                LOGGER.warn("'pack.mcmeta' was not found in '{}'", addonFile.getFileName());
                return new JsonObject();
            }
            try (Reader reader = Files.newBufferedReader(meta, StandardCharsets.UTF_8)) {
                JsonElement element = JsonParser.parseReader(reader);
                return element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Could not read 'pack.mcmeta' of '{}'", addonFile.getFileName(), e);
            return new JsonObject();
        }
    }

    private static JsonObject getObject(JsonObject json, String key) {
        JsonElement element = json.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
    }

    private static String getString(JsonObject json, String key, String fallback) {
        JsonElement element = json.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : fallback;
    }

    /** Builds the {@code authors} JSON array of a pack.mcmeta. */
    static JsonArray toJsonArray(List<String> values) {
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        return array;
    }

    public String getDomain() {
        return this.addonDomain;
    }

    public String getName() {
        return this.addonName;
    }

    public String getVersion() {
        return this.addonVersion;
    }

    public String getAuthorsString() {
        return String.join(", ", this.authors);
    }

    public List<String> getAuthors() {
        return this.authors;
    }

    public String getCredits() {
        return this.credits;
    }

    public String getDescription() {
        return this.description;
    }

    public String getLoaderVersion() {
        return this.loaderVersion;
    }

    /** The pack folder or archive. */
    public Path getFile() {
        return this.addonFile;
    }

    /** The whole {@code pack.mcmeta}, empty if the pack has none. */
    public JsonObject getPackMeta() {
        return this.packMeta.deepCopy();
    }

    @Override
    public String toString() {
        return this.addonDomain + " (" + this.addonFile.getFileName() + ")";
    }
}
