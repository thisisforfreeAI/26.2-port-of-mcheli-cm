package io.github.mcheliceport.net;

import io.github.mcheliceport.McheliCePort;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * A port addition, from the Mission tab of the base screen of the base marker at {@code pos}:
 * sends home every order and planner mission that returns to the marker's base.
 */
public record RecallOrdersPayload(BlockPos pos) implements CustomPacketPayload {

    public static final Type<RecallOrdersPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "recall_orders"));

    public static final StreamCodec<ByteBuf, RecallOrdersPayload> CODEC =
            BlockPos.STREAM_CODEC.map(RecallOrdersPayload::new, RecallOrdersPayload::pos);

    @Override
    public Type<RecallOrdersPayload> type() {
        return TYPE;
    }
}
