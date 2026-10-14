package io.github.mcheliceport.net;

import io.github.mcheliceport.McheliCePort;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** The rider set (or cleared, 0) the airburst distance of their weapon. Upstream {@code PacketAirburstDistReset}. */
public record AirburstDistPayload(int aircraftId, int dist) implements CustomPacketPayload {

    public static final Type<AirburstDistPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "airburst_dist"));

    public static final StreamCodec<FriendlyByteBuf, AirburstDistPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.aircraftId);
                buf.writeVarInt(p.dist);
            },
            buf -> new AirburstDistPayload(buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<AirburstDistPayload> type() {
        return TYPE;
    }
}
