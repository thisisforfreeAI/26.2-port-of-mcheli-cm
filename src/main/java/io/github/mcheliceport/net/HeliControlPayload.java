package io.github.mcheliceport.net;

import io.github.mcheliceport.McheliCePort;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * A rider's helicopter controls (upstream {@code PacketPlayerControlHeli}): the M3 subset of
 * {@code DataPlayerControlAircraft} and {@code DataPlayerControlVehicle}, in CE's order and
 * encodings (enum ordinals as bytes, the switch flags as a short). Weapon, rack, VTOL and chain
 * fields come with M5 and M6.
 *
 * <p>{@code switchCameraMode} is upstream's {@code CameraMode} field shifted by one: 0 leaves the
 * mode alone and {@code 1 + mode} sets it. Upstream used 0 for both "no change" and "normal", so
 * the server never heard about a switch back to normal and kept giving night vision.
 */
public record HeliControlPayload(int entityId, UnmountAction unmount, ModeSwitch modeSwitch, HatchSwitch hatch,
        GearSwitch gear, PlayerControlSwitches switches, byte switchFreeLook, BladeStatus bladeStatus,
        byte switchCameraMode) implements CustomPacketPayload {

    public static final Type<HeliControlPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "heli_control"));

    public static final StreamCodec<FriendlyByteBuf, HeliControlPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.entityId);
                buf.writeByte(p.unmount.ordinal());
                buf.writeByte(p.modeSwitch.ordinal());
                buf.writeByte(p.hatch.ordinal());
                buf.writeByte(p.gear.ordinal());
                buf.writeShort(p.switches.mask());
                buf.writeByte(p.switchFreeLook);
                buf.writeByte(p.bladeStatus.ordinal());
                buf.writeByte(p.switchCameraMode);
            },
            buf -> new HeliControlPayload(buf.readVarInt(), byOrdinal(UnmountAction.values(), buf.readByte()),
                    byOrdinal(ModeSwitch.values(), buf.readByte()), byOrdinal(HatchSwitch.values(), buf.readByte()),
                    byOrdinal(GearSwitch.values(), buf.readByte()), new PlayerControlSwitches(buf.readShort()),
                    buf.readByte(), byOrdinal(BladeStatus.values(), buf.readByte()), buf.readByte()));

    /** A payload that only carries the switches (sent while the controls change, and every 100 ticks). */
    public static HeliControlPayload switchesOnly(int entityId, PlayerControlSwitches switches) {
        return new HeliControlPayload(entityId, UnmountAction.NONE, ModeSwitch.NONE, HatchSwitch.NONE,
                GearSwitch.NONE, switches, (byte) 0, BladeStatus.NONE, (byte) 0);
    }

    private static <E extends Enum<E>> E byOrdinal(E[] values, byte ordinal) {
        // An unknown value from a newer or broken client counts as "nothing" rather than failing.
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : values[0];
    }

    @Override
    public Type<HeliControlPayload> type() {
        return TYPE;
    }

    /** Upstream {@code UnmountAction}. */
    public enum UnmountAction {
        NONE, UNMOUNT_SELF, UNMOUNT_CREW, UNMOUNT_AIRCRAFT
    }

    /** Upstream {@code ModeSwitch}. */
    public enum ModeSwitch {
        NONE, GUNNER_OFF, GUNNER_ON, HOVERING_OFF, HOVERING_ON
    }

    /** Upstream {@code HatchSwitch}. */
    public enum HatchSwitch {
        NONE, FOLD, UNFOLD
    }

    /** Upstream {@code GearSwitch}. */
    public enum GearSwitch {
        NONE, FOLD, UNFOLD
    }

    /** Upstream {@code DataPlayerControlVehicle.BladeStatus}. */
    public enum BladeStatus {
        NONE, UNFOLD, FOLD
    }
}
