package io.github.mcheliceport.net;

import io.github.mcheliceport.McheliCePort;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * The laser designator operator's controls (upstream {@code PacketGLTDControl}): the camera mode
 * switched to (-1 for none), a strike called in, or leaving. Upstream's weapon switch and weapon
 * options were never used by the designator and are left out.
 */
public record GltdControlPayload(byte switchCameraMode, boolean useWeapon, boolean unmount)
        implements CustomPacketPayload {

    public static final Type<GltdControlPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "gltd_control"));

    public static final StreamCodec<FriendlyByteBuf, GltdControlPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeByte(p.switchCameraMode);
                buf.writeBoolean(p.useWeapon);
                buf.writeBoolean(p.unmount);
            },
            buf -> new GltdControlPayload(buf.readByte(), buf.readBoolean(), buf.readBoolean()));

    @Override
    public Type<GltdControlPayload> type() {
        return TYPE;
    }
}
