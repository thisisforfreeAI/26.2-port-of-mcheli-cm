package io.github.mcheliceport.net;

import io.github.mcheliceport.McheliCePort;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * The hand-held Stinger's and Javelin's controls (upstream {@code PacketPlayerLightWeaponControl}):
 * fire at the locked target in a weapon mode, finish a reload, or switch night vision (0 leaves it,
 * 1 switches it off, 2 on). Upstream also sent the firing position; the server uses the player's
 * eyes instead.
 */
public record LightWeaponControlPayload(boolean useWeapon, int targetId, int weaponMode, boolean reload,
        byte cameraMode) implements CustomPacketPayload {

    public static final byte NIGHT_VISION_OFF = 1;
    public static final byte NIGHT_VISION_ON = 2;

    public static final Type<LightWeaponControlPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "light_weapon_control"));

    public static final StreamCodec<FriendlyByteBuf, LightWeaponControlPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBoolean(p.useWeapon);
                buf.writeVarInt(p.targetId);
                buf.writeVarInt(p.weaponMode);
                buf.writeBoolean(p.reload);
                buf.writeByte(p.cameraMode);
            },
            buf -> new LightWeaponControlPayload(buf.readBoolean(), buf.readVarInt(), buf.readVarInt(),
                    buf.readBoolean(), buf.readByte()));

    @Override
    public Type<LightWeaponControlPayload> type() {
        return TYPE;
    }
}
