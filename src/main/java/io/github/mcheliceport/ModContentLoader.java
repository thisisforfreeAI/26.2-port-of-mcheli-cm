package io.github.mcheliceport;

import io.github.mcheliceport.content.ContentRegistries;
import io.github.mcheliceport.content.addon.AddonManager;
import io.github.mcheliceport.content.addon.AddonPack;
import io.github.mcheliceport.content.addon.BuiltinAddonPack;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.jarcontents.JarContents;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Loads McHeli content while the mod is constructed, before registries are filled
 * (PORTING_PLAN.md section 5.1): extracts the stock pack from the mod jar to
 * {@code <game dir>/mcheli_addons/default/} when needed, finds all packs in
 * {@code mcheli_addons/} and parses their content.
 */
public final class ModContentLoader {

    private static List<AddonPack> packs = List.of();
    private static JarContents modContents;

    private ModContentLoader() {
    }

    /** {@code <game dir>/mcheli_addons}. */
    public static Path addonDir() {
        return FMLPaths.GAMEDIR.get().resolve(AddonManager.FOLDER_NAME);
    }

    /** The addon packs found at startup, stock pack first. */
    public static List<AddonPack> packs() {
        return packs;
    }

    /** The files of the mod jar (or of the build output folders in development). */
    public static JarContents modContents() {
        return modContents;
    }

    static ContentRegistries.Snapshot load(ModContainer container) {
        Path addonDir = addonDir();
        modContents = container.getModInfo().getOwningFile().getFile().getContents();
        JarContents contents = modContents;
        BuiltinAddonPack.ensureExtracted(addonDir, (folder, visitor) -> {
            try {
                contents.visitContent(folder, (path, resource) -> {
                    try {
                        visitor.visit(path, resource::open);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            } catch (UncheckedIOException e) {
                throw e.getCause();
            }
        }, container.getModInfo().getVersion().toString());
        packs = AddonManager.loadAddons(addonDir);
        return ContentRegistries.loadContents(packs);
    }
}
