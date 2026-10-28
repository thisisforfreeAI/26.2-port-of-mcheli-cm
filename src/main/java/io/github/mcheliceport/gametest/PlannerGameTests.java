package io.github.mcheliceport.gametest;

import static io.github.mcheliceport.gametest.GameTestSupport.check;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.net.MissionActionPayload;
import io.github.mcheliceport.net.MissionRouteActionPayload;
import io.github.mcheliceport.net.PlannerDataPayload;
import io.github.mcheliceport.wingman.MissionPlanner;
import io.github.mcheliceport.wingman.block.MarkerType;
import io.github.mcheliceport.wingman.config.WingmanConfig;
import io.github.mcheliceport.wingman.handler.AutonomousFlightHandler;
import io.github.mcheliceport.wingman.handler.WingmanTickHandler;
import io.github.mcheliceport.wingman.mission.AutonomousState;
import io.github.mcheliceport.wingman.mission.MissionNode;
import io.github.mcheliceport.wingman.mission.MissionOrder;
import io.github.mcheliceport.wingman.mission.MissionPlan;
import io.github.mcheliceport.wingman.mission.MissionType;
import io.github.mcheliceport.wingman.registry.MarkerRegistry;
import io.github.mcheliceport.wingman.wingman.WingmanEntry;
import io.github.mcheliceport.wingman.wingman.WingmanRegistry;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * Upstream's mission planner: {@code MissionNode}, {@code MissionPlan}, the route-node missions of
 * {@code AutonomousFlightHandler}, {@code PacketMissionAction} and {@code /wingman}.
 */
final class PlannerGameTests {

    private PlannerGameTests() {
    }

    static void register() {
        ModGameTests.test("wingman_planner_routes_and_nodes", 20, PlannerGameTests::routes);
        ModGameTests.testAlone("wingman_planner_mission_flies_its_nodes", 20, PlannerGameTests::mission);
        ModGameTests.testAlone("wingman_command_orders_and_settings", 20, PlannerGameTests::command);
    }

