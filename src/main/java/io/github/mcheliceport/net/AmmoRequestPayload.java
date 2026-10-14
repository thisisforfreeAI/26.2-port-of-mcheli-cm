package io.github.mcheliceport.net;

import io.github.mcheliceport.McheliCePort;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** A rider asks for a weapon's ammunition counts ({@code weaponId} -1: all). Upstream {@code PacketIndNotifyAmmoNum}. */
public record AmmoRequestPayload(int aircraftId, int weaponId) implements CustomPacketPayload {

    public static final Type<AmmoRequestPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "ammo_request"));

    public static final StreamCodec<FriendlyByteBuf, AmmoRequestPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.aircraftId);
                buf.writeVarInt(p.weaponId);
            },
            buf -> new AmmoRequestPayload(buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<AmmoRequestPayload> type() {
        return TYPE;
    }
}
