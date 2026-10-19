package io.github.mcheliceport.net;

import io.github.mcheliceport.McheliCePort;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Upstream {@code PacketImgDataChunk}: one piece of the PNG a client sends for {@code /mcheli sendss},
 * the {@code data} at {@code offset} of a {@code total}-byte image. A {@code total} of
 * {@link #REFUSED} says the player does not allow screen captures.
 */
public record ScreenshotChunkPayload(int requestId, int offset, int total, byte[] data) implements CustomPacketPayload {

    public static final int REFUSED = -1;
    /** Under the 32,767 bytes a server-bound custom payload may hold. */
    public static final int MAX_CHUNK = 30_000;

    public static final Type<ScreenshotChunkPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "screenshot_chunk"));

    public static final StreamCodec<ByteBuf, ScreenshotChunkPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ScreenshotChunkPayload::requestId,
            ByteBufCodecs.VAR_INT, ScreenshotChunkPayload::offset,
            ByteBufCodecs.VAR_INT, ScreenshotChunkPayload::total,
            ByteBufCodecs.byteArray(MAX_CHUNK), ScreenshotChunkPayload::data,
            ScreenshotChunkPayload::new);

    public static ScreenshotChunkPayload refused(int requestId) {
        return new ScreenshotChunkPayload(requestId, 0, REFUSED, new byte[0]);
    }

    @Override
    public Type<ScreenshotChunkPayload> type() {
        return TYPE;
    }
}
