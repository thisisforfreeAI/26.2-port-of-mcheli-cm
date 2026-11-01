package io.github.mcheliceport.content;

import com.mojang.logging.LogUtils;
import io.github.mcheliceport.content.addon.AddonPack;
import io.github.mcheliceport.content.addon.PackFiles;
import io.github.mcheliceport.content.parser.ContentParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Reads the content files of one addon pack.
 *
 * <p>Ported from upstream {@code ContentLoader}, {@code FolderContentLoader} and
 * {@code FileContentLoader}. A content file is any file at
 * {@code assets/mcheli/<type folder>/.../<name>.<yml|txt>}. For packs with loader version 1 (a
 * {@code pack.mcmeta} without {@code loader_version}) the {@code assets} folder must be at the
 * root of the pack; for version 2 it may be nested. Files are read in sorted path order.
 */
public final class ContentLoader {

    private static final Logger LOGGER = LogUtils.getLogger();

    private ContentLoader() {
    }

    /** Reads every content file of a pack; the result is grouped by type later. */
    public static List<ContentEntry> load(AddonPack pack) {
        List<ContentEntry> entries = new ArrayList<>();
        boolean deep = "2".equals(pack.getLoaderVersion());
        try (PackFiles files = PackFiles.open(pack.getFile())) {
            for (String path : files.listFiles()) {
                ContentType type = getContentType(path, deep);
                if (type == null) {
                    continue;
                }
                String extension = getExtension(path);
                if (ContentParsers.get(extension) == null) {
                    LOGGER.debug("Skipping '{}' in pack {}: no parser for '.{}' files", path, pack.getDomain(),
                            extension);
                    continue;
                }
                Path file = files.resolve(path);
                if (file == null) {
                    continue;
                }
                entries.add(new ContentEntry(path, pack.getDomain(), type, readLines(file), false));
            }
        } catch (IOException e) {
            LOGGER.error("IO error while reading pack {}", pack, e);
        }
        return entries;
    }

    /**
     * Reads a file as UTF-8 lines. As upstream's reader, invalid bytes (old files are sometimes in
     * Shift JIS) become replacement characters instead of failing the file.
     */
    static List<String> readLines(Path file) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8))) {
            return reader.lines().toList();
        }
    }

    /**
     * The content type of a pack file, or null if it is not a content file. Upstream's
     * {@code ContentRegistries#filter} plus the loaders' depth rule.
     */
    static @Nullable ContentType getContentType(String path, boolean deep) {
        String[] parts = path.split("/");
        int i = indexOf(parts, "assets");
        if (i < 0 || (!deep && i != 0)) {
            return null;
        }
        if (parts.length < i + 4 || !ContentLocation.NAMESPACE.equals(parts[i + 1])) {
            return null;
        }
        return ContentType.fromDirName(parts[i + 2]);
    }

    private static int indexOf(String[] parts, String needle) {
        for (int i = 0; i < parts.length; i++) {
            if (needle.equals(parts[i])) {
                return i;
            }
        }
        return -1;
    }

    static String getExtension(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1);
    }

    static String getNameWithoutExtension(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    /** One content file, read but not parsed yet. */
    public record ContentEntry(String filepath, String domain, ContentType type, List<String> lines,
            boolean reload) {

        /**
         * Parses and validates the file. Errors are logged and give null, as upstream; a definition
         * that validates to false is kept and logged.
         */
        public @Nullable ContentData parse() {
            ContentLocation location = new ContentLocation(this.domain, getNameWithoutExtension(this.filepath));
            String extension = getExtension(this.filepath);
            ContentParser parser = ContentParsers.get(extension);
            if (parser == null) {
                LOGGER.warn("No parser registered for extension '{}'", extension);
                return null;
            }
            try {
                ContentData content = this.invokeParser(parser, location);
                if (content != null && !content.validate()) {
                    LOGGER.debug("Invalid content info: {}", this.filepath);
                }
                return content;
            } catch (Exception ex) {
                String where = ex instanceof ContentParseException cpe ? " at line " + cpe.getLineNo() : "";
                LOGGER.error("An error occurred while loading{} file: {}, domain: {}", where, this.filepath,
                        this.domain, ex);
                return null;
            }
        }

        private @Nullable ContentData invokeParser(ContentParser parser, ContentLocation location) throws Exception {
            return switch (this.type) {
                case HELICOPTER -> parser.parseHelicopter(location, this.filepath, this.lines, this.reload);
                case PLANE -> parser.parsePlane(location, this.filepath, this.lines, this.reload);
                case SHIP -> parser.parseShip(location, this.filepath, this.lines, this.reload);
                case TANK -> parser.parseTank(location, this.filepath, this.lines, this.reload);
                case VEHICLE -> parser.parseVehicle(location, this.filepath, this.lines, this.reload);
                case WEAPON -> parser.parseWeapon(location, this.filepath, this.lines, this.reload);
                case THROWABLE -> parser.parseThrowable(location, this.filepath, this.lines, this.reload);
                case HUD -> parser.parseHud(location, this.filepath, this.lines, this.reload);
            };
        }

        @Override
        public String toString() {
            return "ContentEntry{" + this.domain + ":" + this.filepath + '}';
        }
    }
}
