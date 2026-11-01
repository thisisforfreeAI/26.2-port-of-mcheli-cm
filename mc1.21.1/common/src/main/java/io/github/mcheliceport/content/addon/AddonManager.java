package io.github.mcheliceport.content.addon;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Finds the addon packs in {@code <game dir>/mcheli_addons/}.
 *
 * <p>Ported from upstream {@code com.norwood.mcheli.helper.addon.AddonManager}. Every folder and
 * every {@code .zip} or {@code .jar} file is a pack, except a folder named {@code generated}
 * (upstream's folder for generated files, which the port does not use). Upstream loaded packs in
 * whatever order the file system listed them; the port loads the stock pack ({@code default})
 * first and then the other packs sorted by name, so a pack can reliably override stock content.
 */
public final class AddonManager {

    public static final String FOLDER_NAME = "mcheli_addons";
    private static final Pattern ZIP_PATTERN = Pattern.compile("(.+)\\.(zip|jar)$", Pattern.CASE_INSENSITIVE);
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, AddonPack> ADDON_LIST = new LinkedHashMap<>();

    private AddonManager() {
    }

    /** The pack with the given domain, if one was loaded. */
    public static synchronized @Nullable AddonPack get(String addonDomain) {
        return ADDON_LIST.get(addonDomain);
    }

    public static synchronized List<AddonPack> getLoadedAddons() {
        return List.copyOf(ADDON_LIST.values());
    }

    /** Finds and reads all packs in {@code addonDir}, creating the folder if needed. */
    public static synchronized List<AddonPack> loadAddons(Path addonDir) {
        LOGGER.info("Loading content from: {}", addonDir.toAbsolutePath());
        ADDON_LIST.clear();
        List<AddonPack> addons = new ArrayList<>();
        List<Path> files;
        try {
            Files.createDirectories(addonDir);
            try (Stream<Path> stream = Files.list(addonDir)) {
                files = stream.filter(AddonManager::isAddonPath).sorted(LOAD_ORDER).toList();
            }
        } catch (IOException e) {
            LOGGER.error("Could not list addon packs in '{}'", addonDir, e);
            return addons;
        }

        for (Path file : files) {
            try {
                AddonPack pack = AddonPack.create(file);
                ADDON_LIST.put(pack.getDomain(), pack);
                addons.add(pack);
            } catch (RuntimeException e) {
                LOGGER.error("Failed to load pack {}", file.getFileName(), e);
            }
        }

        LOGGER.info("Found {} content packs: {}", addons.size(), addons);
        return addons;
    }

    /** Stock pack first, then by file name ignoring case. */
    static final Comparator<Path> LOAD_ORDER = Comparator
            .comparing((Path p) -> !BuiltinAddonPack.EXTRACTED_FOLDER_NAME.equals(p.getFileName().toString()))
            .thenComparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT))
            .thenComparing(p -> p.getFileName().toString());

    private static boolean isAddonPath(Path file) {
        String name = file.getFileName().toString();
        if ("generated".equals(name)) {
            return false;
        }
        return Files.isDirectory(file) || (Files.isRegularFile(file) && ZIP_PATTERN.matcher(name).matches());
    }
}
