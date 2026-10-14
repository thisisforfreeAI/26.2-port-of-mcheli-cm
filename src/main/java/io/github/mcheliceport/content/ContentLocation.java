package io.github.mcheliceport.content;

import java.util.Locale;

/**
 * Identifies one piece of content: the addon pack it came from and its name.
 *
 * <p>Ported from upstream {@code com.norwood.mcheli.helper.addon.AddonResourceLocation}, keeping
 * only what the content system needs. Upstream extended Minecraft 1.12's {@code ResourceLocation},
 * which lowercased its path, so content names are lowercased here too. The resource namespace is
 * always {@code mcheli} (PORTING_PLAN.md, decision D1), so it is not stored.
 *
 * @param addonDomain domain of the addon pack that defined the content, for example
 *                    {@code @builtin} for the stock content
 * @param path        the content name: the file name without its extension, lowercased
 */
public record ContentLocation(String addonDomain, String path) {

    /** Asset namespace of all McHeli content, kept for addon compatibility (decision D1). */
    public static final String NAMESPACE = "mcheli";
    /** Addon domain of content that belongs to no pack, such as the "none" HUD. */
    public static final String BUILTIN_DOMAIN = "@builtin";

    public ContentLocation {
        addonDomain = addonDomain.toLowerCase(Locale.ROOT);
        path = path.toLowerCase(Locale.ROOT);
    }

    public static ContentLocation builtin(String path) {
        return new ContentLocation(BUILTIN_DOMAIN, path);
    }

    /** Same format as upstream's {@code AddonResourceLocation#toString}, for log messages. */
    @Override
    public String toString() {
        return NAMESPACE + ":" + this.addonDomain + '|' + this.path;
    }
}
