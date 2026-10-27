package io.github.mcheliceport.net;

import io.github.mcheliceport.McheliCePort;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** One optional S2C notification per confirmed conventional vehicle kill. No victim body edits. */
public record VehicleBloodPayload(UUID victim, double x, double y, double z, float width, float height,
        float dx, float dy, float dz, byte cause, int seed) implements CustomPacketPayload {
    public static final Type<VehicleBloodPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "vehicle_blood"));
    public static final StreamCodec<FriendlyByteBuf, VehicleBloodPayload> CODEC = StreamCodec.of((b,p) -> {
        b.writeUUID(p.victim); b.writeDouble(p.x); b.writeDouble(p.y); b.writeDouble(p.z);
        b.writeFloat(p.width); b.writeFloat(p.height); b.writeFloat(p.dx); b.writeFloat(p.dy); b.writeFloat(p.dz);
        b.writeByte(p.cause); b.writeInt(p.seed);
    }, b -> new VehicleBloodPayload(b.readUUID(), b.readDouble(), b.readDouble(), b.readDouble(),
            b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), b.readByte(), b.readInt()));

    public boolean valid() {
        float length = dx*dx + dy*dy + dz*dz;
        return Double.isFinite(x) && Math.abs(x) <= 30_000_000 && Double.isFinite(z) && Math.abs(z) <= 30_000_000
                && Double.isFinite(y) && Math.abs(y) <= 200_000
                && Float.isFinite(width) && width >= .1F && width <= 3
                && Float.isFinite(height) && height >= .1F && height <= 4
                && Float.isFinite(length) && length <= 1.01F && cause >= 0 && cause <= 2;
    }
    @Override public Type<VehicleBloodPayload> type() { return TYPE; }
}
