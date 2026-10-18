package io.github.mcheliceport.net;

import io.github.mcheliceport.McheliCePort;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Upstream {@code PacketResetGPS}: the GPS point the player entered. */
public record GpsSetPayload(double x, double y, double z, boolean active) implements CustomPacketPayload {

    public static final Type<GpsSetPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "gps_set"));

    public static final StreamCodec<FriendlyByteBuf, GpsSetPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, GpsSetPayload::x, ByteBufCodecs.DOUBLE, GpsSetPayload::y,
            ByteBufCodecs.DOUBLE, GpsSetPayload::z, ByteBufCodecs.BOOL, GpsSetPayload::active,
            GpsSetPayload::new);

    @Override
    public Type<GpsSetPayload> type() {
        return TYPE;
    }
}
