package io.github.mcheliceport.block;

import io.github.mcheliceport.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The drafting table's block entity (upstream {@code MCH_DraftingTableTileEntity}). It holds no
 * data; it exists so the table's 3D model is drawn by a block entity renderer.
 */
public class DraftingTableBlockEntity extends BlockEntity {

    public DraftingTableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.DRAFTING_TABLE_ENTITY.get(), pos, state);
    }
}
