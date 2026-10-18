package io.github.mcheliceport.net;

import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.wingman.mission.TaxiRoute;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Upstream {@code PacketOpenBaseGui}: a base marker's taxi routes and the markers its route editor
 * offers. Upstream also sent the aircraft near the base, for its Mission tab.
 */
public record OpenBaseScreenPayload(BlockPos pos, String baseId, List<TaxiRoute> routes, List<Marker> parking,
        List<Marker> runwayA, List<Marker> runwayB, List<Marker> helipads, List<Marker> helipadB,
        List<Marker> waypoints) implements CustomPacketPayload {

    /** Upstream's {@code MarkerDto}: a marker's ID and position. */
    public record Marker(String id, BlockPos pos) {
        public static final StreamCodec<ByteBuf, Marker> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(64), Marker::id, BlockPos.STREAM_CODEC, Marker::pos, Marker::new);
    }

    private static final StreamCodec<ByteBuf, List<Marker>> MARKERS = Marker.CODEC.apply(ByteBufCodecs.list(256));

    public static final Type<OpenBaseScreenPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "open_base_screen"));

    public static final StreamCodec<ByteBuf, OpenBaseScreenPayload> CODEC = new StreamCodec<>() {
        @Override
        public OpenBaseScreenPayload decode(ByteBuf buf) {
            return new OpenBaseScreenPayload(BlockPos.STREAM_CODEC.decode(buf), ByteBufCodecs.stringUtf8(64).decode(buf),
                    TaxiRoute.STREAM_CODEC.apply(ByteBufCodecs.list(256)).decode(buf), MARKERS.decode(buf),
                    MARKERS.decode(buf), MARKERS.decode(buf), MARKERS.decode(buf), MARKERS.decode(buf),
                    MARKERS.decode(buf));
        }

        @Override
        public void encode(ByteBuf buf, OpenBaseScreenPayload p) {
            BlockPos.STREAM_CODEC.encode(buf, p.pos());
            ByteBufCodecs.stringUtf8(64).encode(buf, p.baseId());
            TaxiRoute.STREAM_CODEC.apply(ByteBufCodecs.list(256)).encode(buf, p.routes());
            MARKERS.encode(buf, p.parking());
            MARKERS.encode(buf, p.runwayA());
            MARKERS.encode(buf, p.runwayB());
            MARKERS.encode(buf, p.helipads());
            MARKERS.encode(buf, p.helipadB());
            MARKERS.encode(buf, p.waypoints());
        }
    };

    @Override
    public Type<OpenBaseScreenPayload> type() {
        return TYPE;
    }
}
