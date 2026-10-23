package io.github.mcheliceport.content.addon;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import io.github.mcheliceport.content.ContentType;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The stock content pack, which ships inside the mod and is extracted to
 * {@code mcheli_addons/default/} so that it loads like any other pack.
 *
 * <p>Ported from upstream {@code com.norwood.mcheli.helper.addon.BuiltinAddonPack}, with two
 * differences:
 * <ul>
 *   <li>Only the content definitions (the vehicle, weapon, throwable and HUD folders) are
 *       extracted. Upstream copied every asset (about 90 MB) and added the copy as a resource
 *       pack; the port reads stock textures, models and sounds from the mod jar.</li>
 *   <li>Upstream extracted the pack once and then only when a config option asked for it, so
 *       updating the mod left old stock content in place. The port records the mod version in the
 *       extracted {@code pack.mcmeta} and extracts again when it or the content revision changes. Changes made inside
 *       {@code default/} are lost then; overrides belong in a separate pack.</li>
 * </ul>
 */
public final class BuiltinAddonPack {

    public static final String EXTRACTED_FOLDER_NAME = "default";
    public static final String DOMAIN = "@builtin";
    private static final String ASSETS_FOLDER = "assets/mcheli";
    /** Increment for stock definition fixes made between milestone releases. */
    private static final int CONTENT_REVISION = 3;
    private static final Logger LOGGER = LogUtils.getLogger();

    private BuiltinAddonPack() {
    }

    /** Opens one file of the source. */
    @FunctionalInterface
    public interface Opener {

        InputStream open() throws IOException;
    }

    /** Receives one file of the source. */
    @FunctionalInterface
    public interface FileVisitor {

        void visit(String relativePath, Opener opener) throws IOException;
    }

    /** Where the stock files come from: the mod jar in the game, a folder in tests. */
    @FunctionalInterface
    public interface Source {

        /** Calls the visitor for every file under {@code folder}, with paths relative to the jar root. */
        void visitFiles(String folder, FileVisitor visitor) throws IOException;

        /** A folder laid out like the mod jar. */
        static Source ofFolder(Path root) {
            return (folder, visitor) -> {
                Path start = root.resolve(folder);
                if (!Files.isDirectory(start)) {
                    return;
                }
                Files.walkFileTree(start, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        String rel = root.relativize(file).toString().replace('\\', '/');
                        visitor.visit(rel, () -> Files.newInputStream(file));
                        return FileVisitResult.CONTINUE;
                    }
                });
            };
        }
    }

    /** The extracted pack folder inside {@code addonDir}. */
    public static Path getFolder(Path addonDir) {
        return addonDir.resolve(EXTRACTED_FOLDER_NAME);
    }

    /**
     * Extracts the stock pack unless {@code addonDir/default} already holds the files of this mod
     * version and stock content revision.
     *
     * @return true if the pack was (re)extracted
     */
    public static boolean ensureExtracted(Path addonDir, Source source, String modVersion) {
        Path targetDir = getFolder(addonDir);
        String existing = readExtractedVersion(targetDir);
        if (modVersion.equals(existing)) {
            return false;
        }
        try {
            extractTo(targetDir, source, modVersion);
            LOGGER.info(existing == null ? "Extracted the stock content pack to '{}'"
                    : "Updated the stock content pack in '{}' (was version {})", targetDir, existing);
            return true;
        } catch (IOException | UncheckedIOException e) {
            LOGGER.error("Failed to extract the stock content pack to '{}'", targetDir.toAbsolutePath(), e);
            return false;
        }
    }

    private static @Nullable String readExtractedVersion(Path targetDir) {
        Path meta = targetDir.resolve("pack.mcmeta");
        if (!Files.isRegularFile(meta)) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(meta, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            JsonElement addon = root.isJsonObject() ? root.getAsJsonObject().get("addon") : null;
            JsonElement version = addon != null && addon.isJsonObject() ? addon.getAsJsonObject().get("version")
                    : null;
            JsonElement revision = addon != null && addon.isJsonObject() ? addon.getAsJsonObject().get("port_content_revision")
                    : null;
            if (revision == null || revision.getAsInt() != CONTENT_REVISION) { return null; }
            return version != null && version.isJsonPrimitive() ? version.getAsString() : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static void extractTo(Path targetDir, Source source, String modVersion) throws IOException {
        deleteRecursively(targetDir);
        Files.createDirectories(targetDir);
        AtomicInteger copied = new AtomicInteger();
        source.visitFiles(ASSETS_FOLDER, (relativePath, opener) -> {
            if (!isContentFile(relativePath)) {
                return;
            }
            Path out = targetDir.resolve(relativePath).normalize();
            if (!out.startsWith(targetDir)) {
                throw new IOException("Refusing to extract outside the pack folder: " + relativePath);
            }
            Files.createDirectories(out.getParent());
            try (InputStream in = opener.open()) {
                Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
            }
            copied.incrementAndGet();
        });
        // Written last, so an interrupted extraction is redone at the next start.
        Files.writeString(targetDir.resolve("pack.mcmeta"), packMeta(modVersion), StandardCharsets.UTF_8);
        LOGGER.info("Extracted {} stock content files", copied.get());
    }

    /** Whether a jar path is a content definition: {@code assets/mcheli/<content folder>/...}. */
    static boolean isContentFile(String relativePath) {
        String[] parts = relativePath.split("/");
        return parts.length >= 4 && ContentType.fromDirName(parts[2]) != null;
    }

    private static String packMeta(String modVersion) {
        JsonObject pack = new JsonObject();
        pack.addProperty("pack_format", 3);
        pack.addProperty("description", "McHeli CE default content pack");
        JsonObject addon = new JsonObject();
        addon.addProperty("domain", DOMAIN);
        addon.addProperty("version", modVersion);
        addon.addProperty("port_content_revision", CONTENT_REVISION);
        addon.addProperty("loader_version", "2");
        addon.addProperty("credits", "EMB4-MCHeli");
        addon.add("authors", AddonPack.toJsonArray(List.of("EMB4", "Murachiki27")));
        addon.addProperty("description", "Stock content of McHeli CE (26.2 Port), extracted from the mod. "
                + "This folder is replaced when the mod version or stock content revision changes; put changes in a separate pack.");
        JsonObject root = new JsonObject();
        root.add("pack", pack);
        root.add("addon", addon);
        return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root) + "\n";
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (exc != null) {
                    throw exc;
                }
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
