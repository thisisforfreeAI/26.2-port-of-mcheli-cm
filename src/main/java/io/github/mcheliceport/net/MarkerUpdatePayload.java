package io.github.mcheliceport.net;

import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.wingman.block.MarkerType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Upstream {@code PacketMarkerUpdate}: the settings the marker screen saves. */
public record MarkerUpdatePayload(BlockPos pos, MarkerType markerType, String id, String baseId, int parkingHeading)
        implements CustomPacketPayload {

    public static final Type<MarkerUpdatePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "marker_update"));

    private static final StreamCodec<FriendlyByteBuf, MarkerType> MARKER_TYPE =
            ByteBufCodecs.VAR_INT.map(i -> MarkerType.values()[Math.floorMod(i, MarkerType.values().length)],
                    MarkerType::ordinal).cast();

    public static final StreamCodec<FriendlyByteBuf, MarkerUpdatePayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, MarkerUpdatePayload::pos, MARKER_TYPE, MarkerUpdatePayload::markerType,
            ByteBufCodecs.stringUtf8(64), MarkerUpdatePayload::id, ByteBufCodecs.stringUtf8(64), MarkerUpdatePayload::baseId,
            ByteBufCodecs.VAR_INT, MarkerUpdatePayload::parkingHeading, MarkerUpdatePayload::new);

    @Override
    public Type<MarkerUpdatePayload> type() {
        return TYPE;
    }
}
