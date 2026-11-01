package io.github.mcheliceport.platform.loader;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A block entity with load and chunk-unload hooks: on NeoForge these are NeoForge's own
 * {@code onLoad} and {@code onChunkUnloaded}.
 */
public abstract class ModBlockEntity extends BlockEntity {

    protected ModBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }
}
