package io.github.mcheliceport.gametest;

import static io.github.mcheliceport.gametest.GameTestSupport.check;

import io.github.mcheliceport.content.ContentLocation;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.content.aircraft.HeliInfo;
import io.github.mcheliceport.content.aircraft.SeatInfo;
import io.github.mcheliceport.content.weapon.WeaponInfo;
import io.github.mcheliceport.entity.gunner.GunnerEntity;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.entity.weapon.ATMissileEntity;
import io.github.mcheliceport.registry.ModEntityTypes;
import io.github.mcheliceport.weapon.guidance.WeaponGuidanceSystem;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Upstream's ECM jammer ({@code useECMJammer}, {@code destroyMissileECM} and the radar lock check). */
final class EcmGameTests {

    private static final int USE_TIME = 20;
    private static final int WAIT_TIME = 60;

    private EcmGameTests() {
    }

    static void register() {
        ModGameTests.test("ecm_jammer_runs_then_waits", 20, EcmGameTests::runsThenWaits);
        ModGameTests.test("ecm_jammer_breaks_radar_missiles", 20, EcmGameTests::breaksRadarMissiles);
        ModGameTests.test("ecm_jammer_denies_radar_locks", 20, EcmGameTests::deniesRadarLocks);
        ModGameTests.test("ecm_jammer_used_by_gunner", 40, EcmGameTests::usedByGunner);
    }

    private static HeliInfo definition(int type) {
        var info = new HeliInfo(ContentLocation.builtin("ecm_fixture"), "ecm_fixture.yml", "yaml");
        info.maxHp = 1000;
        info.enableECMJammer = true;
        info.ecmJammerType = type;
        info.ecmJammerUseTime = USE_TIME;
        info.ecmJammerWaitTime = WAIT_TIME;
        info.seatList.add(new SeatInfo(Vec3.ZERO, false));
        return info;
    }

    private static HeliEntity fixture(GameTestHelper h, HeliInfo definition) {
        var ac = new HeliEntity(ModEntityTypes.HELICOPTER.get(), h.getLevel()) {
            @Override protected AircraftInfo lookupInfo(String name) { return definition; }
            @Override protected void onUpdateAircraft() { }
        };
        ac.setNoGravity(true);
        ac.setPos(h.absoluteVec(new Vec3(8.5, 20, 8.5)));
        ac.setAircraftTypeName("ecm_fixture");
        h.getLevel().addFreshEntity(ac);
        return ac;
    }

    private static WeaponInfo seeker(String name, boolean radar) {
        var info = new WeaponInfo(ContentLocation.builtin(name), name + ".yml", "yaml");
        info.isRadarMissile = radar;
        return info;
    }

    /** A missile held still six blocks above that homes on the vehicle and reads as the given seeker. */
    private static ATMissileEntity missile(GameTestHelper h, HeliEntity target, WeaponInfo seeker) {
        Vec3 from = target.position().add(0.0, 6.0, 0.0);
        ATMissileEntity missile = new ATMissileEntity(h.getLevel(), from.x, from.y, from.z, 0.0, 0.0, 0.0, 0.0F, 0.0F,
                0.0F) {
            @Override
            public @Nullable WeaponInfo getInfo() {
                return seeker;
            }

            @Override
            public void tick() {
                // Held in place, so it neither flies into the vehicle nor times out during the test.
            }
        };
        missile.setName("9k114");
        missile.setNoGravity(true);
        missile.shootingEntity = GameTestSupport.player(h, GameType.SURVIVAL, new Vec3(8.5, 1, 40.5));
        h.getLevel().addFreshEntity(missile);
        missile.setTargetEntity(target);
        return missile;
    }

