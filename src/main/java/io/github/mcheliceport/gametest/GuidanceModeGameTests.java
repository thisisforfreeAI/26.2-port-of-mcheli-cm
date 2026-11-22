package io.github.mcheliceport.gametest;

import static io.github.mcheliceport.gametest.GameTestSupport.AH64;
import static io.github.mcheliceport.gametest.GameTestSupport.check;

import io.github.mcheliceport.content.ContentLocation;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.content.aircraft.HeliInfo;
import io.github.mcheliceport.content.aircraft.SeatInfo;
import io.github.mcheliceport.content.weapon.WeaponInfo;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.entity.weapon.ATMissileEntity;
import io.github.mcheliceport.entity.weapon.BombEntity;
import io.github.mcheliceport.net.ModNetwork;
import io.github.mcheliceport.registry.ModEntityTypes;
import io.github.mcheliceport.weapon.GpsPosition;
import io.github.mcheliceport.weapon.WeaponSet;
import io.github.mcheliceport.weapon.guidance.MissileGuidance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The guidance modes upstream read but never flew: GPS missiles and bombs, semi-active radar, data
 * link and anti-radiation homing ({@link MissileGuidance}).
 */
final class GuidanceModeGameTests {

    private GuidanceModeGameTests() {
    }

    static void register() {
        ModGameTests.test("gps_point_is_kept_per_player", 20, GuidanceModeGameTests::gpsPoint);
        ModGameTests.test("gps_missile_steers_to_the_point", 20, GuidanceModeGameTests::gpsMissile);
        ModGameTests.test("gps_bomb_glides_to_the_point", 20, GuidanceModeGameTests::gpsBomb);
        ModGameTests.test("semi_active_missile_needs_illumination", 20, GuidanceModeGameTests::semiActive);
        ModGameTests.test("data_link_takes_the_launchers_target", 30, GuidanceModeGameTests::dataLink);
        ModGameTests.test("anti_radiation_missile_homes_on_emitters", 20, GuidanceModeGameTests::antiRadiation);
    }

    private static WeaponInfo weapon(String name) {
        var info = new WeaponInfo(ContentLocation.builtin(name), name + ".yml", "yaml");
        info.rigidityTime = -1;
        info.turningFactor = 1.0;
        return info;
    }

    /** A missile at {@code relative} that reads as {@code info} and only moves when a test steers it. */
    private static ATMissileEntity missile(GameTestHelper h, WeaponInfo info, Vec3 relative) {
        Vec3 from = h.absoluteVec(relative);
        ATMissileEntity missile = new ATMissileEntity(h.getLevel(), from.x, from.y, from.z, 0.0, 0.0, 1.0, 0.0F, 0.0F,
                1.0F) {
            @Override
            public @Nullable WeaponInfo getInfo() {
                return info;
            }

            @Override
            public void tick() {
            }
        };
        missile.setName("guidance_fixture");
        missile.setNoGravity(true);
        missile.guidance = MissileGuidance.create(info);
        h.getLevel().addFreshEntity(missile);
        return missile;
    }

    private static HeliInfo radarVehicle() {
        var info = new HeliInfo(ContentLocation.builtin("radar_fixture"), "radar_fixture.yml", "yaml");
        info.maxHp = 1000;
        info.enableRadar = true;
        info.seatList.add(new SeatInfo(Vec3.ZERO, false));
        return info;
    }

    private static HeliEntity fixture(GameTestHelper h, HeliInfo definition, Vec3 relative) {
        var ac = new HeliEntity(ModEntityTypes.HELICOPTER.get(), h.getLevel()) {
            @Override protected AircraftInfo lookupInfo(String name) { return definition; }
            @Override protected void onUpdateAircraft() { }
        };
        ac.setNoGravity(true);
        ac.setPos(h.absoluteVec(relative));
        ac.setAircraftTypeName("radar_fixture");
        h.getLevel().addFreshEntity(ac);
        return ac;
    }

    /** The server keeps each player's active point, refuses broken ones and forgets it on logout. */
    private static void gpsPoint(GameTestHelper h) {
        Player player = GameTestSupport.player(h, GameType.SURVIVAL, new Vec3(8.5, 1, 8.5));
        check(h, GpsPosition.target(player) == null, "a player without a point has one");
        GpsPosition.setServer(player, new GpsPosition.Point(10.0, 64.0, -20.0, true));
        check(h, new Vec3(10.0, 64.0, -20.0).equals(GpsPosition.target(player)), "the point was not kept");
        GpsPosition.setServer(player, new GpsPosition.Point(Double.NaN, 64.0, 0.0, true));
        check(h, new Vec3(10.0, 64.0, -20.0).equals(GpsPosition.target(player)), "a broken point replaced it");
        GpsPosition.setServer(player, new GpsPosition.Point(10.0, 64.0, -20.0, false));
        check(h, GpsPosition.target(player) == null, "an inactive point guides");
        GpsPosition.setServer(player, new GpsPosition.Point(1.0, 2.0, 3.0, true));
        GpsPosition.forget(player);
        check(h, GpsPosition.target(player) == null, "the point outlived the logout");
        check(h, GpsPosition.target(null) == null, "a missing shooter has a point");
        h.succeed();
    }

