package io.github.mcheliceport.net;

import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.wingman.block.MarkerType;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Upstream {@code PacketOpenMarkerGui}: a marker's settings, for the marker screen. The port adds
 * the bases near the marker, for the screen's base picker, and the other markers' IDs, so it can
 * warn of a duplicate and suggest a free automatic ID.
 */
public record OpenMarkerScreenPayload(BlockPos pos, MarkerType markerType, String id, String baseId, int parkingHeading,
        List<String> nearbyBases, List<String> otherIds) implements CustomPacketPayload {

    public static final Type<OpenMarkerScreenPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "open_marker_screen"));

    private static final StreamCodec<FriendlyByteBuf, MarkerType> MARKER_TYPE =
            ByteBufCodecs.VAR_INT.map(i -> MarkerType.values()[Math.floorMod(i, MarkerType.values().length)],
                    MarkerType::ordinal).cast();

    private static final StreamCodec<FriendlyByteBuf, String> IDS = ByteBufCodecs.stringUtf8(64).cast();

    public static final StreamCodec<FriendlyByteBuf, OpenMarkerScreenPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, OpenMarkerScreenPayload::pos, MARKER_TYPE, OpenMarkerScreenPayload::markerType,
            ByteBufCodecs.stringUtf8(64), OpenMarkerScreenPayload::id, ByteBufCodecs.stringUtf8(64), OpenMarkerScreenPayload::baseId,
            ByteBufCodecs.VAR_INT, OpenMarkerScreenPayload::parkingHeading, IDS.apply(ByteBufCodecs.list(64)),
            OpenMarkerScreenPayload::nearbyBases, IDS.apply(ByteBufCodecs.list(4096)), OpenMarkerScreenPayload::otherIds,
            OpenMarkerScreenPayload::new);

    @Override
    public Type<OpenMarkerScreenPayload> type() {
        return TYPE;
    }
}
