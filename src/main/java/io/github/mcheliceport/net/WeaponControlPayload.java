package io.github.mcheliceport.net;

import io.github.mcheliceport.McheliCePort;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * A rider's weapon controls: the weapon fields of upstream's {@code DataPlayerControlAircraft}
 * (switch weapon, use weapon with its options, the vehicle position the shot was predicted from and
 * the rider's aim, flare pattern) and its reload, chaff and APS switches. Any vehicle kind uses it,
 * so the helicopter control payload stays as M3 built it.
 *
 * @param switchWeapon the weapon id to select, or -1
 * @param flareType    the flare pattern started, or 0
 */
public record WeaponControlPayload(int entityId, byte switchWeapon, boolean useWeapon, int option1, int option2,
        double x, double y, double z, float userYaw, float userPitch, byte flareType, boolean reload,
        boolean useChaff, boolean useAps, boolean useEcm) implements CustomPacketPayload {

    public static final Type<WeaponControlPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "weapon_control"));

    private static final int USE_WEAPON = 1;
    private static final int RELOAD = 2;
    private static final int USE_CHAFF = 4;
    private static final int USE_APS = 8;
    private static final int USE_ECM = 16;

    public static final StreamCodec<FriendlyByteBuf, WeaponControlPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.entityId);
                buf.writeByte(p.switchWeapon);
                buf.writeByte((p.useWeapon ? USE_WEAPON : 0) | (p.reload ? RELOAD : 0) | (p.useChaff ? USE_CHAFF : 0)
                        | (p.useAps ? USE_APS : 0) | (p.useEcm ? USE_ECM : 0));
                if (p.useWeapon) {
                    buf.writeVarInt(p.option1);
                    buf.writeVarInt(p.option2);
                    buf.writeDouble(p.x);
                    buf.writeDouble(p.y);
                    buf.writeDouble(p.z);
                    buf.writeFloat(p.userYaw);
                    buf.writeFloat(p.userPitch);
                }
                buf.writeByte(p.flareType);
            },
            buf -> {
                int id = buf.readVarInt();
                byte sw = buf.readByte();
                int flags = buf.readByte();
                boolean use = (flags & USE_WEAPON) != 0;
                int o1 = use ? buf.readVarInt() : 0;
                int o2 = use ? buf.readVarInt() : 0;
                double x = use ? buf.readDouble() : 0.0;
                double y = use ? buf.readDouble() : 0.0;
                double z = use ? buf.readDouble() : 0.0;
                float yaw = use ? buf.readFloat() : 0.0F;
                float pitch = use ? buf.readFloat() : 0.0F;
                return new WeaponControlPayload(id, sw, use, o1, o2, x, y, z, yaw, pitch, buf.readByte(),
                        (flags & RELOAD) != 0, (flags & USE_CHAFF) != 0, (flags & USE_APS) != 0,
                        (flags & USE_ECM) != 0);
            });

    /** Nothing to do: the builder's starting point. */
    public static WeaponControlPayload none(int entityId) {
        return new WeaponControlPayload(entityId, (byte) -1, false, 0, 0, 0.0, 0.0, 0.0, 0.0F, 0.0F, (byte) 0, false,
                false, false, false);
    }

    public boolean isEmpty() {
        return this.switchWeapon < 0 && !this.useWeapon && this.flareType == 0 && !this.reload && !this.useChaff
                && !this.useAps && !this.useEcm;
    }

    public WeaponControlPayload withSwitchWeapon(int id) {
        return new WeaponControlPayload(this.entityId, (byte) id, this.useWeapon, this.option1, this.option2, this.x,
                this.y, this.z, this.userYaw, this.userPitch, this.flareType, this.reload, this.useChaff, this.useAps, this.useEcm);
    }

    public WeaponControlPayload withUseWeapon(int option1, int option2, double x, double y, double z, float yaw,
            float pitch) {
        return new WeaponControlPayload(this.entityId, this.switchWeapon, true, option1, option2, x, y, z, yaw, pitch,
                this.flareType, this.reload, this.useChaff, this.useAps, this.useEcm);
    }

    /** The same controls without the shot (M9 anti-cheat drops it). */
    public WeaponControlPayload withoutUseWeapon() {
        return new WeaponControlPayload(this.entityId, this.switchWeapon, false, 0, 0, 0.0, 0.0, 0.0, 0.0F, 0.0F,
                this.flareType, this.reload, this.useChaff, this.useAps, this.useEcm);
    }

    public WeaponControlPayload withFlare(int type) {
        return new WeaponControlPayload(this.entityId, this.switchWeapon, this.useWeapon, this.option1, this.option2,
                this.x, this.y, this.z, this.userYaw, this.userPitch, (byte) type, this.reload, this.useChaff,
                this.useAps, this.useEcm);
    }

    public WeaponControlPayload withReload() {
        return new WeaponControlPayload(this.entityId, this.switchWeapon, this.useWeapon, this.option1, this.option2,
                this.x, this.y, this.z, this.userYaw, this.userPitch, this.flareType, true, this.useChaff, this.useAps,
                this.useEcm);
    }

    public WeaponControlPayload withChaff() {
        return new WeaponControlPayload(this.entityId, this.switchWeapon, this.useWeapon, this.option1, this.option2,
                this.x, this.y, this.z, this.userYaw, this.userPitch, this.flareType, this.reload, true, this.useAps,
                this.useEcm);
    }

    public WeaponControlPayload withAps() {
        return new WeaponControlPayload(this.entityId, this.switchWeapon, this.useWeapon, this.option1, this.option2,
                this.x, this.y, this.z, this.userYaw, this.userPitch, this.flareType, this.reload, this.useChaff, true,
                this.useEcm);
    }

    public WeaponControlPayload withEcm() {
        return new WeaponControlPayload(this.entityId, this.switchWeapon, this.useWeapon, this.option1, this.option2,
                this.x, this.y, this.z, this.userYaw, this.userPitch, this.flareType, this.reload, this.useChaff,
                this.useAps, true);
    }

    @Override
    public Type<WeaponControlPayload> type() {
        return TYPE;
    }
}
