package io.github.mcheliceport.net;

import io.github.mcheliceport.McheliCePort;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * A laser beam to draw from {@code src} to {@code dest} for {@code life} ticks, with impact
 * particles when it hit something. Upstream {@code PacketWeaponLaserShooting}.
 *
 * @param renderStartDist how far from the source the beam starts to be drawn
 */
public record LaserShotPayload(Vec3 src, Vec3 dest, int argb, float width, int life, boolean pulsate,
        double renderStartDist, boolean hitSomething) implements CustomPacketPayload {

    public static final Type<LaserShotPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "laser_shot"));

    public static final StreamCodec<FriendlyByteBuf, LaserShotPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                writeVec(buf, p.src);
                writeVec(buf, p.dest);
                buf.writeInt(p.argb);
                buf.writeFloat(p.width);
                buf.writeVarInt(p.life);
                buf.writeBoolean(p.pulsate);
                buf.writeDouble(p.renderStartDist);
                buf.writeBoolean(p.hitSomething);
            },
            buf -> new LaserShotPayload(readVec(buf), readVec(buf), buf.readInt(), buf.readFloat(),
                    buf.readVarInt(), buf.readBoolean(), buf.readDouble(), buf.readBoolean()));

    private static void writeVec(FriendlyByteBuf buf, Vec3 v) {
        buf.writeDouble(v.x);
        buf.writeDouble(v.y);
        buf.writeDouble(v.z);
    }

    private static Vec3 readVec(FriendlyByteBuf buf) {
        return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    @Override
    public Type<LaserShotPayload> type() {
        return TYPE;
    }
}
