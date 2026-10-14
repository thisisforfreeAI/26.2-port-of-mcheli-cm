package io.github.mcheliceport.net;

import io.github.mcheliceport.McheliCePort;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * The pilot's attitude (upstream {@code PacketIndRotation}): the pilot's client owns the
 * vehicle's rotation and sends it whenever it changes. Other clients get it through the vehicle's
 * tracked rotation and synced roll.
 */
public record AircraftAttitudePayload(int entityId, float yaw, float pitch, float roll, boolean rollReversed)
        implements CustomPacketPayload {

    public static final Type<AircraftAttitudePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "aircraft_attitude"));

    public static final StreamCodec<FriendlyByteBuf, AircraftAttitudePayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.entityId);
                buf.writeFloat(p.yaw);
                buf.writeFloat(p.pitch);
                buf.writeFloat(p.roll);
                buf.writeBoolean(p.rollReversed);
            },
            buf -> new AircraftAttitudePayload(buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                    buf.readBoolean()));

    @Override
    public Type<AircraftAttitudePayload> type() {
        return TYPE;
    }
}
