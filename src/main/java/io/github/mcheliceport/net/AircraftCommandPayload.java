package io.github.mcheliceport.net;

import io.github.mcheliceport.McheliCePort;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * The aircraft menu's command field, sent when the menu closes. Upstream {@code PacketCommandSave}.
 */
public record AircraftCommandPayload(int aircraftId, String command) implements CustomPacketPayload {

    /** Upstream's {@code setMaxStringLength(512)}. */
    public static final int MAX_LENGTH = 512;

    public static final Type<AircraftCommandPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "aircraft_command"));

    public static final StreamCodec<FriendlyByteBuf, AircraftCommandPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.aircraftId);
                buf.writeUtf(p.command, MAX_LENGTH);
            },
            buf -> new AircraftCommandPayload(buf.readVarInt(), buf.readUtf(MAX_LENGTH)));

    @Override
    public Type<AircraftCommandPayload> type() {
        return TYPE;
    }
}
