package io.github.mcheliceport.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mcheliceport.config.ServerSettings;
import io.github.mcheliceport.entity.heli.HeliFlightModel;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.junit.jupiter.api.Test;

/** Exit criterion E8: every M3 payload survives its codec, and the control bits are CE's. */
class PayloadCodecTest {

    private static <T> T roundTrip(StreamCodec<FriendlyByteBuf, T> codec, T value) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        codec.encode(buf, value);
        T read = codec.decode(buf);
        assertEquals(0, buf.readableBytes(), "the codec reads everything it writes");
        return read;
    }

    @Test
    void heliControlRoundTrips() {
        PlayerControlSwitches switches = PlayerControlSwitches.NONE
                .with(PlayerControlSwitches.THROTTLE_UP, true)
                .with(PlayerControlSwitches.MOVE_RIGHT, true)
                .with(PlayerControlSwitches.USE_ECM_JAMMER, true);
        for (HeliControlPayload.UnmountAction unmount : HeliControlPayload.UnmountAction.values()) {
            for (HeliControlPayload.ModeSwitch mode : HeliControlPayload.ModeSwitch.values()) {
                HeliControlPayload p = new HeliControlPayload(123456, unmount, mode, HeliControlPayload.HatchSwitch.FOLD,
                        HeliControlPayload.GearSwitch.UNFOLD, switches, (byte) 2, HeliControlPayload.BladeStatus.FOLD);
                assertEquals(p, roundTrip(HeliControlPayload.CODEC, p));
            }
        }
        HeliControlPayload only = HeliControlPayload.switchesOnly(7, switches);
        assertEquals(only, roundTrip(HeliControlPayload.CODEC, only));
    }

    @Test
    void unknownEnumValuesReadAsNone() {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        buf.writeVarInt(1);
        buf.writeByte(99);
        buf.writeByte(-1);
        buf.writeByte(3);
        buf.writeByte(3);
        buf.writeShort(0);
        buf.writeByte(0);
        buf.writeByte(42);
        HeliControlPayload p = HeliControlPayload.CODEC.decode(buf);
        assertEquals(HeliControlPayload.UnmountAction.NONE, p.unmount());
        assertEquals(HeliControlPayload.ModeSwitch.NONE, p.modeSwitch());
        assertEquals(HeliControlPayload.HatchSwitch.NONE, p.hatch());
        assertEquals(HeliControlPayload.GearSwitch.NONE, p.gear());
        assertEquals(HeliControlPayload.BladeStatus.NONE, p.bladeStatus());
    }

    @Test
    void switchBitsMatchCe() {
        // DataPlayerControlAircraft.PlayerControlSwitches#serialize: ejectSeat is bit 0 ... useECMJammer bit 13.
        String[] ceOrder = {"ejectSeat", "useWeapon", "switchSearchLight", "useBrake", "switchGunnerStatus",
                "throttleUp", "throttleDown", "moveLeft", "moveRight", "openGui", "reload", "useChaff", "useAPS",
                "useECMJammer"};
        int[] port = {PlayerControlSwitches.EJECT_SEAT, PlayerControlSwitches.USE_WEAPON,
                PlayerControlSwitches.SWITCH_SEARCH_LIGHT, PlayerControlSwitches.USE_BRAKE,
                PlayerControlSwitches.SWITCH_GUNNER_STATUS, PlayerControlSwitches.THROTTLE_UP,
                PlayerControlSwitches.THROTTLE_DOWN, PlayerControlSwitches.MOVE_LEFT, PlayerControlSwitches.MOVE_RIGHT,
                PlayerControlSwitches.OPEN_GUI, PlayerControlSwitches.RELOAD, PlayerControlSwitches.USE_CHAFF,
                PlayerControlSwitches.USE_APS, PlayerControlSwitches.USE_ECM_JAMMER};
        assertEquals(ceOrder.length, PlayerControlSwitches.COUNT);
        for (int i = 0; i < port.length; i++) {
            assertEquals(i, port[i], ceOrder[i]);
            PlayerControlSwitches one = PlayerControlSwitches.NONE.with(port[i], true);
            assertEquals((short) (1 << i), one.mask(), ceOrder[i]);
            assertTrue(one.get(port[i]));
            assertFalse(one.with(port[i], false).get(port[i]));
        }
        PlayerControlSwitches s = new PlayerControlSwitches((short) ((1 << 5) | (1 << 6) | (1 << 7) | (1 << 8)
                | (1 << 9) | (1 << 4)));
        assertTrue(s.throttleUp() && s.throttleDown() && s.moveLeft() && s.moveRight() && s.openGui()
                && s.switchGunnerStatus());
    }

    @Test
    void otherPayloadsRoundTrip() {
        AircraftAttitudePayload attitude = new AircraftAttitudePayload(5, -170.25F, 89.5F, 179.75F, true);
        assertEquals(attitude, roundTrip(AircraftAttitudePayload.CODEC, attitude));
        AircraftMotionPayload motion = new AircraftMotionPayload(6, 0.123456789, -1.5, 3.0E-7, 0.85F);
        assertEquals(motion, roundTrip(AircraftMotionPayload.CODEC, motion));
        for (SeatControlPayload.Action action : SeatControlPayload.Action.values()) {
            SeatControlPayload seat = new SeatControlPayload(action);
            assertEquals(seat, roundTrip(SeatControlPayload.CODEC, seat));
        }
        AircraftStatusRequestPayload request = new AircraftStatusRequestPayload(99);
        assertEquals(request, roundTrip(AircraftStatusRequestPayload.CODEC, request));
        AircraftStatusResponsePayload response = new AircraftStatusResponsePayload(99, (byte) 4,
                new byte[] {-1, 0, 3});
        assertEquals(response, roundTrip(AircraftStatusResponsePayload.CODEC, response));
    }

    @Test
    void serverSettingsRoundTrip() {
        ServerSettings.Values values = new ServerSettings.Values(true,
                new HeliFlightModel.RotationLimits(true, 25.0F, -15.0F, 40.0F), false);
        ServerSettingsPayload p = ServerSettingsPayload.of(values);
        ServerSettingsPayload read = roundTrip(ServerSettingsPayload.CODEC, p);
        assertEquals(p, read);
        assertEquals(values, read.toValues());
        assertEquals(ServerSettings.Values.DEFAULT, ServerSettingsPayload.of(ServerSettings.Values.DEFAULT).toValues());
    }
}
