package io.github.mcheliceport.item;

import io.github.mcheliceport.content.ContentRegistries;
import io.github.mcheliceport.content.throwable.ThrowableInfo;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;

/**
 * The item of one throwable definition, such as a grenade or a smoke grenade (upstream
 * {@code MCH_ItemThrowable}).
 *
 * <p>Throwing comes with the weapon entities (milestone M5).
 */
public class ThrowableItem extends Item {

    private final String contentName;

    public ThrowableItem(Properties properties, String contentName) {
        super(properties);
        this.contentName = contentName;
    }

    public String getContentName() {
        return this.contentName;
    }

    /** The current definition of this throwable, or null if it is no longer loaded. */
    public @Nullable ThrowableInfo getThrowableInfo() {
        return ContentRegistries.get().throwable().get(this.contentName);
    }
}
