package io.github.mcheliceport.net;

import io.github.mcheliceport.McheliCePort;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** The server reloaded the rider's weapon by hand; the client does too. Upstream {@code PacketSyncReload}. */
public record SyncReloadPayload(int aircraftId) implements CustomPacketPayload {

    public static final Type<SyncReloadPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "sync_reload"));

    public static final StreamCodec<FriendlyByteBuf, SyncReloadPayload> CODEC = StreamCodec.of(
            (buf, p) -> buf.writeVarInt(p.aircraftId), buf -> new SyncReloadPayload(buf.readVarInt()));

    @Override
    public Type<SyncReloadPayload> type() {
        return TYPE;
    }
}
