package io.github.mcheliceport;

import io.github.mcheliceport.content.ContentRegistries;
import io.github.mcheliceport.content.addon.AddonManager;
import io.github.mcheliceport.content.addon.AddonPack;
import io.github.mcheliceport.content.addon.BuiltinAddonPack;
import io.github.mcheliceport.platform.Platform;
import java.nio.file.Path;
import java.util.List;

/**
 * Loads McHeli content while the mod is constructed, before registries are filled
 * (PORTING_PLAN.md section 5.1): extracts the stock pack from the mod jar to
 * {@code <game dir>/mcheli_addons/default/} when needed, finds all packs in
 * {@code mcheli_addons/} and parses their content.
 */
public final class ModContentLoader {

    private static List<AddonPack> packs = List.of();
    private static Platform.ModFiles modContents;

    private ModContentLoader() {
    }

    /** {@code <game dir>/mcheli_addons}. */
    public static Path addonDir() {
        return Platform.get().gameDir().resolve(AddonManager.FOLDER_NAME);
    }

    /** The addon packs found at startup, stock pack first. */
    public static List<AddonPack> packs() {
        return packs;
    }

    /** The files of the mod jar (or of the build output folders in development). */
    public static Platform.ModFiles modContents() {
        return modContents;
    }

    /** Loads the content, reading the stock pack from {@code modFiles}; {@code modVersion} decides re-extraction. */
    public static ContentRegistries.Snapshot load(Platform.ModFiles modFiles, String modVersion) {
        Path addonDir = addonDir();
        modContents = modFiles;
        BuiltinAddonPack.ensureExtracted(addonDir, modFiles, modVersion);
        packs = AddonManager.loadAddons(addonDir);
        return ContentRegistries.loadContents(packs);
    }
}
