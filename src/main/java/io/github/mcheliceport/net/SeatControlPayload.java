package io.github.mcheliceport.net;

import io.github.mcheliceport.McheliCePort;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** A crew member's seat change (upstream {@code PacketSeatPlayerControl}). */
public record SeatControlPayload(Action action) implements CustomPacketPayload {

    public static final Type<SeatControlPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "seat_control"));

    public static final StreamCodec<FriendlyByteBuf, SeatControlPayload> CODEC = StreamCodec.of(
            (buf, p) -> buf.writeByte(p.action.ordinal()),
            buf -> {
                byte b = buf.readByte();
                Action[] values = Action.values();
                return new SeatControlPayload(b >= 0 && b < values.length ? values[b] : Action.IDLE);
            });

    @Override
    public Type<SeatControlPayload> type() {
        return TYPE;
    }

    /** Upstream {@code PlayerControlState}. */
    public enum Action {
        IDLE, NEXT, PREV, DISMOUNT
    }
}
