package io.github.mcheliceport.gametest;

import static io.github.mcheliceport.gametest.GameTestSupport.AH64;
import static io.github.mcheliceport.gametest.GameTestSupport.check;

import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.content.ContentType;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.entity.plane.PlaneEntity;
import io.github.mcheliceport.entity.weapon.BaseBulletEntity;
import io.github.mcheliceport.entity.weapon.BombEntity;
import io.github.mcheliceport.entity.weapon.BulletEntity;
import io.github.mcheliceport.item.AircraftItem;
import io.github.mcheliceport.registry.ModItems;
import io.github.mcheliceport.wingman.config.WingmanConfig;
import io.github.mcheliceport.wingman.handler.ProjectileChunkLoader;
import io.github.mcheliceport.wingman.handler.WingmanTickHandler;
import io.github.mcheliceport.wingman.wingman.WingmanEntry;
import io.github.mcheliceport.wingman.wingman.WingmanRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The AI fire control and flight in real ticks: a P-51D wingman's guns hit a target drone flying
 * ahead of it, strafe a husk on the ground and pull off without hitting the ground, an F-4's bombs
 * land on a husk, an A-10 picks the weapon each target calls for, and a plane whose leader has
 * landed circles over it banked and without sliding sideways.
 */
final class AiFireControlGameTests {

    /** Chunks forced each way round the test, for aircraft that fly a couple of hundred blocks. */
    private static final int ARENA_CHUNKS = 10;
    /** These tests fly and fire in a batch of their own, so other tests do not see their rounds. */
    private static final String BATCH = "ai_flight";

    private AiFireControlGameTests() {
    }

    static void register() {
        ModGameTests.testInBatch(BATCH, "ai_guns_hit_a_drone", 620, AiFireControlGameTests::gunsHitADrone);
        ModGameTests.testInBatch(BATCH, "ai_strafes_a_ground_target", 820, AiFireControlGameTests::strafes);
        ModGameTests.testInBatch(BATCH, "ai_bombs_land_on_target", 820, AiFireControlGameTests::bombs);
        ModGameTests.testInBatch(BATCH, "ai_plane_circles_a_stopped_leader", 420, AiFireControlGameTests::circles);
        ModGameTests.testInBatch(BATCH, "ai_chooses_the_weapon_for_the_target", 20, AiFireControlGameTests::chooses);
    }

    /**
     * A stretch of empty sky well away from the test grid (whose barrier boxes would stop a plane),
     * its chunks forced: a stone strip along +z for the ground, with pads for the leader and targets.
     * Each test gets its own, {@code slot} thousand blocks further on.
     */
    private static final class Arena {
        final ServerLevel level;
        final BlockPos base;
        final double floor;

        Arena(GameTestHelper h, int slot) {
            this.level = h.getLevel();
            BlockPos o = h.absolutePos(BlockPos.ZERO);
            this.base = new BlockPos(o.getX() + 3000 + slot * 1000, o.getY(), o.getZ());
            this.forEachChunk(true);
            for (int z = -16; z <= 240; z++) {
                for (int x = -4; x <= 12; x++) {
                    this.level.setBlockAndUpdate(this.base.offset(x, 0, z), Blocks.STONE.defaultBlockState());
                }
            }
            this.floor = this.base.getY() + 1.0;
        }

        /** {@code over} blocks above the ground at {@code x}, {@code z} from the arena's corner. */
        Vec3 at(double x, double over, double z) {
            return new Vec3(this.base.getX() + x, this.floor + over, this.base.getZ() + z);
        }

        private void forEachChunk(boolean forced) {
            int cx = this.base.getX() >> 4;
            int cz = (this.base.getZ() + 100) >> 4;
            for (int x = -ARENA_CHUNKS; x <= ARENA_CHUNKS; x++) {
                for (int z = -ARENA_CHUNKS; z <= ARENA_CHUNKS; z++) {
                    this.level.getChunk(cx + x, cz + z);
                    this.level.setChunkForced(cx + x, cz + z, forced);
                }
            }
        }

        void close() {
            this.forEachChunk(false);
        }
    }

    private static PlaneEntity plane(GameTestHelper h, String name, Vec3 pos, double speed) {
        AircraftItem item = (AircraftItem) ModItems.contentItems().stream()
                .filter(i -> i.type() == ContentType.PLANE && i.contentName().equals(name))
                .findFirst().orElseThrow().item().get();
        PlaneEntity plane = (PlaneEntity) item.createAircraft(h.getLevel(), pos.x, pos.y, pos.z);
        plane.initRotationYaw(0.0F);
        plane.setFuel(plane.getMaxFuel());
        h.getLevel().addFreshEntity(plane);
        plane.setCurrentThrottle(1.0);
        plane.setDeltaMovement(0.0, 0.0, speed);
        if (plane.canFoldLandingGear()) {
            plane.foldLandingGear();
        }
        return plane;
    }

