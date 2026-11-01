package io.github.mcheliceport;

import io.github.mcheliceport.content.ContentRegistries;
import io.github.mcheliceport.content.addon.AddonManager;
import io.github.mcheliceport.content.addon.AddonPack;
import io.github.mcheliceport.content.addon.BuiltinAddonPack;
import io.github.mcheliceport.platform.Services;
import java.nio.file.Path;
import java.util.List;

/**
 * Loads McHeli content while the mod starts, before registries are filled
 * (PORTING_PLAN.md section 5.1): extracts the stock pack from the mod jar to
 * {@code <game dir>/mcheli_addons/default/} when needed, finds all packs in
 * {@code mcheli_addons/} and parses their content.
 */
public final class ModContentLoader {

    private static List<AddonPack> packs = List.of();

    private ModContentLoader() {
    }

    /** {@code <game dir>/mcheli_addons}. */
    public static Path addonDir() {
        return Services.PLATFORM.gameDir().resolve(AddonManager.FOLDER_NAME);
    }

    /** The addon packs found at startup, stock pack first. */
    public static List<AddonPack> packs() {
        return packs;
    }

    /** Whether the mod jar (or the build output in development) has a file at {@code path}. */
    public static boolean modFileExists(String path) {
        return Services.PLATFORM.modFileExists(path);
    }

    static ContentRegistries.Snapshot load() {
        Path addonDir = addonDir();
        BuiltinAddonPack.ensureExtracted(addonDir,
                (folder, visitor) -> Services.PLATFORM.visitModFiles(folder,
                        (path, opener) -> visitor.visit(path, opener::open)),
                Services.PLATFORM.modVersion());
        packs = AddonManager.loadAddons(addonDir);
        return ContentRegistries.loadContents(packs);
    }
}
