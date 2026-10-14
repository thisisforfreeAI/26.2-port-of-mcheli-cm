package io.github.mcheliceport.net;

import io.github.mcheliceport.McheliCePort;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Hands a just-fired TV missile to its vehicle on the clients, so the shooter's view follows it.
 * Upstream {@code PacketNotifyTVMissileEntity}.
 */
public record TvMissilePayload(int aircraftId, int missileId) implements CustomPacketPayload {

    public static final Type<TvMissilePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "tv_missile"));

    public static final StreamCodec<FriendlyByteBuf, TvMissilePayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.aircraftId);
                buf.writeVarInt(p.missileId);
            },
            buf -> new TvMissilePayload(buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<TvMissilePayload> type() {
        return TYPE;
    }
}
