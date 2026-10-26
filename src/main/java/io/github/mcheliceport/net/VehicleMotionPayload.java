package io.github.mcheliceport.net;

import io.github.mcheliceport.McheliCePort;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * M9: a fast vehicle's velocity, server to the clients watching it, every two ticks. Optional:
 * clients without it keep vanilla interpolation.
 */
public record VehicleMotionPayload(int entityId, float vx, float vy, float vz) implements CustomPacketPayload {

    public static final Type<VehicleMotionPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "vehicle_motion"));

    public static final StreamCodec<FriendlyByteBuf, VehicleMotionPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.entityId);
                buf.writeFloat(p.vx);
                buf.writeFloat(p.vy);
                buf.writeFloat(p.vz);
            },
            buf -> new VehicleMotionPayload(buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat()));

    /** Finite and no faster than 20 blocks a tick. */
    public boolean valid() {
        double sq = (double) vx * vx + (double) vy * vy + (double) vz * vz;
        return this.entityId >= 0 && Double.isFinite(sq) && sq <= 400.0;
    }

    @Override
    public Type<VehicleMotionPayload> type() {
        return TYPE;
    }
}
