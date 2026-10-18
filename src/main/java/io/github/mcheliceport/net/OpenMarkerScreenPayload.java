package io.github.mcheliceport.net;

import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.wingman.block.MarkerType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Upstream {@code PacketOpenMarkerGui}: a marker's settings, for the marker screen. */
public record OpenMarkerScreenPayload(BlockPos pos, MarkerType markerType, String id, String baseId, int parkingHeading)
        implements CustomPacketPayload {

    public static final Type<OpenMarkerScreenPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "open_marker_screen"));

    private static final StreamCodec<FriendlyByteBuf, MarkerType> MARKER_TYPE =
            ByteBufCodecs.VAR_INT.map(i -> MarkerType.values()[Math.floorMod(i, MarkerType.values().length)],
                    MarkerType::ordinal).cast();

    public static final StreamCodec<FriendlyByteBuf, OpenMarkerScreenPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, OpenMarkerScreenPayload::pos, MARKER_TYPE, OpenMarkerScreenPayload::markerType,
            ByteBufCodecs.stringUtf8(64), OpenMarkerScreenPayload::id, ByteBufCodecs.stringUtf8(64), OpenMarkerScreenPayload::baseId,
            ByteBufCodecs.VAR_INT, OpenMarkerScreenPayload::parkingHeading, OpenMarkerScreenPayload::new);

    @Override
    public Type<OpenMarkerScreenPayload> type() {
        return TYPE;
    }
}
