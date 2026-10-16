package io.github.mcheliceport.net;

import io.github.mcheliceport.McheliCePort;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Upstream RackAction, separate so existing vehicle-control layouts stay stable. */
public record RackControlPayload(int aircraftId, Action action) implements CustomPacketPayload {
    public enum Action { NONE, LOAD, UNLOAD, RIDE, LEAVE }
    public static final Type<RackControlPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "rack_control"));
    public static final StreamCodec<FriendlyByteBuf, RackControlPayload> CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeVarInt(p.aircraftId); buf.writeByte(p.action.ordinal()); },
            buf -> { int id = buf.readVarInt(); int action = buf.readUnsignedByte();
                return new RackControlPayload(id, action < Action.values().length ? Action.values()[action] : Action.NONE); });
    @Override public Type<RackControlPayload> type() { return TYPE; }
}