    /** An AH-64 on the ground at {@code pos} with a player in it, so its wingmen may attack. */
    private static HeliEntity leader(GameTestHelper h, Vec3 pos) {
        HeliEntity leader = (HeliEntity) GameTestSupport.item(AH64).createAircraft(h.getLevel(), pos.x, pos.y, pos.z);
        leader.initRotationYaw(0.0F);
        h.getLevel().addFreshEntity(leader);
        Player pilot = h.makeMockPlayer(GameType.SURVIVAL);
        pilot.snapTo(leader.getX(), leader.getY(), leader.getZ() + 3.0, 0.0F, 0.0F);
        leader.interact(pilot, InteractionHand.MAIN_HAND, Vec3.ZERO);
        check(h, leader.getPilot() == pilot, "the leader's pilot did not board");
        return leader;
    }

    /** A husk standing still at {@code pos}. */
    private static Husk husk(GameTestHelper h, Vec3 pos) {
        Husk husk = EntityTypes.HUSK.create(h.getLevel(), EntitySpawnReason.COMMAND);
        husk.snapTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        husk.setNoAi(true);
        husk.setPersistenceRequired();
        h.getLevel().addFreshEntity(husk);
        return husk;
    }

    /**
     * Ends a flight test: removes every round still flying round the arena and, once no chunk is
     * leased for one any more (the projectile chunk test counts them all), succeeds.
     */
    private static void finish(GameTestHelper h, Arena arena) {
        for (BaseBulletEntity b : h.getLevel().getEntitiesOfClass(BaseBulletEntity.class,
                new AABB(arena.base).inflate(600.0))) {
            b.discard();
        }
        arena.close();
        h.succeedWhen(() -> check(h, ProjectileChunkLoader.leased(h.getLevel()) == 0, "rounds still lease chunks"));
    }

    private static WingmanEntry attack(AircraftEntity wingman, HeliEntity leader, AircraftEntity target, String weapon) {
        return attack(wingman, leader, target.getUUID(), weapon);
    }

    private static WingmanEntry attack(AircraftEntity wingman, HeliEntity leader, UUID target, String weapon) {
        WingmanEntry entry = new WingmanEntry(leader, 0);
        entry.attackMode = WingmanEntry.ATK_MANUAL;
        entry.manualTargetId = target;
        entry.weaponType = weapon;
        WingmanRegistry.put(wingman.getUUID(), entry);
        return entry;
    }

    private static final class Run {
        int tick;
        boolean done;
        double lowest = Double.MAX_VALUE;
        final Set<UUID> shots = new HashSet<>();
        final Map<UUID, Vec3> bombs = new HashMap<>();
        final List<Double> misses = new ArrayList<>();
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    /**
     * A P-51D 70 blocks behind a BQM-74E drone flying its autopilot 60 blocks up hits it with its
     * guns within 20 seconds.
     */
    private static void gunsHitADrone(GameTestHelper h) {
        Arena arena = new Arena(h, 0);
        HeliEntity leader = leader(h, arena.at(0.5, 0.0, 0.5));
        PlaneEntity drone = plane(h, "bqm_74e", arena.at(4.5, 60.0, 78.5), 1.0);
        PlaneEntity fighter = plane(h, "p-51d", arena.at(4.5, 60.0, 8.5), 1.0);
        attack(fighter, leader, drone, null);
        int hp = drone.getHP();
        Run r = new Run();
        h.onEachTick(() -> {
            if (r.done) {
                return;
            }
            r.tick++;
            for (BulletEntity b : h.getLevel().getEntitiesOfClass(BulletEntity.class, fighter.getBoundingBox().inflate(24.0),
                    b -> b.shootingAircraft == fighter)) {
                r.shots.add(b.getUUID());
            }
            boolean hit = drone.getHP() < hp || drone.isDestroyed();
            if (hit || r.tick >= 400) {
                r.done = true;
                McheliCePort.LOGGER.info("[AiFireControlTest] drone: hit={} after {} ticks, {} rounds fired, HP {} -> {},"
                        + " range {}", hit, r.tick, r.shots.size(), hp, drone.getHP(), fmt(fighter.distanceTo(drone)));
                WingmanRegistry.remove(fighter.getUUID());
                GameTestSupport.cleanUp(fighter);
                GameTestSupport.cleanUp(drone);
                GameTestSupport.cleanUp(leader);
                check(h, hit, "in 20 seconds the P-51D's " + r.shots.size() + " rounds never hit the drone");
                finish(h, arena);
            }
        });
    }

