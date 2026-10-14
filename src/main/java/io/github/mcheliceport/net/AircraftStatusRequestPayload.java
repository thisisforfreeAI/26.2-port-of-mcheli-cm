package io.github.mcheliceport.net;

import io.github.mcheliceport.McheliCePort;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** A client asks for a vehicle's seat status on its first tick (upstream {@code PacketStatusRequest}). */
public record AircraftStatusRequestPayload(int entityId) implements CustomPacketPayload {

    public static final Type<AircraftStatusRequestPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "aircraft_status_request"));

    public static final StreamCodec<FriendlyByteBuf, AircraftStatusRequestPayload> CODEC = StreamCodec.of(
            (buf, p) -> buf.writeVarInt(p.entityId), buf -> new AircraftStatusRequestPayload(buf.readVarInt()));

    @Override
    public Type<AircraftStatusRequestPayload> type() {
        return TYPE;
    }
}
