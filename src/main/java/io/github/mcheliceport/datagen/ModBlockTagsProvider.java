package io.github.mcheliceport.datagen;

import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.registry.ModBlocks;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.neoforged.neoforge.common.data.BlockTagsProvider;

/**
 * Mining tags. Upstream's blocks used 1.12's iron material: a pickaxe mines them fastest, and the
 * charger and marker block (which do not override harvesting, unlike the drafting table) need a
 * stone pickaxe or better to drop.
 */
public class ModBlockTagsProvider extends BlockTagsProvider {

    public ModBlockTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries, McheliCePort.MOD_ID);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        this.tag(BlockTags.MINEABLE_WITH_PICKAXE)
                .add(ModBlocks.DRAFTING_TABLE.getKey())
                .add(ModBlocks.LIT_DRAFTING_TABLE.getKey())
                .add(ModBlocks.CHARGER.getKey())
                .add(ModBlocks.WINGMAN_MARKER.getKey());
        this.tag(BlockTags.NEEDS_STONE_TOOL)
                .add(ModBlocks.CHARGER.getKey())
                .add(ModBlocks.WINGMAN_MARKER.getKey());
    }
}
