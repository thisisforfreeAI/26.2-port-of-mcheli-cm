package io.github.mcheliceport.content;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * The kinds of content files an addon pack can contain, keyed by the folder they live in under
 * {@code assets/mcheli/}.
 *
 * <p>Ported from upstream {@code com.norwood.mcheli.helper.info.ContentType} and
 * {@code ContentFactories}. Upstream's {@code ITEM} type ("item" folder) is not ported: its parsers
 * were stubs that never produced content, and no stock or known addon content uses it.
 */
public enum ContentType {
    HELICOPTER("helicopter", "helicopters"),
    PLANE("plane", "planes"),
    SHIP("ship", "ships"),
    TANK("tank", "tanks"),
    VEHICLE("vehicle", "vehicles"),
    WEAPON("weapon", "weapons"),
    THROWABLE("throwable", "throwable"),
    HUD("hud", "hud");

    /** Singular name, used in log messages. */
    public final String typeName;
    /** Folder name under {@code assets/mcheli/}. */
    public final String dirName;

    ContentType(String typeName, String dirName) {
        this.typeName = typeName;
        this.dirName = dirName;
    }

    /** Returns the content type stored in the given folder, or null if the folder holds no content. */
    public static @Nullable ContentType fromDirName(@Nullable String dirName) {
        if (dirName == null) {
            return null;
        }
        for (ContentType type : values()) {
            if (type.dirName.equals(dirName)) {
                return type;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
