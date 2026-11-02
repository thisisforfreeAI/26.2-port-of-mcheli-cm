package io.github.mcheliceport.platform.registry;

import java.util.function.Supplier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

/** An item entry; like NeoForge's {@code DeferredItem}. */
public class DeferredItem<T extends Item> extends DeferredHolder<Item, T> implements ItemLike {

    DeferredItem(ResourceKey<Item> key, Supplier<? extends T> factory) {
        super(key, factory);
    }

    @Override
    public Item asItem() {
        return get();
    }

    public ItemStack toStack() {
        return new ItemStack(get());
    }

    public ItemStack toStack(int count) {
        return new ItemStack(get(), count);
    }
}
