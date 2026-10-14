package io.github.mcheliceport.registry;

import io.github.mcheliceport.block.DraftingTableBlock;
import io.github.mcheliceport.wingman.block.WingmanMarkerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;

/**
 * The port's blocks. Upstream's 1.12 values are converted: 1.12's {@code setResistance(10)} is an
 * explosion resistance of 6, and blocks of 1.12's iron material need a pickaxe to drop (the
 * {@code mineable/pickaxe} and {@code needs_stone_tool} tags, from datagen).
 */
public final class ModBlocks {

    public static final DeferredBlock<DraftingTableBlock> DRAFTING_TABLE = ModRegistries.BLOCKS.registerBlock(
            "drafting_table", p -> new DraftingTableBlock(p, false, ModBlocks.LIT_DRAFTING_TABLE),
            p -> draftingTable(p));
    public static final DeferredBlock<DraftingTableBlock> LIT_DRAFTING_TABLE = ModRegistries.BLOCKS.registerBlock(
            "lit_drafting_table", p -> new DraftingTableBlock(p, true, ModBlocks.DRAFTING_TABLE),
            p -> draftingTable(p).lightLevel(state -> 15));
    public static final DeferredBlock<Block> CHARGER = ModRegistries.BLOCKS.registerSimpleBlock("charger",
            p -> p.mapColor(MapColor.METAL).sound(SoundType.METAL).strength(1.5f, 6.0f)
                    .requiresCorrectToolForDrops());
    public static final DeferredBlock<WingmanMarkerBlock> WINGMAN_MARKER = ModRegistries.BLOCKS.registerBlock(
            "wingman_marker", WingmanMarkerBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(2.0f, 6.0f).requiresCorrectToolForDrops());

    private ModBlocks() {
    }

    private static Block.Properties draftingTable(Block.Properties p) {
        return p.mapColor(MapColor.METAL).sound(SoundType.METAL).strength(0.2f).noOcclusion()
                .pushReaction(PushReaction.DESTROY);
    }

    /** Loads the class, which registers the blocks. */
    static void init() {
    }
}
