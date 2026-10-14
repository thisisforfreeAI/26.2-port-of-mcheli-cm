package io.github.mcheliceport.net;

import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.weapon.WeaponSet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * A seat's selected weapon and that weapon's ammunition and cooldowns, from the server. Upstream
 * {@code PacketSyncWeapon}.
 */
public record WeaponSyncPayload(int aircraftId, int seatId, int weaponId, short ammo, short reserveAmmo,
        short cooldown, short reloadCooldown) implements CustomPacketPayload {

    public static final Type<WeaponSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "weapon_sync"));

    public static final StreamCodec<FriendlyByteBuf, WeaponSyncPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.aircraftId);
                buf.writeVarInt(p.seatId);
                buf.writeVarInt(p.weaponId);
                buf.writeShort(p.ammo);
                buf.writeShort(p.reserveAmmo);
                buf.writeShort(p.cooldown);
                buf.writeShort(p.reloadCooldown);
            },
            buf -> new WeaponSyncPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readShort(),
                    buf.readShort(), buf.readShort(), buf.readShort()));

    public static WeaponSyncPayload of(AircraftEntity aircraft, int seatId, int weaponId, WeaponSet ws) {
        return new WeaponSyncPayload(aircraft.getId(), seatId, weaponId, (short) ws.getAmmo(),
                (short) ws.getRestAllAmmoNum(), (short) ws.cooldown, (short) ws.reloadCooldown);
    }

    @Override
    public Type<WeaponSyncPayload> type() {
        return TYPE;
    }
}
