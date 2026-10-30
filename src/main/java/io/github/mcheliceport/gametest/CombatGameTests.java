package io.github.mcheliceport.gametest;

import static io.github.mcheliceport.gametest.GameTestSupport.check;

import io.github.mcheliceport.content.ContentLocation;
import io.github.mcheliceport.content.ContentType;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.content.aircraft.HeliInfo;
import io.github.mcheliceport.content.aircraft.SeatInfo;
import io.github.mcheliceport.entity.gunner.GunnerEntity;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.entity.plane.PlaneEntity;
import io.github.mcheliceport.item.AircraftItem;
import io.github.mcheliceport.registry.ModEntityTypes;
import io.github.mcheliceport.registry.ModItems;
import io.github.mcheliceport.weapon.WeaponParam;
import io.github.mcheliceport.weapon.WeaponSet;
import io.github.mcheliceport.weapon.explosion.McheliExplosion;
import io.github.mcheliceport.weapon.guidance.WeaponGuidanceSystem;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Upstream's smaller combat systems: {@code Destruct} bombs, the pulse-Doppler notch and the iron curtain. */
final class CombatGameTests {

    private CombatGameTests() {
    }

    static void register() {
        ModGameTests.test("destruct_bomb_blows_up_its_uav", 40, CombatGameTests::destruct);
        ModGameTests.test("notch_breaks_plane_to_plane_lock", 20, CombatGameTests::notch);
        ModGameTests.test("iron_curtain_blocks_damage_while_it_runs", 20, CombatGameTests::ironCurtain);
        ModGameTests.test("wreck_blast_leaves_the_ground_it_lies_on", 20, CombatGameTests::wreckGround);
    }

    private static PlaneEntity plane(GameTestHelper h, Vec3 relative) {
        Vec3 pos = h.absoluteVec(relative);
        AircraftItem item = (AircraftItem) ModItems.contentItems().stream()
                .filter(i -> i.type() == ContentType.PLANE && i.contentName().equals("a-10"))
                .findFirst().orElseThrow().item().get();
        PlaneEntity plane = (PlaneEntity) item.createAircraft(h.getLevel(), pos.x, pos.y, pos.z);
        h.getLevel().addFreshEntity(plane);
        return plane;
    }

    private static Husk husk(GameTestHelper h, Vec3 relative) {
        Husk husk = EntityTypes.HUSK.create(h.getLevel(), EntitySpawnReason.COMMAND);
        Vec3 pos = h.absoluteVec(relative);
        husk.setNoAi(true);
        husk.snapTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        h.getLevel().addFreshEntity(husk);
        return husk;
    }

    /** The RC Goblin's plastic bomb explodes at the UAV and takes it away, as upstream's {@code MCH_WeaponBomb}. */
    private static void destruct(GameTestHelper h) {
        HeliEntity goblin = GameTestSupport.spawn(h, "rc-goblin-bomb", new Vec3(8.5, 1, 8.5), 0.0F);
        Husk husk = husk(h, new Vec3(10.5, 1, 8.5));
        Player user = GameTestSupport.player(h, GameType.SURVIVAL, new Vec3(8.5, 1, 30.5));
        h.runAfterDelay(2, () -> {
            check(h, goblin.isUAV() && goblin.getSeatNum() == 0, "the RC Goblin is a crewless UAV");
            WeaponSet ws = goblin.getWeapon(0);
            check(h, ws != null && ws.getInfo() != null && ws.getInfo().destruct, "the RC Goblin carries a Destruct bomb");
            WeaponParam prm = new WeaponParam();
            prm.entity = goblin;
            prm.user = user;
            prm.setPosAndRot(goblin.getX(), goblin.getY(), goblin.getZ(), 0.0F, 0.0F);
            check(h, ws.getCurrentWeapon().shot(prm), "the bomb was not used");
            check(h, goblin.isRemoved(), "the UAV is still there");
            check(h, husk.getHealth() < husk.getMaxHealth(), "the explosion did not hurt the husk beside it");
            husk.discard();
            h.succeed();
        });
    }

