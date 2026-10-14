package io.github.mcheliceport.net;

import io.github.mcheliceport.McheliCePort;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** A vehicle started dispensing chaff for {@code useTick} ticks. Upstream {@code PacketChaffUse}. */
public record ChaffUsePayload(int aircraftId, int useTick) implements CustomPacketPayload {

    public static final Type<ChaffUsePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "chaff_use"));

    public static final StreamCodec<FriendlyByteBuf, ChaffUsePayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.aircraftId);
                buf.writeVarInt(p.useTick);
            },
            buf -> new ChaffUsePayload(buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<ChaffUsePayload> type() {
        return TYPE;
    }
}
