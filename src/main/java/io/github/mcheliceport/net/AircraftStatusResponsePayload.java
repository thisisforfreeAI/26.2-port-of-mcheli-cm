package io.github.mcheliceport.net;

import io.github.mcheliceport.McheliCePort;
import java.util.Arrays;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * The answer to {@link AircraftStatusRequestPayload} (upstream {@code PacketStatusResponse}): the
 * number of seats including the pilot's, and each seat's current weapon. Weapons come with M5,
 * so every ID is -1 for now.
 */
public record AircraftStatusResponsePayload(int entityId, byte seatNum, byte[] weaponIds)
        implements CustomPacketPayload {

    public static final Type<AircraftStatusResponsePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "aircraft_status_response"));

    public static final StreamCodec<FriendlyByteBuf, AircraftStatusResponsePayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.entityId);
                buf.writeByte(p.seatNum);
                buf.writeByteArray(p.weaponIds);
            },
            buf -> new AircraftStatusResponsePayload(buf.readVarInt(), buf.readByte(), buf.readByteArray(256)));

    @Override
    public Type<AircraftStatusResponsePayload> type() {
        return TYPE;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof AircraftStatusResponsePayload other && this.entityId == other.entityId
                && this.seatNum == other.seatNum && Arrays.equals(this.weaponIds, other.weaponIds);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * this.entityId + this.seatNum) + Arrays.hashCode(this.weaponIds);
    }

    @Override
    public String toString() {
        return "AircraftStatusResponsePayload[entityId=" + this.entityId + ", seatNum=" + this.seatNum
                + ", weaponIds=" + Arrays.toString(this.weaponIds) + "]";
    }
}
