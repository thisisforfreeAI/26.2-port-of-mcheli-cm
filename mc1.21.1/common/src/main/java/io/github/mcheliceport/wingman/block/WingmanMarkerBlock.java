package io.github.mcheliceport.wingman.block;

import io.github.mcheliceport.net.ModNetwork;
import io.github.mcheliceport.net.OpenBaseScreenPayload;
import io.github.mcheliceport.net.OpenMarkerScreenPayload;
import io.github.mcheliceport.wingman.MissionDispatch;
import io.github.mcheliceport.wingman.registry.MarkerRegistry;
import io.github.mcheliceport.wingman.registry.TaxiRouteRegistry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
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
                            marker.getBaseId(), marker.getParkingHeading(), nearbyBases((ServerLevel) level, pos),
                            otherIds((ServerLevel) level, pos)));
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Upstream {@code openBaseGui}: the base's taxi routes, its markers by type and the aircraft
     * near it. Waypoints without the base set still show when within 500 blocks of the base marker,
     * so one placed without its parent base can be used.
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
                runwayB, helipads, helipadB, waypoints, MissionDispatch.nearbyAircraft(level, pos));
    }

    /**
     * Upstream {@code autoId} gave the type's prefix and one more than the markers of that type, which
     * repeated an ID once a marker changed type (a parking spot turned helipad kept {@code p_1}, and
     * the next parking spot took {@code p_1} again). The port takes the lowest number no marker uses.
     */
    public static String autoId(ServerLevel level, MarkerType type) {
        return autoId(level, type, null);
    }

    /** {@link #autoId(ServerLevel, MarkerType)}, not counting the marker at the position. */
    public static String autoId(ServerLevel level, MarkerType type, @Nullable BlockPos self) {
        Set<String> taken = new HashSet<>();
        for (MarkerRegistry.MarkerInfo m : MarkerRegistry.snapshot(level)) {
            if (!m.pos().equals(self)) {
                taken.add(m.id());
            }
        }
        return nextAutoId(type, taken);
    }

    /** The type's prefix and the lowest number not taken, as {@code hp_2}. */
    public static String nextAutoId(MarkerType type, Collection<String> taken) {
        for (int n = 1; ; n++) {
            String id = type.idPrefix() + "_" + n;
            if (!taken.contains(id)) {
                return id;
            }
        }
    }

    /** Whether the ID looks automatic for some type, as {@code p_3}. */
    public static boolean isAutoId(String id) {
        int sep = id.lastIndexOf('_');
        if (sep <= 0 || sep == id.length() - 1 || !id.substring(sep + 1).chars().allMatch(Character::isDigit)) {
            return false;
        }
        String prefix = id.substring(0, sep);
        for (MarkerType type : MarkerType.values()) {
            if (type.idPrefix().equals(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The ID a marker keeps when it becomes the type: a new ID when it has none or an automatic one
     * of another type ({@code p_1} becoming a helipad is {@code hp_1}), else its own.
     */
    public static String idForType(ServerLevel level, BlockPos pos, String id, MarkerType type) {
        if (type != MarkerType.BASE && (id.isEmpty() || isAutoId(id) && !id.startsWith(type.idPrefix() + "_"))) {
            return autoId(level, type, pos);
        }
        return id;
    }

    /** Base markers within 500 blocks of the position, nearest first, for the marker screen's base picker. */
    public static List<String> nearbyBases(ServerLevel level, BlockPos pos) {
        List<MarkerRegistry.MarkerInfo> bases = new ArrayList<>();
        for (MarkerRegistry.MarkerInfo m : MarkerRegistry.findByType(level, MarkerType.BASE)) {
            if (!m.id().isEmpty() && m.pos().distSqr(pos) <= 500.0 * 500.0) {
                bases.add(m);
            }
        }
        bases.sort(Comparator.comparingDouble(m -> m.pos().distSqr(pos)));
        List<String> ids = new ArrayList<>();
        for (MarkerRegistry.MarkerInfo m : bases) {
            if (!ids.contains(m.id()) && ids.size() < 16) {
                ids.add(m.id());
            }
        }
        return ids;
    }

    /** Every other marker's ID, so the marker screen can warn of a duplicate. */
    public static List<String> otherIds(ServerLevel level, BlockPos pos) {
        Set<String> ids = new TreeSet<>();
        for (MarkerRegistry.MarkerInfo m : MarkerRegistry.snapshot(level)) {
            if (!m.pos().equals(pos) && !m.id().isEmpty()) {
                ids.add(m.id());
            }
        }
        return new ArrayList<>(ids);
    }
}
