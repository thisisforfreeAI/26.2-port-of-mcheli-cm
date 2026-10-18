package io.github.mcheliceport.wingman.block;

import io.github.mcheliceport.net.ModNetwork;
import io.github.mcheliceport.net.OpenMarkerScreenPayload;
import io.github.mcheliceport.wingman.registry.MarkerRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * Upstream's {@code WingmanMarkerBlock}: marks bases, runway ends, parking spots, helipads and
 * waypoints for AI wingmen. A new marker is a parking spot, as upstream's block entity started.
 * Right-clicking opens the marker screen.
 *
 * <p>Upstream kept the marker type in its block entity and only showed it through the block's
 * model; the port stores the type in the block state and the rest in {@link WingmanMarkerBlockEntity}.
 */
public class WingmanMarkerBlock extends Block implements EntityBlock {

    public static final EnumProperty<MarkerType> TYPE = EnumProperty.create("type", MarkerType.class);

    public WingmanMarkerBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(TYPE, MarkerType.PARKING));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TYPE);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WingmanMarkerBlockEntity(pos, state);
    }

    /**
     * Upstream {@code onBlockActivated}: the server sends the marker's settings and the client
     * opens the marker screen. Upstream opened its base screen for a base marker; until that screen
     * is ported, a base marker opens the marker screen too.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        if (level.getBlockEntity(pos) instanceof WingmanMarkerBlockEntity marker && player instanceof ServerPlayer sp) {
            ModNetwork.sendToPlayer(sp, new OpenMarkerScreenPayload(pos, marker.getMarkerType(), marker.getMarkerId(),
                    marker.getBaseId(), marker.getParkingHeading()));
        }
        return InteractionResult.SUCCESS;
    }

    /** Upstream {@code autoId}: the type's prefix and one more than the markers of that type. */
    public static String autoId(ServerLevel level, MarkerType type) {
        String prefix = switch (type) {
            case PARKING -> "p";
            case RUNWAY_A -> "ra";
            case RUNWAY_B -> "rb";
            case HELIPAD -> "hp";
            case HELIPAD_B -> "hpb";
            default -> "wp";
        };
        return prefix + "_" + (MarkerRegistry.findByType(level, type).size() + 1);
    }
}
