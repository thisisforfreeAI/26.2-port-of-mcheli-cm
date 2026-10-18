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

    @Test void groundBrakeAndCommonControlsRoundTrip() {
        for (boolean brake : new boolean[] {false, true}) {
            var packet = new GroundControlPayload(HeliControlPayload.switchesOnly(318,
                    PlayerControlSwitches.NONE.with(PlayerControlSwitches.MOVE_LEFT, true)), brake);
            assertEquals(packet, roundTrip(GroundControlPayload.CODEC, packet));
        }
    }

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
                        HeliControlPayload.GearSwitch.UNFOLD, switches, (byte) 2, HeliControlPayload.BladeStatus.FOLD,
                        (byte) 2);
                assertEquals(p, roundTrip(HeliControlPayload.CODEC, p));
            }
        }
        HeliControlPayload only = HeliControlPayload.switchesOnly(7, switches);
        assertEquals(only, roundTrip(HeliControlPayload.CODEC, only));
    }

    @Test
    void planeControlsRoundTripAndRejectUnknownSwitchValues() {
        HeliControlPayload common = HeliControlPayload.switchesOnly(123, PlayerControlSwitches.NONE);
        for (PlaneControlPayload.PartSwitch vtol : PlaneControlPayload.PartSwitch.values()) {
            for (PlaneControlPayload.PartSwitch wing : PlaneControlPayload.PartSwitch.values()) {
                PlaneControlPayload p = new PlaneControlPayload(common, vtol, wing);
                assertEquals(p, roundTrip(PlaneControlPayload.CODEC, p));
                ShipControlPayload ship = new ShipControlPayload(common, vtol, wing);
                assertEquals(ship, roundTrip(ShipControlPayload.CODEC, ship));
            }
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            HeliControlPayload.CODEC.encode(buf, common);
            buf.writeByte(255); buf.writeByte(99);
            PlaneControlPayload read = PlaneControlPayload.CODEC.decode(buf);
            assertEquals(PlaneControlPayload.PartSwitch.NONE, read.vtol());
            assertEquals(PlaneControlPayload.PartSwitch.NONE, read.wing());
        } finally { buf.release(); }
    }

    @Test
    void explosionEffectRoundTrips() {
        java.util.List<net.minecraft.core.BlockPos> blocks = java.util.List.of(new net.minecraft.core.BlockPos(10, 64, -3),
                new net.minecraft.core.BlockPos(-120, -60, 300), new net.minecraft.core.BlockPos(5, 70, 0));
        ExplosionEffectPayload p = new ExplosionEffectPayload(4.5, 66.2, -1.25, 3.5F, true, blocks);
        assertEquals(p, roundTrip(ExplosionEffectPayload.CODEC, p));
        ExplosionEffectPayload empty = new ExplosionEffectPayload(0, 0, 0, 0, false, java.util.List.of());
        assertEquals(empty, roundTrip(ExplosionEffectPayload.CODEC, empty));
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
        buf.writeByte(0);
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
                new HeliFlightModel.RotationLimits(true, 25.0F, -15.0F, 40.0F), false, false);
        ServerSettingsPayload p = ServerSettingsPayload.of(values);
        ServerSettingsPayload read = roundTrip(ServerSettingsPayload.CODEC, p);
        assertEquals(p, read);
        assertEquals(values, read.toValues());
        assertEquals(ServerSettings.Values.DEFAULT, ServerSettingsPayload.of(ServerSettings.Values.DEFAULT).toValues());
    }

    @Test
    void spottingRoundTrips() {
        SpotEnemyPayload spot = new SpotEnemyPayload(15, new int[] {3, 700000, 42});
        assertEquals(spot, roundTrip(SpotEnemyPayload.CODEC, spot));
        assertEquals(new SpotEnemyPayload(60, new int[0]),
                roundTrip(SpotEnemyPayload.CODEC, new SpotEnemyPayload(60, new int[0])));
        MarkPosPayload mark = new MarkPosPayload(-30000, 1000, 12);
        assertEquals(mark, roundTrip(MarkPosPayload.CODEC, mark));
        RangeFinderSpotPayload request = new RangeFinderSpotPayload(60);
        assertEquals(request, roundTrip(RangeFinderSpotPayload.CODEC, request));
    }

    @Test
    void weaponControlRoundTrips() {
        WeaponControlPayload none = WeaponControlPayload.none(42);
        assertTrue(none.isEmpty());
        assertEquals(none, roundTrip(WeaponControlPayload.CODEC, none));
        WeaponControlPayload all = none.withSwitchWeapon(3).withUseWeapon(7, -2, 10.5, 64.25, -300.125, 91.5F, -12.0F)
                .withFlare(2).withReload().withChaff().withAps();
        assertFalse(all.isEmpty());
        assertEquals(all, roundTrip(WeaponControlPayload.CODEC, all));
        // Without a shot the aim is not sent and reads back as zero.
        WeaponControlPayload flareOnly = none.withFlare(1);
        assertEquals(flareOnly, roundTrip(WeaponControlPayload.CODEC, flareOnly));
    }

    @Test
    void weaponPayloadsRoundTrip() {
        WeaponSyncPayload sync = new WeaponSyncPayload(5, 1, 3, (short) 120, (short) 900, (short) -4, (short) 60);
        assertEquals(sync, roundTrip(WeaponSyncPayload.CODEC, sync));
        AmmoRequestPayload request = new AmmoRequestPayload(9, -1);
        assertEquals(request, roundTrip(AmmoRequestPayload.CODEC, request));
        ResupplyPayload resupply = new ResupplyPayload(9, 4);
        assertEquals(resupply, roundTrip(ResupplyPayload.CODEC, resupply));
        SyncReloadPayload reload = new SyncReloadPayload(11);
        assertEquals(reload, roundTrip(SyncReloadPayload.CODEC, reload));
        AirburstDistPayload airburst = new AirburstDistPayload(12, 450);
        assertEquals(airburst, roundTrip(AirburstDistPayload.CODEC, airburst));
        ChaffUsePayload chaff = new ChaffUsePayload(13, 60);
        assertEquals(chaff, roundTrip(ChaffUsePayload.CODEC, chaff));
        NotifyHitPayload hit = new NotifyHitPayload(-1);
        assertEquals(hit, roundTrip(NotifyHitPayload.CODEC, hit));
        TvMissilePayload tv = new TvMissilePayload(14, 15);
        assertEquals(tv, roundTrip(TvMissilePayload.CODEC, tv));
        LaserShotPayload laser = new LaserShotPayload(new net.minecraft.world.phys.Vec3(1.5, 70.0, -2.0),
                new net.minecraft.world.phys.Vec3(40.0, 62.5, 8.0), 0xC0FF2020, 0.3F, 5, true, 2.5, true);
        assertEquals(laser, roundTrip(LaserShotPayload.CODEC, laser));
        ClientSoundPayload sound = new ClientSoundPayload(1.0F, 2.0F, 3.0F, "hit", true, false);
        assertEquals(sound, roundTrip(ClientSoundPayload.CODEC, sound));
    }

    @Test
    void notifyAmmoRoundTrips() {
        NotifyAmmoPayload all = new NotifyAmmoPayload(20, true, (byte) 0, new short[] {1, 200, 0},
                new short[] {900, -1, 5});
        NotifyAmmoPayload read = roundTrip(NotifyAmmoPayload.CODEC, all);
        assertEquals(all.aircraftId(), read.aircraftId());
        assertTrue(read.all());
        assertTrue(java.util.Arrays.equals(all.ammo(), read.ammo()));
        assertTrue(java.util.Arrays.equals(all.restAmmo(), read.restAmmo()));
        NotifyAmmoPayload one = new NotifyAmmoPayload(21, false, (byte) 4, new short[] {30}, new short[] {300});
        NotifyAmmoPayload readOne = roundTrip(NotifyAmmoPayload.CODEC, one);
        assertEquals(4, readOne.weaponId());
        assertFalse(readOne.all());
        assertTrue(java.util.Arrays.equals(one.ammo(), readOne.ammo()));
        assertTrue(java.util.Arrays.equals(one.restAmmo(), readOne.restAmmo()));
    }
}
