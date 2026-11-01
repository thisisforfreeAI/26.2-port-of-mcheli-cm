package io.github.mcheliceport.platform.loader;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A block entity with NeoForge-style load and chunk-unload hooks, called on the server from
 * Fabric's block entity and chunk events ({@code FabricMod}).
 */
public abstract class ModBlockEntity extends BlockEntity {

    protected ModBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** The block entity was added to a loaded level. */
    public void onLoad() {
    }

    /** Its chunk is unloading (not called when the block is removed). */
    public void onChunkUnloaded() {
    }
}
