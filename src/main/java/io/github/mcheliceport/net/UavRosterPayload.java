package io.github.mcheliceport.net;

import io.github.mcheliceport.item.UavTabletData;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/** Telemetry only: receiving a roster never grants control of an aircraft. */
public record UavRosterPayload(int menuId, int stationId, List<Row> rows, @Nullable UUID selected) implements CustomPacketPayload {
    public record Row(UUID id, String name, String status, int fuel, int hp, int maxHp, int distance) { }
    public UavRosterPayload { rows = List.copyOf(rows); if (rows.size() > UavTabletData.MAX) { throw new IllegalArgumentException("Too many UAVs"); } }
    public static final Type<UavRosterPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("mcheli_ce_port", "uav_roster"));
    public static final StreamCodec<FriendlyByteBuf, UavRosterPayload> CODEC = StreamCodec.of(UavRosterPayload::write, UavRosterPayload::read);
    private static void write(FriendlyByteBuf b, UavRosterPayload p) {
        b.writeVarInt(p.menuId); b.writeVarInt(p.stationId); b.writeBoolean(p.selected != null);
        if (p.selected != null) { b.writeUUID(p.selected); }
        b.writeVarInt(p.rows.size());
        for (var r : p.rows) {
            b.writeUUID(r.id); b.writeUtf(r.name, 128); b.writeUtf(r.status, 64);
            b.writeVarInt(r.fuel); b.writeVarInt(r.hp); b.writeVarInt(r.maxHp); b.writeVarInt(r.distance);
        }
    }
    private static UavRosterPayload read(FriendlyByteBuf b) {
        int menu = b.readVarInt(), station = b.readVarInt(); var selected = b.readBoolean() ? b.readUUID() : null;
        int count = b.readVarInt(); if (count < 0 || count > UavTabletData.MAX) { throw new io.netty.handler.codec.DecoderException("Invalid UAV roster size"); }
        var rows = new ArrayList<Row>();
        for (int i = 0; i < count; i++) { rows.add(new Row(b.readUUID(), b.readUtf(128), b.readUtf(64), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt())); }
        return new UavRosterPayload(menu, station, rows, selected);
    }
    @Override public Type<UavRosterPayload> type() { return TYPE; }
}
