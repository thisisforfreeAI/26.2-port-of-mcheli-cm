package io.github.mcheliceport.net;

import io.github.mcheliceport.McheliCePort;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Magazine and reserve counts from the server: every weapon set's ({@code all}), or one weapon's.
 * Upstream {@code PacketNotifyAmmoNum}.
 */
public record NotifyAmmoPayload(int aircraftId, boolean all, byte weaponId, short[] ammo, short[] restAmmo)
        implements CustomPacketPayload {

    public static final Type<NotifyAmmoPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "notify_ammo"));

    /** More weapon sets than any vehicle has; a longer list is refused. */
    private static final int MAX_WEAPONS = 256;

    public static final StreamCodec<FriendlyByteBuf, NotifyAmmoPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.aircraftId);
                buf.writeBoolean(p.all);
                buf.writeByte(p.weaponId);
                int n = Math.min(p.ammo.length, p.restAmmo.length);
                buf.writeVarInt(n);
                for (int i = 0; i < n; i++) {
                    buf.writeShort(p.ammo[i]);
                    buf.writeShort(p.restAmmo[i]);
                }
            },
            buf -> {
                int id = buf.readVarInt();
                boolean all = buf.readBoolean();
                byte wid = buf.readByte();
                int n = buf.readVarInt();
                if (n < 0 || n > MAX_WEAPONS) {
                    throw new IllegalArgumentException("Too many weapons in ammo payload: " + n);
                }
                short[] ammo = new short[n];
                short[] rest = new short[n];
                for (int i = 0; i < n; i++) {
                    ammo[i] = buf.readShort();
                    rest[i] = buf.readShort();
                }
                return new NotifyAmmoPayload(id, all, wid, ammo, rest);
            });

    @Override
    public Type<NotifyAmmoPayload> type() {
        return TYPE;
    }
}
