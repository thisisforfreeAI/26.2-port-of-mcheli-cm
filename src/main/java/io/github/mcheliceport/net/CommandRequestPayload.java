package io.github.mcheliceport.net;

import io.github.mcheliceport.McheliCePort;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Upstream {@code PacketHandleCommand}: what {@code /mcheli sendss} and {@code /mcheli modlist} ask a
 * player's client for, answered with {@link ScreenshotChunkPayload} or {@link ModListPayload} under
 * the same {@code requestId}.
 */
public record CommandRequestPayload(int requestId, Action action) implements CustomPacketPayload {

    public enum Action {
        SCREENSHOT,
        MOD_LIST
    }

    public static final Type<CommandRequestPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "command_request"));

    public static final StreamCodec<ByteBuf, CommandRequestPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CommandRequestPayload::requestId,
            ByteBufCodecs.VAR_INT.map(i -> Action.values()[Math.floorMod(i, Action.values().length)], Action::ordinal),
            CommandRequestPayload::action,
            CommandRequestPayload::new);

    @Override
    public Type<CommandRequestPayload> type() {
        return TYPE;
    }
}