    /** A missile with a GPS point turns to it, and goes off at it within its proximity fuse. */
    private static void gpsMissile(GameTestHelper h) {
        WeaponInfo info = weapon("gps_fixture");
        info.isGPSMissile = true;
        check(h, MissileGuidance.create(info) == null, "a GPS missile took the emitter or link guidance");
        ATMissileEntity missile = missile(h, info, new Vec3(8.5, 10, 8.5));
        Vec3 point = missile.position().add(30.0, 0.0, 0.0);
        missile.pointTarget = point;
        missile.guidanceToPoint(point);
        Vec3 m = missile.getDeltaMovement();
        check(h, m.x > 0.99 && Math.abs(m.z) < 0.01, "the missile did not turn to the point: " + m);
        info.proximityFuseDist = 2.0F;
        missile.guidanceToPoint(missile.position().add(1.0, 0.0, 0.0));
        check(h, missile.isRemoved(), "the missile did not go off at the point");
        h.succeed();
    }

    /** A GPS bomb's horizontal velocity turns towards the point as it falls. */
    private static void gpsBomb(GameTestHelper h) {
        Vec3 from = h.absoluteVec(new Vec3(8.5, 30, 8.5));
        BombEntity bomb = new BombEntity(h.getLevel(), from.x, from.y, from.z, 0.0, 0.0, 1.0, 0.0F, 0.0F, 0.5F);
        bomb.setDeltaMovement(0.0, -1.0, 0.5);
        Vec3 point = from.add(20.0, -29.0, 0.0);
        // Falling a block a tick, it lands in 29 ticks: about 0.7 blocks a tick sideways.
        for (int i = 0; i < 10; i++) {
            bomb.glideToPoint(point);
        }
        Vec3 m = bomb.getDeltaMovement();
        check(h, m.x > 0.3 && m.z < 0.25, "the bomb did not glide towards the point: " + m);
        check(h, Math.sqrt(m.x * m.x + m.z * m.z) <= 1.0 + 1.0e-6, "the bomb sped up past its limit: " + m);
        h.succeed();
    }

    /**
     * A semi-active missile keeps its target while the shooter looks at it, and loses it for good
     * once the shooter looks away for longer than {@code PassiveRadarLockOutCount}.
     */
    private static void semiActive(GameTestHelper h) {
        WeaponInfo info = weapon("sarh_fixture");
        info.semiActiveRadar = true;
        info.isRadarMissile = true;
        info.passiveRadarLockOutCount = 4;
        Player shooter = GameTestSupport.player(h, GameType.SURVIVAL, new Vec3(8.5, 1, 2.5));
        HeliEntity target = fixture(h, radarVehicle(), new Vec3(8.5, 1, 20.5));
        target.setPos(target.getX(), shooter.getEyeY() - target.getBbHeight() / 2.0, target.getZ());
        shooter.setYRot(0.0F);
        shooter.setXRot(0.0F);
        ATMissileEntity missile = missile(h, info, new Vec3(8.5, 3, 4.5));
        missile.shootingEntity = shooter;
        missile.setTargetEntity(target);
        missile.guidance.tick(missile);
        check(h, missile.guidance.launcherTracking(), "the shooter looking at the target does not illuminate it");
        check(h, missile.guidance.overridesSeekerLimit(), "an illuminated missile is held to its seeker angle");
        shooter.setYRot(180.0F);
        for (int i = 0; i < 4; i++) {
            missile.guidance.tick(missile);
        }
        check(h, missile.targetEntity == target, "the missile lost its target inside the lock-out time");
        missile.guidance.tick(missile);
        check(h, missile.targetEntity == null && missile.guidance.linkBroken(), "the missile kept an unlit target");
        shooter.setYRot(0.0F);
        missile.guidance.tick(missile);
        check(h, missile.targetEntity == null, "a broken semi-active missile found its target again");
        missile.discard();
        GameTestSupport.cleanUp(target);
        h.succeed();
    }

