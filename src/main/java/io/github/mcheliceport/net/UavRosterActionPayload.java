package io.github.mcheliceport.net;

import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** UUIDs keep a stale row click from operating on a different aircraft after a roster change. */
public record UavRosterActionPayload(int menuId, int stationId, UUID uav, int action) implements CustomPacketPayload {
    public static final Type<UavRosterActionPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("mcheli_ce_port", "uav_roster_action"));
    public static final StreamCodec<FriendlyByteBuf, UavRosterActionPayload> CODEC = StreamCodec.of(
            (b, p) -> { b.writeVarInt(p.menuId); b.writeVarInt(p.stationId); b.writeUUID(p.uav); b.writeVarInt(p.action); },
            b -> new UavRosterActionPayload(b.readVarInt(), b.readVarInt(), b.readUUID(), b.readVarInt()));
    @Override public Type<UavRosterActionPayload> type() { return TYPE; }
}
