package io.github.mcheliceport.net;

import io.github.mcheliceport.McheliCePort;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Upstream {@code PacketSendModlist}: the lines a client sends for {@code /mcheli modlist}. As
 * upstream, a client only shares them while its {@code debugLog} is on; otherwise {@code shared} is
 * false and there are no lines.
 */
public record ModListPayload(int requestId, boolean shared, List<String> lines) implements CustomPacketPayload {

    public static final int MAX_LINES = 256;
    public static final int MAX_LINE_LENGTH = 256;

    public static final Type<ModListPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "mod_list"));

    public static final StreamCodec<ByteBuf, ModListPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ModListPayload::requestId,
            ByteBufCodecs.BOOL, ModListPayload::shared,
            ByteBufCodecs.stringUtf8(MAX_LINE_LENGTH).apply(ByteBufCodecs.list(MAX_LINES)),
            ModListPayload::lines,
            ModListPayload::new);

    public ModListPayload {
        lines = List.copyOf(lines);
    }

    @Override
    public Type<ModListPayload> type() {
        return TYPE;
    }
}
