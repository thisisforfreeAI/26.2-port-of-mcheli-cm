package io.github.mcheliceport.gametest;

import static io.github.mcheliceport.gametest.GameTestSupport.AH64;
import static io.github.mcheliceport.gametest.GameTestSupport.check;

import io.github.mcheliceport.config.ServerConfig;
import io.github.mcheliceport.content.ContentType;
import io.github.mcheliceport.damage.ComponentHealth;
import io.github.mcheliceport.damage.ComponentKind;
import io.github.mcheliceport.damage.ComponentView;
import io.github.mcheliceport.damage.VehicleComponents;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.flare.FlareEntity;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.entity.plane.PlaneEntity;
import io.github.mcheliceport.entity.weapon.ATMissileEntity;
import io.github.mcheliceport.item.AircraftItem;
import io.github.mcheliceport.registry.ModItems;
import io.github.mcheliceport.wingman.ai.AiSkill;
import io.github.mcheliceport.wingman.ai.PilotAi;
import io.github.mcheliceport.wingman.ai.TerrainAvoidance;
import io.github.mcheliceport.wingman.config.WingmanConfig;
import io.github.mcheliceport.wingman.handler.WingmanTickHandler;
import io.github.mcheliceport.wingman.mission.AutonomousState;
import io.github.mcheliceport.wingman.mission.MissionOrder;
import io.github.mcheliceport.wingman.mission.MissionType;
import io.github.mcheliceport.wingman.wingman.WingmanEntry;
import io.github.mcheliceport.wingman.wingman.WingmanRegistry;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * M10d AI pilots: a wingman climbs before a hill it is flying towards, fires its flares at a missile
 * after its reaction time and turns away from it, and breaks off its attack, or flies home from an
 * order, once its tail rotor is shot out.
 */
final class AiPilotGameTests {

    private AiPilotGameTests() {
    }

    static void register() {
        ModGameTests.testAlone("ai_climbs_before_a_hill", 20, AiPilotGameTests::climbs);
        ModGameTests.test("ai_counters_a_missile", 80, AiPilotGameTests::counters);
        ModGameTests.test("ai_crippled_wingman_breaks_off", 20, AiPilotGameTests::breaksOff);
    }

    /** A stone column from the floor up to {@code top} (absolute), or air again. */
    private static void column(GameTestHelper h, int x, int z, int bottom, int top, boolean stone) {
        for (int y = bottom; y <= top; y++) {
            h.getLevel().setBlockAndUpdate(new BlockPos(x, y, z), stone ? Blocks.STONE.defaultBlockState()
                    : Blocks.AIR.defaultBlockState());
        }
    }

    private static PlaneEntity plane(GameTestHelper h, String name, Vec3 relative) {
        AircraftItem item = (AircraftItem) ModItems.contentItems().stream()
                .filter(i -> i.type() == ContentType.PLANE && i.contentName().equals(name))
                .findFirst().orElseThrow().item().get();
        Vec3 pos = h.absoluteVec(relative);
        PlaneEntity plane = (PlaneEntity) item.createAircraft(h.getLevel(), pos.x, pos.y, pos.z);
        plane.initRotationYaw(0.0F);
        h.getLevel().addFreshEntity(plane);
        return plane;
    }

    private static void climbs(GameTestHelper h) {
        GameTestSupport.withConfig(ServerConfig.AI_DIFFICULTY, AiSkill.NORMAL, () -> climbsNormal(h));
        h.succeed();
    }