    private static boolean throwsOn(String token) {
        try {
            MissionNode.parse(token);
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    /**
     * Nodes read and write upstream's text, refuse numbers out of range, and take upstream's
     * defaults; routes keep the order saved and leave out a node that no longer reads. A take-off
     * or landing node finds both runway ends from the base's ID or either end's ID. The planner's
     * payloads keep their contents, and Orbit Atk orbits at the order's radius with any weapon.
     */
    private static void routes(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        List<String> saved = new ArrayList<>();
        List<BlockPos> markers = new ArrayList<>();
        try {
            check(h, MissionNode.parse("FLYTO:10.7,80,-3").equals(MissionNode.flyTo(10.7, 80, -3))
                    && MissionNode.parse("flyto:10.7,80,-3").serialize().equals("flyto:10,80,-3"),
                    "a fly-to node does not read or write upstream's text");
            check(h, MissionNode.parse("attack:").attackRadius() == MissionNode.DEFAULT_ATTACK_RADIUS
                    && MissionNode.parse("loiter").durationTicks() == MissionNode.DEFAULT_LOITER_TICKS
                    && MissionNode.parse("land:b1").runwayId().equals("b1")
                    && MissionNode.parse("park:p_1").serialize().equals("park:p_1")
                    && MissionNode.parse("loiter:40").serialize().equals("loiter:40"),
                    "a node's defaults or text are not upstream's");
            check(h, throwsOn("hover:1") && throwsOn("flyto:1,2") && throwsOn("flyto:1,NaN,2") && throwsOn("attack:-5")
                    && throwsOn("loiter:x") && throwsOn("flyto:4e7,0,0"), "a bad node was read");

            MissionPlan.put(level, "pln_t_b", List.of(MissionNode.loiter(5)));
            MissionPlan.put(level, "pln_t_a", List.of(MissionNode.flyTo(1, 2, 3), MissionNode.park("pp")));
            saved.addAll(List.of("pln_t_b", "pln_t_a"));
            List<String> names = MissionPlan.names(level);
            check(h, names.indexOf("pln_t_b") >= 0 && names.indexOf("pln_t_b") < names.indexOf("pln_t_a")
                    && MissionPlan.get(level, "pln_t_a").equals(List.of(MissionNode.flyTo(1, 2, 3), MissionNode.park("pp"))),
                    "the routes are not kept in the order saved");
            check(h, MissionPlan.get(level, "pln_t_none") == null && !MissionPlan.isValidName("a b")
                    && !MissionPlan.isValidName("") && MissionPlan.isValidName("x".repeat(64))
                    && !MissionPlan.isValidName("x".repeat(65)), "the route names are not checked");
            check(h, MissionPlanner.createRoute(level, "pln_t_c", List.of("loiter:1", "bogus")).getString().startsWith("§c")
                    && !MissionPlan.exists(level, "pln_t_c")
                    && MissionPlanner.createRoute(level, "pln_t_c", java.util.Collections.nCopies(65, "loiter"))
                            .getString().startsWith("§c")
                    && !MissionPlan.exists(level, "pln_t_c"), "a bad route was saved");
            check(h, MissionPlanner.deleteRoute(level, "pln_t_b").getString().startsWith("§a")
                    && !MissionPlan.exists(level, "pln_t_b"), "the route was not deleted");

            String base = "pln_t_base";
            MarkerRegistry.MarkerInfo b = new MarkerRegistry.MarkerInfo(h.absolutePos(new BlockPos(1, 1, 1)),
                    MarkerType.BASE, base, "", -1);
            MarkerRegistry.MarkerInfo a = new MarkerRegistry.MarkerInfo(h.absolutePos(new BlockPos(2, 1, 1)),
                    MarkerType.RUNWAY_A, "pln_t_ra", base, -1);
            MarkerRegistry.MarkerInfo e = new MarkerRegistry.MarkerInfo(h.absolutePos(new BlockPos(3, 1, 1)),
                    MarkerType.RUNWAY_B, "pln_t_rb", base, -1);
            for (MarkerRegistry.MarkerInfo m : List.of(b, a, e)) {
                MarkerRegistry.register(level, m);
                markers.add(m.pos());
            }
            for (String id : List.of(base, "pln_t_ra", "pln_t_rb")) {
                MarkerRegistry.MarkerInfo[] rwy = AutonomousFlightHandler.resolveNodeRunway(level, id);
                check(h, rwy != null && rwy[0].pos().equals(a.pos()) && rwy[1].pos().equals(e.pos()),
                        "the runway of '" + id + "' was not found");
            }
            check(h, AutonomousFlightHandler.resolveNodeRunway(level, "pln_t_nowhere") == null,
                    "a runway that does not exist was found");

            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            PlannerDataPayload data = new PlannerDataPayload(
                    List.of(new PlannerDataPayload.Aircraft(UUID.randomUUID(), "ah-64", "LOITER", 1, 3)),
                    List.of(new PlannerDataPayload.Route("r", List.of("loiter:5", "park:p"))),
                    List.of(new PlannerDataPayload.Marker("BASE", "b", new BlockPos(1, -2, 3))), 1.5, 2.5, -3.5);
            PlannerDataPayload.CODEC.encode(buf, data);
            check(h, PlannerDataPayload.CODEC.decode(buf).equals(data), "the planner data changed in its codec");
            MissionActionPayload action = new MissionActionPayload(MissionActionPayload.ASSIGN,
                    Optional.of(UUID.randomUUID()), "r");
            MissionActionPayload.CODEC.encode(buf, action);
            check(h, MissionActionPayload.CODEC.decode(buf).equals(action), "the mission action changed in its codec");
            MissionRouteActionPayload route = new MissionRouteActionPayload(MissionRouteActionPayload.CREATE, "r",
                    List.of("flyto:1,2,3"));
            MissionRouteActionPayload.CODEC.encode(buf, route);
            check(h, MissionRouteActionPayload.CODEC.decode(buf).equals(route), "the route action changed in its codec");

            WingmanEntry orbit = new WingmanEntry();
            orbit.order = new MissionOrder();
            orbit.order.orbitRadius = 77.0;
            Zombie target = new Zombie(EntityTypes.ZOMBIE, level);
            target.setPos(10.0, 70.0, -20.0);
            Vec3 t = WingmanTickHandler.computeOrbitTarget(target, target, orbit);
            check(h, Math.abs(Math.hypot(t.x - 10.0, t.z + 20.0) - 77.0) < 1e-6, "the orbit is not at the order's radius");
            check(h, WingmanTickHandler.isOrbitFireWeapon("rocket", true) && !WingmanTickHandler.isOrbitFireWeapon("rocket", false)
                    && WingmanTickHandler.isOrbitFireWeapon("aamissile", false), "Orbit Atk does not orbit every weapon");
        } finally {
            saved.forEach(n -> MissionPlan.remove(level, n));
            markers.forEach(p -> MarkerRegistry.unregister(level, p));
        }
        h.succeed();
    }

    /**
     * A route replaces the aircraft's order and is flown node by node: a take-off from a runway
     * that does not exist is skipped, a fly-to point is reached within 30 blocks at the cruising
     * height, a loiter circles for its ticks, an attack waits while a hostile mob is in its radius,
     * a park stops on the spot, and a take-off taxis to runway end A. An abort ends the route and
     * leaves orders alone, and an aircraft a player flies takes no route.
     */
    private static void mission(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        HeliEntity heli = GameTestSupport.spawn(h, GameTestSupport.AH64, new Vec3(2, 2, 2), 0.0F);
        HeliEntity flown = GameTestSupport.spawn(h, GameTestSupport.AH64, new Vec3(12, 2, 2), 0.0F);
        ServerPlayer pilot = h.makeMockServerPlayerInLevel();
        String base = "pln_m_base";
        List<MarkerRegistry.MarkerInfo> markers = List.of(
                new MarkerRegistry.MarkerInfo(h.absolutePos(new BlockPos(2, 1, 8)), MarkerType.PARKING, "pln_m_p1", base, 1),
                new MarkerRegistry.MarkerInfo(h.absolutePos(new BlockPos(2, 1, 1)), MarkerType.BASE, base, "", -1),
                new MarkerRegistry.MarkerInfo(h.absolutePos(new BlockPos(2, 1, 70)), MarkerType.RUNWAY_A, "pln_m_ra", base, -1),
                new MarkerRegistry.MarkerInfo(h.absolutePos(new BlockPos(2, 1, 150)), MarkerType.RUNWAY_B, "pln_m_rb", base, -1));
        Zombie zombie = null;
        try {
            markers.forEach(m -> MarkerRegistry.register(level, m));
            pilot.setGameMode(GameType.SURVIVAL);
            pilot.startRiding(flown, true, true);
            MissionPlan.put(level, "pln_m_route", List.of(MissionNode.takeoff("pln_m_none"),
                    MissionNode.flyTo(heli.getX() + 10.0, heli.getY(), heli.getZ()), MissionNode.loiter(3),
                    MissionNode.attack(16.0), MissionNode.park("pln_m_p1"), MissionNode.takeoff(base)));

            check(h, MissionPlanner.assign(level, flown.getUUID(), "pln_m_route", "").getString().startsWith("§c")
                    && WingmanRegistry.get(flown.getUUID()) == null, "an aircraft a player flies took a route");
            check(h, MissionPlanner.assign(level, heli.getUUID(), "pln_m_none", "").getString().startsWith("§c")
                    && WingmanRegistry.get(heli.getUUID()) == null, "a route that does not exist was assigned");

            WingmanEntry entry = new WingmanEntry();
            entry.order = new MissionOrder();
            entry.order.missionTypes.add(MissionType.CAP);
            WingmanRegistry.put(heli.getUUID(), entry);
            check(h, MissionPlanner.abortAll() == 0 && entry.order != null, "an abort ended an order");
            MissionPlanner.assign(level, heli.getUUID(), "pln_m_route", "");
            check(h, entry.order == null && entry.mission != null && entry.mission.size() == 6
                    && entry.autoState == AutonomousState.ENROUTE && entry.isAutonomous(),
                    "the route did not replace the order");

            AutonomousFlightHandler.tickStart(level);
            check(h, entry.missionIndex == 1, "a take-off from a runway that does not exist was not skipped");
            AutonomousFlightHandler.tickStart(level);
            check(h, entry.missionIndex == 2 && entry.autoTargetY == Math.max(heli.getY(), 80.0)
                    && entry.autoState == AutonomousState.ENROUTE, "the fly-to point was not reached at cruising height");
            for (int i = 0; i < 2; i++) {
                AutonomousFlightHandler.tickStart(level);
            }
            check(h, entry.missionIndex == 2 && entry.autoState == AutonomousState.LOITER, "the loiter ended early");
            AutonomousFlightHandler.tickStart(level);
            check(h, entry.missionIndex == 3, "the loiter did not end after its ticks");

            zombie = h.spawn(EntityTypes.ZOMBIE, new BlockPos(5, 2, 2));
            AutonomousFlightHandler.tickStart(level);
            check(h, entry.missionIndex == 3 && entry.autoState == AutonomousState.ATTACK
                    && entry.attackMode == WingmanEntry.ATK_AUTO, "the attack did not wait for the mob");
            zombie.discard();
            zombie = null;
            AutonomousFlightHandler.tickStart(level);
            check(h, entry.missionIndex == 4 && entry.attackMode == WingmanEntry.ATK_NONE,
                    "the attack did not end without mobs");

            AutonomousFlightHandler.tickStart(level);
            check(h, entry.missionIndex == 5 && entry.autoState == AutonomousState.PARKED
                    && entry.assignedParkingId.equals("pln_m_p1"), "the aircraft did not park on the spot");
            AutonomousFlightHandler.tickStart(level);
            check(h, entry.autoState == AutonomousState.TAXI_OUT, "the take-off did not start taxiing");
            AutonomousFlightHandler.tickStart(level);
            check(h, entry.autoTargetX == markers.get(2).pos().getX() + 0.5
                    && entry.autoTargetZ == markers.get(2).pos().getZ() + 0.5,
                    "the take-off from the base does not taxi to runway end A");

            MissionPlanner.abort(heli.getUUID());
            check(h, entry.mission == null && entry.autoState == AutonomousState.NONE && !entry.isAutonomous(),
                    "the abort did not end the route");
            WingmanTickHandler.tickEnd(level);
            check(h, WingmanRegistry.get(heli.getUUID()) == null, "the aircraft stayed in the registry");

            MissionPlan.put(level, "pln_m_short", List.of(MissionNode.loiter(0)));
            MissionPlanner.assign(level, heli.getUUID(), "pln_m_short", "");
            AutonomousFlightHandler.tickStart(level);
            AutonomousFlightHandler.tickStart(level);
            WingmanEntry done = WingmanRegistry.get(heli.getUUID());
            check(h, done != null && done.mission == null && done.autoState == AutonomousState.PARKED,
                    "the finished route did not end parked");
        } finally {
            if (zombie != null) {
                zombie.discard();
            }
            WingmanRegistry.remove(heli.getUUID());
            WingmanRegistry.remove(flown.getUUID());
            MissionPlan.remove(level, "pln_m_route");
            MissionPlan.remove(level, "pln_m_short");
            markers.forEach(m -> MarkerRegistry.unregister(level, m.pos()));
            pilot.stopRiding();
            GameTestSupport.cleanUp(heli);
            GameTestSupport.cleanUp(flown);
        }
        h.succeed();
    }

    private static int run(CommandSourceStack source, String command) throws CommandSyntaxException {
        return source.getServer().getCommands().getDispatcher().execute(command, source);
    }

    private static boolean refused(CommandSourceStack source, String command) {
        try {
            run(source, command);
            return false;
        } catch (CommandSyntaxException e) {
            return true;
        }
    }

    /**
     * {@code /wingman} saves and shows routes, changes the formation values within upstream's
     * config ranges, spawns aircraft only for operators, keeps the parking spot {@code order park}
     * gives until the order, dispatches an order from a base with the mission types' weapons and
     * the numbers given, and aborts it.
     */
    private static void command(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        HeliEntity heli = GameTestSupport.spawn(h, GameTestSupport.AH64, new Vec3(2, 2, 2), 0.0F);
        String base = "pln_c_base";
        List<MarkerRegistry.MarkerInfo> markers = List.of(
                new MarkerRegistry.MarkerInfo(h.absolutePos(new BlockPos(1, 1, 1)), MarkerType.BASE, base, "", -1),
                new MarkerRegistry.MarkerInfo(h.absolutePos(new BlockPos(2, 1, 8)), MarkerType.PARKING, "pln_c_p1", base, 1));
        CommandSourceStack user = player.createCommandSourceStack().withPermission(PermissionSet.NO_PERMISSIONS)
                .withSuppressedOutput();
        CommandSourceStack op = player.createCommandSourceStack().withPermission(LevelBasedPermissionSet.GAMEMASTER)
                .withSuppressedOutput();
        String uuid = heli.getStringUUID();
        try {
            markers.forEach(m -> MarkerRegistry.register(level, m));
            check(h, run(user, "wingman route create pln_c_r flyto:1,80,2 loiter:20 park:pln_c_p1") == 1
                    && MissionPlan.get(level, "pln_c_r").size() == 3 && run(user, "wingman route show pln_c_r") == 3,
                    "the route was not saved");
            check(h, run(user, "wingman route create pln_c_bad flyto:1,2") == 0 && !MissionPlan.exists(level, "pln_c_bad"),
                    "a bad route was saved");
            run(user, "wingman route delete pln_c_r");
            check(h, !MissionPlan.exists(level, "pln_c_r"), "the route was not deleted");

            run(user, "wingman dist 5 -10 15");
            run(user, "wingman maxwings 3");
            run(user, "wingman minalt 50");
            run(user, "wingman maxalt 200");
            check(h, WingmanConfig.formationSideDist == 5 && WingmanConfig.formationAltOffset == -10
                    && WingmanConfig.formationRearDist == 15 && WingmanConfig.maxWingmen == 3
                    && WingmanConfig.minAttackAltitude == 50 && WingmanConfig.maxAttackAltitude == 200,
                    "the formation values were not set");
            run(user, "wingman alt clear");
            check(h, WingmanConfig.minAttackAltitude == 0 && WingmanConfig.maxAttackAltitude == 0,
                    "the altitude limits were not cleared");
            check(h, refused(user, "wingman dist 600 0 0") && refused(user, "wingman maxwings 65")
                    && WingmanConfig.formationSideDist == 5, "a value out of range was taken");
            check(h, refused(user, "wingman spawnuav") && !refused(op, "wingman spawnuav"),
                    "spawnuav is not for operators only");
            check(h, run(user, "wingman follow") == 0 && run(user, "wingman engage " + uuid) == 0,
                    "a player outside an aircraft gave wingman orders");

            check(h, run(user, "wingman order park " + uuid + " pln_c_none") == 0 && WingmanRegistry.get(heli.getUUID()) == null,
                    "a parking spot that does not exist was assigned");
            run(user, "wingman order park " + uuid + " pln_c_p1");
            WingmanTickHandler.tickEnd(level);
            WingmanEntry entry = WingmanRegistry.get(heli.getUUID());
            check(h, entry != null && entry.parkingHold && entry.assignedParkingId.equals("pln_c_p1")
                    && entry.autoState == AutonomousState.PARKED, "the parking spot was not kept");

            check(h, run(user, "wingman order dispatch " + uuid + " pln_c_nowhere cap 1 2") == 0
                    && run(user, "wingman order dispatch " + uuid + " " + base + " bogus 1 2") == 0
                    && entry.order == null, "an order without a base or a known type applied");
            run(user, "wingman order dispatch " + uuid + " " + base + " cap+recon 100 200 150 90 3 120");
            MissionOrder o = entry.order;
            check(h, o != null && o.missionTypes.equals(new java.util.LinkedHashSet<>(List.of(MissionType.CAP, MissionType.RECON)))
                    && o.weapons.containsAll(List.of("aamissile", "gun")) && o.weapons.size() == 2
                    && o.baseId.equals(base) && o.targetX == 100 && o.targetZ == 200 && o.orbitRadius == 150
                    && o.cruiseAlt == 90 && o.strikePasses == 3 && o.timeLimitSeconds == 120,
                    "the order is not the one given");
            check(h, "aamissile".equals(entry.weaponType) && !entry.parkingHold
                    && entry.assignedParkingId.equals("pln_c_p1"), "the order did not leave from the parking spot");
            run(user, "wingman order dispatch " + uuid + " " + base + " \"strike,bogus\" 0 0");
            check(h, entry.order != null && entry.order.orbitRadius == 300 && entry.order.timeLimitSeconds == 600
                    && "asmissile".equals(entry.weaponType), "the order's defaults are not upstream's");
            run(user, "wingman order abort " + uuid);
            check(h, entry.order == null && !entry.isAutonomous(), "the order was not aborted");
            WingmanTickHandler.tickEnd(level);
            check(h, WingmanRegistry.get(heli.getUUID()) == null, "the aborted aircraft stayed in the registry");
        } catch (CommandSyntaxException e) {
            throw new IllegalStateException("a command failed to parse: " + e.getMessage(), e);
        } finally {
            WingmanConfig.reset();
            WingmanRegistry.remove(heli.getUUID());
            MissionPlan.remove(level, "pln_c_r");
            markers.forEach(m -> MarkerRegistry.unregister(level, m.pos()));
            GameTestSupport.cleanUp(heli);
        }
        h.succeed();
    }
}
