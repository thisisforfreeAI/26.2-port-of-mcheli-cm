package io.github.mcheliceport.datagen;

import io.github.mcheliceport.registry.ModBlocks;
import io.github.mcheliceport.registry.ModRegistries;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredHolder;

/** Block drops: each block drops itself, and the lit drafting table drops the unlit one. */
public class ModBlockLootProvider extends BlockLootSubProvider {

    public ModBlockLootProvider(HolderLookup.Provider registries) {
        super(Set.of(), FeatureFlags.REGISTRY.allFlags(), registries);
    }

    @Override
    protected void generate() {
        this.dropSelf(ModBlocks.DRAFTING_TABLE.get());
        this.dropOther(ModBlocks.LIT_DRAFTING_TABLE.get(), ModBlocks.DRAFTING_TABLE.get());
        this.dropSelf(ModBlocks.CHARGER.get());
        this.dropSelf(ModBlocks.WINGMAN_MARKER.get());
    }

    @Override
    protected Iterable<Block> getKnownBlocks() {
        return ModRegistries.BLOCKS.getEntries().stream().<Block>map(DeferredHolder::get).toList();
    }
}