    /**
     * A P-51D 40 blocks up runs in at a husk on the ground 110 blocks ahead: its guns hit it, and it
     * pulls off its passes without flying into the ground.
     */
    private static void strafes(GameTestHelper h) {
        Arena arena = new Arena(h, 1);
        HeliEntity leader = leader(h, arena.at(0.5, 0.0, 0.5));
        Husk husk = husk(h, arena.at(4.5, 0.0, 118.5));
        PlaneEntity fighter = plane(h, "p-51d", arena.at(4.5, 40.0, 8.5), 1.0);
        attack(fighter, leader, husk.getUUID(), null);
        float health = husk.getHealth();
        Run r = new Run();
        h.onEachTick(() -> {
            if (r.done) {
                return;
            }
            r.tick++;
            r.lowest = Math.min(r.lowest, fighter.getY() - arena.floor);
            for (BulletEntity b : h.getLevel().getEntitiesOfClass(BulletEntity.class, fighter.getBoundingBox().inflate(24.0),
                    b -> b.shootingAircraft == fighter)) {
                r.shots.add(b.getUUID());
            }
            boolean hit = husk.getHealth() < health || !husk.isAlive();
            if (fighter.isDestroyed() || hit && r.tick > 40 || r.tick >= 600) {
                r.done = true;
                McheliCePort.LOGGER.info("[AiFireControlTest] strafe: hit={} after {} ticks, {} rounds, lowest {} over the"
                        + " ground, destroyed={}", hit, r.tick, r.shots.size(), fmt(r.lowest), fighter.isDestroyed());
                boolean crashed = fighter.isDestroyed();
                WingmanRegistry.remove(fighter.getUUID());
                GameTestSupport.cleanUp(fighter);
                GameTestSupport.cleanUp(leader);
                husk.discard();
                check(h, !crashed, "the P-51D flew into the ground on its pass, lowest " + fmt(r.lowest));
                check(h, hit, "in 30 seconds the P-51D's " + r.shots.size() + " rounds never hit the husk");
                check(h, r.lowest > 4.0, "the P-51D came within " + fmt(r.lowest) + " blocks of the ground");
                finish(h, arena);
            }
        });
    }

    /**
     * An F-4 with Mk 82s, 100 blocks up and 130 blocks short of a husk, bombs it: a bomb lands within
     * 6 blocks of it.
     */
    private static void bombs(GameTestHelper h) {
        Arena arena = new Arena(h, 2);
        HeliEntity leader = leader(h, arena.at(0.5, 0.0, 0.5));
        Husk husk = husk(h, arena.at(4.5, 0.0, 168.5));
        PlaneEntity bomber = plane(h, "f4a", arena.at(4.5, 100.0, 8.5), 1.2);
        attack(bomber, leader, husk.getUUID(), "bomb");
        Vec3 target = husk.position();
        Run r = new Run();
        h.onEachTick(() -> {
            if (r.done) {
                return;
            }
            r.tick++;
            Set<UUID> seen = new HashSet<>();
            for (BombEntity b : h.getLevel().getEntitiesOfClass(BombEntity.class, bomber.getBoundingBox().inflate(400.0),
                    b -> b.shootingAircraft == bomber)) {
                r.bombs.put(b.getUUID(), b.position());
                seen.add(b.getUUID());
            }
            r.bombs.entrySet().removeIf(e -> {
                if (seen.contains(e.getKey())) {
                    return false;
                }
                Vec3 p = e.getValue();
                r.misses.add(Math.hypot(p.x - target.x, p.z - target.z));
                return true;
            });
            boolean onTarget = r.misses.stream().anyMatch(m -> m <= 6.0);
            if (onTarget || bomber.isDestroyed() || r.tick >= 600) {
                r.done = true;
                McheliCePort.LOGGER.info("[AiFireControlTest] bombs: on target={} after {} ticks, misses {}", onTarget,
                        r.tick, r.misses.stream().map(AiFireControlGameTests::fmt).toList());
                WingmanRegistry.remove(bomber.getUUID());
                GameTestSupport.cleanUp(bomber);
                GameTestSupport.cleanUp(leader);
                husk.discard();
                check(h, onTarget, "no bomb landed within 6 blocks of the husk: " + r.misses);
                finish(h, arena);
            }
        });
    }

