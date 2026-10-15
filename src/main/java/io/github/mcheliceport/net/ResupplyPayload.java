package io.github.mcheliceport.net;

import io.github.mcheliceport.McheliCePort;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** The aircraft menu's reload button for one weapon set. Upstream {@code PacketRequestResupply}. */
public record ResupplyPayload(int aircraftId, int weaponId) implements CustomPacketPayload {

    public static final Type<ResupplyPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "resupply"));

    public static final StreamCodec<FriendlyByteBuf, ResupplyPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.aircraftId);
                buf.writeVarInt(p.weaponId);
            },
            buf -> new ResupplyPayload(buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<ResupplyPayload> type() {
        return TYPE;
    }
}
