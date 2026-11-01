package io.github.mcheliceport.platform.registry;

import java.util.function.Supplier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;

/** A block entry; like NeoForge's {@code DeferredBlock}. */
public class DeferredBlock<T extends Block> extends DeferredHolder<Block, T> implements ItemLike {

    DeferredBlock(ResourceKey<Block> key, Supplier<? extends T> factory) {
        super(key, factory);
    }

    @Override
    public Item asItem() {
        return get().asItem();
    }
}