    /**
     * A wreck's blast breaks what stands on the ground (a fence) but not the ground under the wreck, so
     * the hulk does not drop into a crater; the same blast as a weapon's still digs one.
     */
    private static void wreckGround(GameTestHelper h) {
        for (int x = 2; x <= 22; x++) {
            for (int y = 1; y <= 3; y++) {
                for (int z = 2; z <= 22; z++) { h.setBlock(x, y, z, Blocks.DIRT); }
            }
        }
        h.setBlock(7, 4, 6, Blocks.OAK_FENCE);
        Vec3 wreck = h.absoluteVec(new Vec3(6.5, 4.0, 6.5));
        McheliExplosion.newWreckExplosion(h.getLevel(), null, wreck.x, wreck.y, wreck.z, 4.5F, 2.25F);
        for (int x = 4; x <= 9; x++) {
            for (int z = 4; z <= 9; z++) { check(h, !h.getBlockState(new net.minecraft.core.BlockPos(x, 3, z)).isAir(), "the wreck's blast dug into the ground at " + x + ", " + z); }
        }
        check(h, h.getBlockState(new net.minecraft.core.BlockPos(7, 4, 6)).isAir(), "the wreck's blast left the fence beside it");
        Vec3 bomb = h.absoluteVec(new Vec3(17.5, 4.0, 17.5));
        McheliExplosion.newExplosion(h.getLevel(), null, null, bomb.x, bomb.y, bomb.z, 4.5F, 2.25F, false, false, true, 0);
        check(h, h.getBlockState(new net.minecraft.core.BlockPos(17, 3, 17)).isAir(), "the same blast from a weapon dug no crater");
        h.succeed();
    }

    /**
     * Upstream's pulse-Doppler notch: a seeker on a plane loses a plane flying across its own course
     * by more than {@code PDHDNMaxDegree}, whether head-on or tail-on; within it, the lock holds.
     */
    private static void notch(GameTestHelper h) {
        PlaneEntity shooter = plane(h, new Vec3(4.5, 20, 4.5));
        PlaneEntity target = plane(h, new Vec3(12.5, 20, 12.5));
        GunnerEntity pilot = new GunnerEntity(ModEntityTypes.GUNNER.get(), h.getLevel());
        pilot.snapTo(shooter.getX(), shooter.getY(), shooter.getZ(), 0.0F, 0.0F);
        h.getLevel().addFreshEntity(pilot);
        check(h, pilot.startRiding(shooter), "the pilot did not board");
        WeaponGuidanceSystem seeker = new WeaponGuidanceSystem(h.getLevel());
        seeker.canLockInAir = true;
        seeker.canLockOnGround = true;
        seeker.setTarget(target);
        shooter.setDeltaMovement(new Vec3(0.0, 0.0, 1.0));
        target.setDeltaMovement(new Vec3(1.0, 0.0, 0.0));
        check(h, seeker.canContinueLocking(pilot), "the lock broke with the notch off");
        seeker.pdHDNMaxDegree = 60.0F;
        check(h, !seeker.canContinueLocking(pilot), "a beaming plane kept the lock");
        target.setDeltaMovement(new Vec3(0.5, 0.0, -1.0));
        check(h, seeker.canContinueLocking(pilot), "a plane flying head-on, 27 degrees off, broke the lock");
        target.setDeltaMovement(new Vec3(0.0, 0.0, 0.0));
        check(h, seeker.canContinueLocking(pilot), "a still target broke the lock");
        pilot.stopRiding();
        pilot.discard();
        GameTestSupport.cleanUp(shooter);
        GameTestSupport.cleanUp(target);
        h.succeed();
    }

    /** An APS with range 100 is upstream's iron curtain: no damage while it runs, then damage as usual. */
    private static void ironCurtain(GameTestHelper h) {
        var definition = new HeliInfo(ContentLocation.builtin("curtain_fixture"), "curtain_fixture.yml", "yaml");
        definition.maxHp = 1000;
        definition.hasAPS = true;
        definition.apsRange = 100;
        definition.apsUseTime = 20;
        definition.apsWaitTime = 40;
        definition.seatList.add(new SeatInfo(Vec3.ZERO, false));
        var heli = new HeliEntity(ModEntityTypes.HELICOPTER.get(), h.getLevel()) {
            @Override protected AircraftInfo lookupInfo(String name) { return definition; }
            @Override protected void onUpdateAircraft() { }
        };
        heli.setNoGravity(true);
        heli.setPos(h.absoluteVec(new Vec3(8.5, 10, 8.5)));
        heli.setAircraftTypeName("curtain_fixture");
        h.getLevel().addFreshEntity(heli);
        // The APS takes its settings on the vehicle's first tick, as upstream.
        heli.tick();
        check(h, heli.canUseAPS() && heli.useAPS(), "the iron curtain did not start");
        check(h, heli.isIronCurtainActive(), "the iron curtain is not running");
        int hp = heli.getHP();
        check(h, !heli.hurtServer(h.getLevel(), h.getLevel().damageSources().generic(), 100.0F) && heli.getHP() == hp,
                "the vehicle took damage under the iron curtain");
        for (int i = 0; i < 20; i++) {
            heli.tick();
        }
        check(h, !heli.isIronCurtainActive(), "the iron curtain still runs after its use time");
        heli.hurtServer(h.getLevel(), h.getLevel().damageSources().generic(), 100.0F);
        check(h, heli.getHP() < hp, "the vehicle took no damage after the iron curtain");
        GameTestSupport.cleanUp(heli);
        h.succeed();
    }
}