    /**
     * A P-51D wingman 40 blocks up whose leader sits on the ground circles over it: it stays in the
     * air at speed, banks into the turn, keeps its motion along its nose and stays near.
     */
    private static void circles(GameTestHelper h) {
        Arena arena = new Arena(h, 3);
        WingmanConfig.reset();
        HeliEntity leader = leader(h, arena.at(4.5, 0.0, 60.5));
        PlaneEntity plane = plane(h, "p-51d", arena.at(4.5, 40.0, 8.5), 1.0);
        WingmanRegistry.put(plane.getUUID(), new WingmanEntry(leader, 0));
        Run r = new Run();
        double[] stats = new double[4];
        h.onEachTick(() -> {
            if (r.done) {
                return;
            }
            r.tick++;
            Vec3 m = plane.getDeltaMovement();
            double hs = Math.hypot(m.x, m.z);
            if (r.tick > 40) {
                double heading = Math.toDegrees(Math.atan2(-m.x, m.z));
                double slip = Math.abs(Mth.wrapDegrees(heading - plane.getYaw()));
                stats[0] = Math.max(stats[0], slip);
                stats[1] += slip;
                stats[2] = Math.max(stats[2], Math.abs(plane.getRoll()));
                stats[3] = Math.min(stats[3] == 0 ? Double.MAX_VALUE : stats[3], hs);
                r.lowest = Math.min(r.lowest, plane.getY() - arena.floor);
            }
            if (r.tick >= 200) {
                r.done = true;
                double far = Math.hypot(plane.getX() - leader.getX(), plane.getZ() - leader.getZ());
                double meanSlip = stats[1] / (r.tick - 40);
                McheliCePort.LOGGER.info("[AiFireControlTest] circle: slip max {} mean {}, bank max {}, slowest {}, lowest {},"
                        + " {} from the leader", fmt(stats[0]), fmt(meanSlip), fmt(stats[2]), fmt(stats[3]), fmt(r.lowest),
                        fmt(far));
                WingmanRegistry.remove(plane.getUUID());
                GameTestSupport.cleanUp(plane);
                GameTestSupport.cleanUp(leader);
                check(h, stats[3] > 0.4, "the plane slowed to " + fmt(stats[3]) + " blocks a tick, hanging in the air");
                check(h, meanSlip < 8.0 && stats[0] < 25.0, "the plane slid sideways: mean " + fmt(meanSlip) + ", most "
                        + fmt(stats[0]) + " degrees off its nose");
                check(h, stats[2] > 10.0, "the plane turned flat, banking at most " + fmt(stats[2]));
                check(h, r.lowest > 10.0, "the plane sank to " + fmt(r.lowest) + " over the ground");
                check(h, far < 160.0, "the plane wandered " + fmt(far) + " blocks from its leader");
                finish(h, arena);
            }
        });
    }

    /**
     * An A-10 free to use any weapon takes its Sidewinder to a drone in the air, its gun to a husk
     * and its Maverick to an aircraft on the ground; told to use rockets, it uses rockets.
     */
    private static void chooses(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        Arena arena = new Arena(h, 4);
        HeliEntity leader = leader(h, arena.at(0.5, 0.0, 0.5));
        PlaneEntity a10 = plane(h, "a-10", arena.at(4.5, 30.0, 8.5), 1.0);
        PlaneEntity drone = plane(h, "bqm_74e", arena.at(4.5, 30.0, 60.5), 1.0);
        PlaneEntity parked = plane(h, "p-51d", arena.at(10.5, 0.0, 40.5), 0.0);
        Husk husk = husk(h, arena.at(2.5, 0.0, 40.5));
        h.runAfterDelay(2, () -> {
            try {
                WingmanEntry entry = new WingmanEntry(leader, 0);
                entry.attackMode = WingmanEntry.ATK_MANUAL;
                WingmanTickHandler.chooseWeapon(level, a10, entry, drone);
                check(h, "aamissile".equals(entry.aiWeaponType), "at a drone in the air: " + entry.aiWeaponType);
                WingmanTickHandler.chooseWeapon(level, a10, entry, husk);
                check(h, "machinegun1".equals(entry.aiWeaponType), "at a husk: " + entry.aiWeaponType);
                WingmanTickHandler.chooseWeapon(level, a10, entry, parked);
                check(h, "asmissile".equals(entry.aiWeaponType), "at a parked aircraft: " + entry.aiWeaponType);
                entry.weaponType = "rocket";
                entry.aiWeaponHold = 0;
                WingmanTickHandler.chooseWeapon(level, a10, entry, parked);
                check(h, "rocket".equals(entry.aiWeaponType), "told to use rockets: " + entry.aiWeaponType);
                entry.weaponType = "aamissile";
                entry.aiWeaponHold = 0;
                WingmanTickHandler.chooseWeapon(level, a10, entry, husk);
                check(h, entry.aiWeapon < 0, "told to use Sidewinders on a husk it chose " + entry.aiWeaponType);
            } finally {
                GameTestSupport.cleanUp(a10);
                GameTestSupport.cleanUp(drone);
                GameTestSupport.cleanUp(parked);
                GameTestSupport.cleanUp(leader);
                husk.discard();
                arena.close();
            }
            h.succeed();
        });
    }
}
