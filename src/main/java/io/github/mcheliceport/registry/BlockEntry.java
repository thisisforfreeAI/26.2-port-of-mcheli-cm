package io.github.mcheliceport.registry;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;

/** A registered block, the loader-neutral stand-in for NeoForge's {@code DeferredBlock}. */
public final class BlockEntry<T extends Block> extends RegEntry<Block, T> implements ItemLike {

    BlockEntry(RegEntry<Block, ? extends T> entry) {
        super(entry);
    }

    @Override
    public Item asItem() {
        return get().asItem();
    }
}
