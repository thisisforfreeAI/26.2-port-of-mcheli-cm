package io.github.mcheliceport.wingman.block;

import io.github.mcheliceport.net.ModNetwork;
import io.github.mcheliceport.net.OpenBaseScreenPayload;
import io.github.mcheliceport.net.OpenMarkerScreenPayload;
import io.github.mcheliceport.wingman.registry.MarkerRegistry;
import io.github.mcheliceport.wingman.registry.TaxiRouteRegistry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
 * Right-clicking opens the marker screen, or a base marker's base screen.
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
     * Upstream {@code onBlockActivated}: a base marker opens the base screen, any other marker the
     * marker screen, with what the server knows.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        if (level.getBlockEntity(pos) instanceof WingmanMarkerBlockEntity marker && player instanceof ServerPlayer sp) {
            ModNetwork.sendToPlayer(sp, marker.getMarkerType() == MarkerType.BASE
                    ? baseScreen((ServerLevel) level, pos, marker.getMarkerId())
                    : new OpenMarkerScreenPayload(pos, marker.getMarkerType(), marker.getMarkerId(),
                            marker.getBaseId(), marker.getParkingHeading()));
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Upstream {@code openBaseGui}: the base's taxi routes and its markers by type. Waypoints
     * without the base set still show when within 500 blocks of the base marker, so one placed
     * without its parent base can be used.
     */
    public static OpenBaseScreenPayload baseScreen(ServerLevel level, BlockPos pos, String baseId) {
        List<OpenBaseScreenPayload.Marker> parking = new ArrayList<>();
        List<OpenBaseScreenPayload.Marker> runwayA = new ArrayList<>();
        List<OpenBaseScreenPayload.Marker> runwayB = new ArrayList<>();
        List<OpenBaseScreenPayload.Marker> helipads = new ArrayList<>();
        List<OpenBaseScreenPayload.Marker> helipadB = new ArrayList<>();
        List<OpenBaseScreenPayload.Marker> waypoints = new ArrayList<>();
        Set<String> waypointIds = new HashSet<>();
        for (MarkerRegistry.MarkerInfo m : MarkerRegistry.findChildren(level, baseId)) {
            var dto = new OpenBaseScreenPayload.Marker(m.id(), m.pos());
            switch (m.type()) {
                case PARKING -> parking.add(dto);
                case WAYPOINT -> {
                    waypoints.add(dto);
                    waypointIds.add(m.id());
                }
                case RUNWAY_A -> runwayA.add(dto);
                case RUNWAY_B -> runwayB.add(dto);
                case HELIPAD -> helipads.add(dto);
                case HELIPAD_B -> helipadB.add(dto);
                default -> {
                }
            }
        }
        for (MarkerRegistry.MarkerInfo m : MarkerRegistry.snapshot(level)) {
            double dx = m.pos().getX() - pos.getX();
            double dz = m.pos().getZ() - pos.getZ();
            if (m.type() == MarkerType.WAYPOINT && !waypointIds.contains(m.id()) && dx * dx + dz * dz <= 500.0 * 500.0) {
                waypoints.add(new OpenBaseScreenPayload.Marker(m.id(), m.pos()));
            }
        }
        return new OpenBaseScreenPayload(pos, baseId, TaxiRouteRegistry.getForBase(level, baseId), parking, runwayA,
                runwayB, helipads, helipadB, waypoints);
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
