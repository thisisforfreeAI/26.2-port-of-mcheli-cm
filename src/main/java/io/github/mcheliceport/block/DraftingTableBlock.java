package io.github.mcheliceport.block;

import io.github.mcheliceport.registry.ModBlocks;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The drafting table, where vehicles are crafted. There are two blocks, unlit and lit, as upstream
 * ({@code MCH_DraftingTableBlock}): sneak-using the table switches its lamp.
 *
 * <p>As upstream, the block itself is invisible: its block entity's renderer draws the table's
 * 3D model. Using the table opens its recipe browser ({@code DraftingTableMenu}).
 */
public class DraftingTableBlock extends Block implements EntityBlock {

    public static final EnumProperty<Direction8> DIRECTION8 = EnumProperty.create("direction8", Direction8.class);

    private final boolean lit;
    private final Supplier<? extends Block> otherVariant;

    public DraftingTableBlock(Properties properties, boolean lit, Supplier<? extends Block> otherVariant) {
        super(properties);
        this.lit = lit;
        this.otherVariant = otherVariant;
        this.registerDefaultState(this.stateDefinition.any().setValue(DIRECTION8, Direction8.NORTH));
    }

    public boolean isLit() {
        return this.lit;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DraftingTableBlockEntity(pos, state);
    }

    /** Upstream {@code BlockContainer}: drawn by the block entity renderer only. */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DIRECTION8);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(DIRECTION8, Direction8.fromYaw(context.getRotation()));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        if (!player.isSecondaryUseActive()) {
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                io.github.mcheliceport.menu.DraftingTableMenu.open(serverPlayer, pos);
            }
            return InteractionResult.SUCCESS;
        }
        if (!level.isClientSide()) {
            BlockState switched = this.otherVariant.get().defaultBlockState()
                    .setValue(DIRECTION8, state.getValue(DIRECTION8));
            level.setBlock(pos, switched, Block.UPDATE_CLIENTS);
            level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.3f, 0.5f);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return new ItemStack(ModBlocks.DRAFTING_TABLE.get());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(DIRECTION8, state.getValue(DIRECTION8).rotate(rotation));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(DIRECTION8, state.getValue(DIRECTION8).mirror(mirror));
    }
}
