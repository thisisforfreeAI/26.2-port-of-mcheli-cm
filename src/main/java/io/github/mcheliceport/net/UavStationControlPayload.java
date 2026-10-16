package io.github.mcheliceport.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Only the currently mounted station can receive reopen/exit controls. Menu actions use vanilla menu IDs. */
public record UavStationControlPayload(int stationId, boolean exit) implements CustomPacketPayload {
    public static final Type<UavStationControlPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("mcheli_ce_port", "uav_station_control"));
    public static final StreamCodec<FriendlyByteBuf, UavStationControlPayload> CODEC = StreamCodec.of(
            (b, p) -> { b.writeVarInt(p.stationId); b.writeBoolean(p.exit); }, b -> new UavStationControlPayload(b.readVarInt(), b.readBoolean()));
    @Override public Type<UavStationControlPayload> type() { return TYPE; }
}
