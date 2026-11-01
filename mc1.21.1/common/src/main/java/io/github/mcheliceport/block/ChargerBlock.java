package io.github.mcheliceport.block;

import io.github.mcheliceport.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * The aircraft charger (upstream {@code MCH_BlockCharger}): it buffers energy and feeds nearby
 * electric vehicles ({@link ChargerBlockEntity}). Using it shows how much it holds.
 */
public class ChargerBlock extends Block implements EntityBlock {

    public ChargerBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ChargerBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        return level.isClientSide() || type != ModBlocks.CHARGER_ENTITY.get() ? null
                : (BlockEntityTicker<T>) (BlockEntityTicker<ChargerBlockEntity>) ChargerBlockEntity::serverTick;
    }

    /** Upstream: {@code Charger: <energy> / <capacity>} above the hotbar. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof ChargerBlockEntity charger) {
            serverPlayer.sendSystemMessage(Component.literal(String.format("Charger: %,d / %,d", charger.getEnergy(),
                    charger.getCapacity())), true);
        }
        return InteractionResult.SUCCESS;
    }
}
