package io.github.mcheliceport.net;

import io.github.mcheliceport.McheliCePort;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * The pilot's velocity and throttle, sent every tick in the default (client-authoritative) mode
 * with vanilla's vehicle move packet, so the server can carry on from them when the pilot gets
 * out and other clients see the rotor spin up. A port addition: upstream's server always
 * simulated the vehicle itself.
 */
public record AircraftMotionPayload(int entityId, double motionX, double motionY, double motionZ, float throttle)
        implements CustomPacketPayload {

    public static final Type<AircraftMotionPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "aircraft_motion"));

    public static final StreamCodec<FriendlyByteBuf, AircraftMotionPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.entityId);
                buf.writeDouble(p.motionX);
                buf.writeDouble(p.motionY);
                buf.writeDouble(p.motionZ);
                buf.writeFloat(p.throttle);
            },
            buf -> new AircraftMotionPayload(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readFloat()));

    @Override
    public Type<AircraftMotionPayload> type() {
        return TYPE;
    }
}
