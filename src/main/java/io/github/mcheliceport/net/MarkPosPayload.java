package io.github.mcheliceport.net;

import io.github.mcheliceport.McheliCePort;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * The team's mark point (upstream {@code PacketMarkPos}); a {@code y} of 1000 or more clears it.
 */
public record MarkPosPayload(int x, int y, int z) implements CustomPacketPayload {

    public static final Type<MarkPosPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "mark_pos"));

    public static final StreamCodec<FriendlyByteBuf, MarkPosPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeInt(p.x);
                buf.writeInt(p.y);
                buf.writeInt(p.z);
            },
            buf -> new MarkPosPayload(buf.readInt(), buf.readInt(), buf.readInt()));

    @Override
    public Type<MarkPosPayload> type() {
        return TYPE;
    }
}
