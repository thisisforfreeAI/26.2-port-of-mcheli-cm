package io.github.mcheliceport.net;

import io.github.mcheliceport.McheliCePort;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** M10b: the commander marks what they look at for the crew (or clears the mark). */
public record CrewMarkPayload(int aircraftId) implements CustomPacketPayload {

    public static final Type<CrewMarkPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "crew_mark"));

    public static final StreamCodec<FriendlyByteBuf, CrewMarkPayload> CODEC = StreamCodec.of(
            (buf, p) -> buf.writeVarInt(p.aircraftId),
            buf -> new CrewMarkPayload(buf.readVarInt()));

    @Override
    public Type<CrewMarkPayload> type() {
        return TYPE;
    }
}
