package io.github.mcheliceport.net;

import io.github.mcheliceport.McheliCePort;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Upstream {@code PacketPlannerData}, sent by {@code /wingman gui}: the aircraft the mission planner
 * can send, the saved routes, the markers its route editor offers and where the player stands.
 */
public record PlannerDataPayload(List<Aircraft> aircraft, List<Route> routes, List<Marker> markers, double playerX,
        double playerY, double playerZ) implements CustomPacketPayload {

    /** The most aircraft, routes and markers sent. */
    public static final int MAX_AIRCRAFT = 256;
    public static final int MAX_ROUTES = 256;
    public static final int MAX_MARKERS = 512;

    /** Upstream's {@code UavDto}: an aircraft, its mission state and the node it is on. */
    public record Aircraft(UUID uuid, String name, String state, int nodeIdx, int nodeCount) {
        public static final StreamCodec<ByteBuf, Aircraft> CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Aircraft::uuid, ByteBufCodecs.stringUtf8(64), Aircraft::name,
                ByteBufCodecs.stringUtf8(32), Aircraft::state, ByteBufCodecs.VAR_INT, Aircraft::nodeIdx,
                ByteBufCodecs.VAR_INT, Aircraft::nodeCount, Aircraft::new);
    }

    /** Upstream's {@code RouteDto}: a saved route and its nodes' text. */
    public record Route(String name, List<String> nodes) {
        public static final StreamCodec<ByteBuf, Route> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(64), Route::name,
                ByteBufCodecs.stringUtf8(160).apply(ByteBufCodecs.list(64)), Route::nodes, Route::new);
    }

    /** Upstream's {@code MarkerDto}: a marker's type name, ID and position. */
    public record Marker(String type, String id, BlockPos pos) {
        public static final StreamCodec<ByteBuf, Marker> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(16), Marker::type, ByteBufCodecs.stringUtf8(64), Marker::id,
                BlockPos.STREAM_CODEC, Marker::pos, Marker::new);
    }

    public static final Type<PlannerDataPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "planner_data"));

    public static final StreamCodec<ByteBuf, PlannerDataPayload> CODEC = StreamCodec.composite(
            Aircraft.CODEC.apply(ByteBufCodecs.list(MAX_AIRCRAFT)), PlannerDataPayload::aircraft,
            Route.CODEC.apply(ByteBufCodecs.list(MAX_ROUTES)), PlannerDataPayload::routes,
            Marker.CODEC.apply(ByteBufCodecs.list(MAX_MARKERS)), PlannerDataPayload::markers,
            ByteBufCodecs.DOUBLE, PlannerDataPayload::playerX,
            ByteBufCodecs.DOUBLE, PlannerDataPayload::playerY,
            ByteBufCodecs.DOUBLE, PlannerDataPayload::playerZ,
            PlannerDataPayload::new);

    @Override
    public Type<PlannerDataPayload> type() {
        return TYPE;
    }
}