    /**
     * 30 blocks over the ground behind a moving leader, a helicopter going 5 blocks a second holds its
     * slot's height; a column 10 blocks ahead (2 seconds) reaching 26 blocks up is more than its 18
     * blocks of clearance below it, so it throttles up and stops nosing down. Hovering it does not
     * look ahead. A plane at 20 blocks a second with the column 40 blocks ahead climbs at full throttle.
     */
    private static void climbsNormal(GameTestHelper h) {
        WingmanConfig.setFormation(4, 0, 6, 10, 0, 0);
        // The test area's highest blocks are its ground: the aircraft fly 30 blocks over them.
        TerrainAvoidance.Ground ground = TerrainAvoidance.of(h.getLevel());
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        int floor = (int) TerrainAvoidance.highestAhead(ground, Vec3.atCenterOf(o.offset(8, 0, 0)), new Vec3(0, 0, 1),
                new int[] {3});
        double up = floor + 30 - o.getY();
        HeliEntity leader = GameTestSupport.spawn(h, AH64, new Vec3(2, up, 2), 0.0F);
        HeliEntity wingman = GameTestSupport.spawn(h, AH64, new Vec3(2, up, 2), 0.0F);
        PlaneEntity plane = plane(h, "p-51d", new Vec3(12, up, 2));
        BlockPos hill = BlockPos.ZERO;
        BlockPos planeHill = BlockPos.ZERO;
        try {
            Vec3 slot = WingmanTickHandler.formationPos(leader, 0);
            wingman.setPos(slot);
            WingmanRegistry.put(wingman.getUUID(), new WingmanEntry(leader, 0));
            leader.setDeltaMovement(0, 0, 0.5);
            wingman.setDeltaMovement(0, 0, 0.25);
            WingmanTickHandler.tickStart(h.getLevel());
            double level = wingman.getCurrentThrottle();
            check(h, !PilotAi.isPullingUp(wingman), "30 blocks over the ground is clear");

            hill = BlockPos.containing(wingman.getX(), floor, wingman.getZ() + 10);
            column(h, hill.getX(), hill.getZ(), floor, floor + 24, true);
            WingmanTickHandler.tickStart(h.getLevel());
            check(h, PilotAi.isPullingUp(wingman), "the hill 2 seconds ahead is seen");
            check(h, wingman.getCurrentThrottle() > level + 0.1,
                    "the helicopter throttles up: " + level + " to " + wingman.getCurrentThrottle());
            check(h, wingman.getPitch() <= 0.0F, "and does not nose down into it: " + wingman.getPitch());

            wingman.setDeltaMovement(Vec3.ZERO);
            WingmanTickHandler.tickStart(h.getLevel());
            check(h, !PilotAi.isPullingUp(wingman), "hovering, only the ground under it counts");

            GameTestSupport.withConfig(ServerConfig.AI_TERRAIN, false, () -> {
                wingman.setDeltaMovement(0, 0, 0.25);
                WingmanTickHandler.tickStart(h.getLevel());
                check(h, !PilotAi.isPullingUp(wingman), "with terrain avoidance off it flies upstream's way");
            });
            WingmanRegistry.remove(wingman.getUUID());

            plane.setDeltaMovement(0, 0, 1.0);
            plane.setPitch(0.0F);
            planeHill = BlockPos.containing(plane.getX(), floor, plane.getZ() + 40);
            column(h, planeHill.getX(), planeHill.getZ(), floor, floor + 24, true);
            WingmanRegistry.put(plane.getUUID(), new WingmanEntry(leader, 1));
            WingmanTickHandler.tickStart(h.getLevel());
            check(h, PilotAi.isPullingUp(plane), "the plane sees the hill 2 seconds ahead");
            check(h, plane.getPitch() < 0.0F && plane.getCurrentThrottle() == 1.0,
                    "and climbs at full throttle: pitch " + plane.getPitch() + " throttle " + plane.getCurrentThrottle());
        } finally {
            WingmanConfig.reset();
            WingmanRegistry.remove(wingman.getUUID());
            WingmanRegistry.remove(plane.getUUID());
            column(h, hill.getX(), hill.getZ(), floor, floor + 24, false);
            column(h, planeHill.getX(), planeHill.getZ(), floor, floor + 24, false);
            GameTestSupport.cleanUp(leader);
            GameTestSupport.cleanUp(wingman);
            GameTestSupport.cleanUp(plane);
        }
    }

    /**
     * An anti-tank missile 180 blocks over a wingman homes on it: the pilot fires nothing within its
     * reaction time, then its flares, and turns away for its break.
     */
    private static void counters(GameTestHelper h) {
        HeliEntity leader = GameTestSupport.spawn(h, AH64, new Vec3(16, 1, 4), 0.0F);
        HeliEntity heli = GameTestSupport.spawn(h, AH64, new Vec3(8.5, 1, 4.5), 0.0F);
        h.runAfterDelay(2, () -> {
            check(h, heli.haveFlare() && heli.canUseFlare(), "the AH-64 has flares ready");
            WingmanRegistry.put(heli.getUUID(), new WingmanEntry(leader, 0));
            Vec3 from = heli.position().add(0.0, 180.0, 0.0);
            ATMissileEntity missile = new ATMissileEntity(h.getLevel(), from.x, from.y, from.z, 0.0, 0.0, 0.0, 0.0F,
                    90.0F, 0.0F);
            missile.setName("9k114");
            missile.shootingEntity = GameTestSupport.player(h, GameType.SURVIVAL, new Vec3(8.5, 1, 40.5));
            h.getLevel().addFreshEntity(missile);
            missile.setTargetEntity(heli);
            AiSkill skill = PilotAi.skill();
            h.runAfterDelay(skill.reactionTicks() - 1, () ->
                    check(h, heli.canUseFlare(), "the pilot fired before its reaction time"));
            h.runAfterDelay(skill.reactionTicks() + 6, () -> {
                check(h, !heli.canUseFlare(), "the pilot has not fired its flares");
                check(h, !h.getLevel().getEntitiesOfClass(FlareEntity.class, heli.getBoundingBox().inflate(16.0))
                        .isEmpty(), "no flares out");
                check(h, PilotAi.isBreaking(heli) == (skill.breakTicks() > 0) && (skill.breakTicks() == 0
                        || PilotAi.breakTarget(heli) != null), "the pilot does not break as its skill says");
                missile.discard();
                WingmanRegistry.remove(heli.getUUID());
                GameTestSupport.cleanUp(heli);
                GameTestSupport.cleanUp(leader);
                h.succeed();
            });
        });
    }

