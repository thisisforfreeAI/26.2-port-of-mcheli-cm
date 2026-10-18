package io.github.mcheliceport.net;

import io.github.mcheliceport.McheliCePort;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * A rangefinder spot (upstream {@code PacketRequestSpotEnemy}): the target filter of the
 * rangefinder's mode, or 0 to mark the point the player looks at.
 */
public record RangeFinderSpotPayload(int targetFilter) implements CustomPacketPayload {

    public static final Type<RangeFinderSpotPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "range_finder_spot"));

    public static final StreamCodec<FriendlyByteBuf, RangeFinderSpotPayload> CODEC = StreamCodec.of(
            (buf, p) -> buf.writeVarInt(p.targetFilter),
            buf -> new RangeFinderSpotPayload(buf.readVarInt()));

    @Override
    public Type<RangeFinderSpotPayload> type() {
        return TYPE;
    }
}
