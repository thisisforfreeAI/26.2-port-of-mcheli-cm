package io.github.mcheliceport.net;

import io.github.mcheliceport.McheliCePort;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Separate from visual packets so silent explosions remain silent, including out of visual range. */
public record ExplosionSoundPayload(double x, double y, double z, float power, float pitch) implements CustomPacketPayload {
    public ExplosionSoundPayload(double x, double y, double z, float power) { this(x, y, z, power, 0.7F); }
    public static final Type<ExplosionSoundPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "explosion_sound"));
    public static final StreamCodec<FriendlyByteBuf, ExplosionSoundPayload> CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeDouble(p.x); buf.writeDouble(p.y); buf.writeDouble(p.z); buf.writeFloat(p.power); buf.writeFloat(p.pitch); },
            buf -> new ExplosionSoundPayload(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readFloat()));
    @Override public Type<ExplosionSoundPayload> type() { return TYPE; }
}