    /** The jammer runs for {@code UseTime} ticks and cannot run again until {@code WaitTime} has passed. */
    private static void runsThenWaits(GameTestHelper h) {
        HeliEntity heli = fixture(h, definition(2));
        check(h, heli.haveECMJammer() && heli.canUseECMJammer(), "the jammer is ready");
        check(h, heli.useECMJammer() && heli.isECMJammerUsing(), "the jammer did not start");
        check(h, !heli.useECMJammer(), "the jammer started again while waiting");
        for (int i = 0; i < USE_TIME; i++) {
            heli.tick();
        }
        check(h, !heli.isECMJammerUsing(), "the jammer still runs after its use time");
        check(h, !heli.canUseECMJammer(), "the jammer is ready before its wait time");
        for (int i = USE_TIME; i < WAIT_TIME; i++) {
            heli.tick();
        }
        check(h, heli.canUseECMJammer(), "the jammer is not ready after its wait time");
        HeliEntity plain = fixture(h, definition(0));
        plain.getAcInfo().enableECMJammer = false;
        check(h, !plain.useECMJammer(), "a vehicle without a jammer used one");
        GameTestSupport.cleanUp(heli);
        GameTestSupport.cleanUp(plain);
        h.succeed();
    }

    /** A type 2 jammer strips radar missiles of their target; heat seekers, and a type 1 jammer, are unaffected. */
    private static void breaksRadarMissiles(GameTestHelper h) {
        HeliEntity heli = fixture(h, definition(2));
        HeliEntity soft = fixture(h, definition(1));
        soft.setPos(soft.position().add(0.0, 8.0, 0.0));
        ATMissileEntity radar = missile(h, heli, seeker("ecm_radar", true));
        ATMissileEntity heat = missile(h, heli, seeker("ecm_heat", false));
        ATMissileEntity other = missile(h, soft, seeker("ecm_radar", true));
        heli.tick();
        check(h, radar.targetEntity == heli, "the radar missile lost its target before the jammer ran");
        check(h, heli.useECMJammer() && soft.useECMJammer(), "the jammers did not start");
        heli.tick();
        soft.tick();
        check(h, radar.targetEntity == null, "the radar missile kept its target");
        check(h, heat.targetEntity == heli, "the heat seeker lost its target");
        check(h, other.targetEntity == soft, "a type 1 jammer broke a radar lock");
        radar.discard();
        heat.discard();
        other.discard();
        GameTestSupport.cleanUp(heli);
        GameTestSupport.cleanUp(soft);
        h.succeed();
    }

    /** A radar seeker cannot lock a vehicle whose type 2 jammer runs; a heat seeker still can. */
    private static void deniesRadarLocks(GameTestHelper h) {
        HeliEntity heli = fixture(h, definition(2));
        WeaponGuidanceSystem radar = new WeaponGuidanceSystem(h.getLevel());
        radar.isRadarMissile = true;
        radar.isHeatSeekerMissile = false;
        radar.canLockInAir = true;
        radar.canLockOnGround = true;
        WeaponGuidanceSystem heat = new WeaponGuidanceSystem(h.getLevel());
        heat.isHeatSeekerMissile = true;
        heat.canLockInAir = true;
        heat.canLockOnGround = true;
        check(h, radar.canLockEntity(heli), "a radar seeker cannot lock the vehicle before jamming");
        heli.useECMJammer();
        check(h, !radar.canLockEntity(heli), "a radar seeker locked a jamming vehicle");
        check(h, heat.canLockEntity(heli), "a heat seeker cannot lock a jamming vehicle");
        GameTestSupport.cleanUp(heli);
        h.succeed();
    }

    /** A gunner switches the jammer on against a missile homing on its vehicle, as upstream's gunner. */
    private static void usedByGunner(GameTestHelper h) {
        HeliEntity heli = fixture(h, definition(2));
        GunnerEntity gunner = new GunnerEntity(ModEntityTypes.GUNNER.get(), h.getLevel());
        gunner.snapTo(heli.getX(), heli.getY(), heli.getZ(), 0.0F, 0.0F);
        h.getLevel().addFreshEntity(gunner);
        check(h, gunner.startRiding(heli), "the gunner did not board");
        ATMissileEntity missile = missile(h, heli, seeker("ecm_radar", true));
        h.succeedWhen(() -> {
            check(h, heli.isECMJammerUsing() || !heli.canUseECMJammer(), "the gunner has not used the jammer");
            missile.discard();
            gunner.stopRiding();
            gunner.discard();
            GameTestSupport.cleanUp(heli);
        });
    }
}
