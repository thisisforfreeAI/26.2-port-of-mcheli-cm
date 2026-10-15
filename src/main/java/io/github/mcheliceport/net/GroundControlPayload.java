package io.github.mcheliceport.net;

import io.github.mcheliceport.McheliCePort;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Ground controls with a held tank brake; existing heli/plane/ship layouts stay intact. */
public record GroundControlPayload(HeliControlPayload controls, boolean brake) implements CustomPacketPayload {
    public static final Type<GroundControlPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "ground_control"));
    public static final StreamCodec<FriendlyByteBuf, GroundControlPayload> CODEC = StreamCodec.of(
            (b, p) -> { HeliControlPayload.CODEC.encode(b, p.controls); b.writeBoolean(p.brake); },
            b -> new GroundControlPayload(HeliControlPayload.CODEC.decode(b), b.readBoolean()));
    @Override public Type<GroundControlPayload> type() { return TYPE; }
}
