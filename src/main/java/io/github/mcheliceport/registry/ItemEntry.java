package io.github.mcheliceport.registry;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

/** A registered item, the loader-neutral stand-in for NeoForge's {@code DeferredItem}. */
public final class ItemEntry<T extends Item> extends RegEntry<Item, T> implements ItemLike {

    ItemEntry(RegEntry<Item, ? extends T> entry) {
        super(entry);
    }

    @Override
    public Item asItem() {
        return get();
    }

    public ItemStack toStack() {
        return toStack(1);
    }

    public ItemStack toStack(int count) {
        return new ItemStack(get(), count);
    }
}