    private static int slot(AircraftEntity ac, ComponentKind kind) {
        List<ComponentView> views = VehicleComponents.of(ac);
        for (int i = 0; i < views.size(); i++) {
            if (views.get(i).kind() == kind) {
                return i;
            }
        }
        return -1;
    }

    /**
     * With its tail rotor shot out an attacking wingman stops attacking and will not start again
     * until it is repaired; on an order it flies home.
     */
    private static void breaksOff(GameTestHelper h) {
        HeliEntity leader = GameTestSupport.spawn(h, AH64, new Vec3(2, 12, 2), 0.0F);
        HeliEntity heli = GameTestSupport.spawn(h, AH64, new Vec3(12, 12, 12), 0.0F);
        HeliEntity ordered = GameTestSupport.spawn(h, AH64, new Vec3(20, 12, 20), 0.0F);
        heli.tick();
        ordered.tick();
        try {
            int tail = slot(heli, ComponentKind.TAIL_ROTOR);
            check(h, tail >= 0, "the AH-64 has a tail rotor");
            WingmanEntry entry = new WingmanEntry(leader, 0);
            entry.attackMode = WingmanEntry.ATK_AUTO;
            WingmanRegistry.put(heli.getUUID(), entry);
            WingmanTickHandler.tickStart(h.getLevel());
            check(h, entry.attackMode == WingmanEntry.ATK_AUTO && !PilotAi.isCrippled(heli), "intact, it attacks");

            heli.setComponentHealth(ComponentHealth.withLevel(ComponentHealth.FULL, tail, 0));
            WingmanTickHandler.tickStart(h.getLevel());
            check(h, PilotAi.isCrippled(heli) && entry.attackMode == WingmanEntry.ATK_NONE,
                    "with its tail rotor out it breaks off");
            entry.attackMode = WingmanEntry.ATK_MANUAL;
            WingmanTickHandler.tickStart(h.getLevel());
            check(h, entry.attackMode == WingmanEntry.ATK_NONE, "and refuses a new attack");
            heli.setComponentHealth(ComponentHealth.FULL);
            entry.attackMode = WingmanEntry.ATK_AUTO;
            WingmanTickHandler.tickStart(h.getLevel());
            check(h, entry.attackMode == WingmanEntry.ATK_AUTO && !PilotAi.isCrippled(heli), "repaired, it attacks again");

            MissionOrder order = new MissionOrder();
            order.missionTypes.add(MissionType.CAS);
            order.baseId = "ai_nowhere";
            order.targetX = ordered.getX();
            order.targetZ = ordered.getZ();
            order.cruiseAlt = ordered.getY();
            order.orbitRadius = 50.0;
            WingmanEntry onOrder = new WingmanEntry();
            onOrder.order = order;
            onOrder.autoState = AutonomousState.ON_STATION;
            WingmanRegistry.put(ordered.getUUID(), onOrder);
            ordered.setComponentHealth(ComponentHealth.withLevel(ComponentHealth.FULL, slot(ordered, ComponentKind.TAIL_ROTOR), 0));
            WingmanTickHandler.tickStart(h.getLevel());
            check(h, onOrder.autoState == AutonomousState.RTB && "damaged".equals(onOrder.rtbReason),
                    "on an order it flies home: " + onOrder.autoState);
        } finally {
            WingmanRegistry.remove(heli.getUUID());
            WingmanRegistry.remove(ordered.getUUID());
            GameTestSupport.cleanUp(leader);
            GameTestSupport.cleanUp(heli);
            GameTestSupport.cleanUp(ordered);
        }
        h.succeed();
    }
}