    /**
     * A data-link missile takes the launcher's new BVR target in flight; with {@code OnlyDataLink} it
     * loses its target once the shooter leaves the launcher.
     */
    private static void dataLink(GameTestHelper h) {
        HeliEntity heli = GameTestSupport.spawn(h, AH64, new Vec3(8.5, 1, 8.5), 0.0F);
        HeliEntity first = GameTestSupport.spawn(h, AH64, new Vec3(8.5, 1, 24.5), 0.0F);
        HeliEntity second = GameTestSupport.spawn(h, AH64, new Vec3(24.5, 1, 24.5), 0.0F);
        Player pilot = GameTestSupport.player(h, GameType.SURVIVAL, new Vec3(10.5, 1, 8.5));
        h.runAfterDelay(2, () -> {
            heli.interact(pilot, InteractionHand.MAIN_HAND, Vec3.ZERO);
            check(h, heli.getPilot() == pilot, "the pilot did not board");
            int seeker = -1;
            for (int i = 0; i < heli.getWeaponNum() && seeker < 0; i++) {
                WeaponSet ws = heli.getWeapon(i);
                if (ws.getCurrentWeapon() != null && ws.getCurrentWeapon().getGuidanceSystem() != null) {
                    seeker = i;
                }
            }
            check(h, seeker >= 0, "the AH-64 has no seeker");
            heli.weapons().switchWeapon(pilot, seeker);
            WeaponInfo info = weapon("datalink_fixture");
            info.enableDataLink = true;
            info.onlyDataLink = true;
            info.passiveRadarLockOutCount = 2;
            ATMissileEntity missile = missile(h, info, new Vec3(8.5, 6, 12.5));
            missile.shootingEntity = pilot;
            missile.shootingAircraft = heli;
            missile.setTargetEntity(first);
            ModNetwork.applyBvrLock(pilot, second.getId());
            missile.guidance.tick(missile);
            check(h, missile.targetEntity == second, "the missile did not take the launcher's new target");
            check(h, missile.guidance.launcherTracking(), "the launcher does not track its own BVR target");
            pilot.stopRiding();
            for (int i = 0; i < 3; i++) {
                missile.guidance.tick(missile);
            }
            check(h, missile.targetEntity == null, "an only-data-link missile kept its target off the link");
            missile.discard();
            GameTestSupport.cleanUp(heli);
            GameTestSupport.cleanUp(first);
            GameTestSupport.cleanUp(second);
            h.succeed();
        });
    }

    /**
     * Only a radiating vehicle counts as an emitter. An anti-radiation missile keeps tracking a quiet
     * emitter for its grace time, then flies at its last position for its memory time, then lets go.
     */
    private static void antiRadiation(GameTestHelper h) {
        HeliEntity emitter = fixture(h, radarVehicle(), new Vec3(8.5, 10, 20.5));
        check(h, !MissileGuidance.isEmitter(emitter), "a vehicle with its radar off radiates");
        emitter.setRadarEnabledRuntime(true, false);
        check(h, MissileGuidance.isEmitter(emitter), "a vehicle with its radar on does not radiate");
        Vec3 from = h.absoluteVec(new Vec3(8.5, 10, 4.5));
        check(h, MissileGuidance.findEmitter(h.getLevel(), null, from, new Vec3(0, 0, 1), 100, 10) == emitter,
                "the emitter ahead was not found");
        check(h, MissileGuidance.findEmitter(h.getLevel(), null, from, new Vec3(1, 0, 0), 100, 10) == null,
                "an emitter off the seeker's axis was found");

        WeaponInfo info = weapon("arm_fixture");
        info.antiRadiationMissile = true;
        info.armEmitterLostGraceTick = 2;
        info.armMemoryTimeTick = 3;
        info.scanInterval = 1000;
        ATMissileEntity missile = missile(h, info, new Vec3(8.5, 10, 6.5));
        missile.setTargetEntity(emitter);
        missile.guidance.tick(missile);
        check(h, missile.pointTarget == null && missile.targetEntity == emitter, "the missile left a live emitter");
        Vec3 seen = emitter.position().add(0.0, emitter.getBbHeight() / 2.0, 0.0);
        emitter.setRadarEnabledRuntime(false, false);
        for (int i = 0; i < 2; i++) {
            missile.guidance.tick(missile);
        }
        check(h, missile.pointTarget == null, "the missile went to memory inside its grace time");
        missile.guidance.tick(missile);
        check(h, seen.equals(missile.pointTarget), "the missile did not fly at the emitter's last position");
        emitter.setRadarEnabledRuntime(true, false);
        missile.guidance.tick(missile);
        check(h, missile.pointTarget == null && missile.targetEntity == emitter,
                "the missile did not resume when the emitter came back on");
        emitter.setRadarEnabledRuntime(false, false);
        for (int i = 0; i < 6; i++) {
            missile.guidance.tick(missile);
        }
        check(h, missile.pointTarget == null && missile.targetEntity == null,
                "the missile kept a quiet emitter past its memory");
        missile.discard();
        GameTestSupport.cleanUp(emitter);
        h.succeed();
    }
}
