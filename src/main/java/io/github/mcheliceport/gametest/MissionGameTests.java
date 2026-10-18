package io.github.mcheliceport.gametest;

import static io.github.mcheliceport.gametest.GameTestSupport.check;

import io.github.mcheliceport.content.ContentType;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.entity.plane.PlaneEntity;
import io.github.mcheliceport.item.AircraftItem;
import io.github.mcheliceport.net.DispatchOrderPayload;
import io.github.mcheliceport.net.ModNetwork;
import io.github.mcheliceport.net.OpenBaseScreenPayload;
import io.github.mcheliceport.registry.ModBlocks;
import io.github.mcheliceport.registry.ModItems;
import io.github.mcheliceport.wingman.MissionDispatch;
import io.github.mcheliceport.wingman.block.MarkerType;
import io.github.mcheliceport.wingman.block.WingmanMarkerBlock;
import io.github.mcheliceport.wingman.block.WingmanMarkerBlockEntity;
import io.github.mcheliceport.wingman.handler.AutonomousFlightHandler;
import io.github.mcheliceport.wingman.handler.MissionChunkLoader;
import io.github.mcheliceport.wingman.handler.WingmanTickHandler;
import io.github.mcheliceport.wingman.mission.AutonomousState;
import io.github.mcheliceport.wingman.mission.MissionOrder;
import io.github.mcheliceport.wingman.mission.MissionType;
import io.github.mcheliceport.wingman.mission.TaxiRoute;
import io.github.mcheliceport.wingman.registry.MarkerRegistry;
import io.github.mcheliceport.wingman.registry.TaxiRouteRegistry;
import io.github.mcheliceport.wingman.wingman.WingmanEntry;
import io.github.mcheliceport.wingman.wingman.WingmanRegistry;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Upstream's missions: the base screen's {@code DISPATCH_ORDER}, {@code AutonomousFlightHandler}'s
 * order steps and {@code ChunkLoadHandler}.
 */
final class MissionGameTests {

    private MissionGameTests() {
    }

    static void register() {
        ModGameTests.test("wingman_mission_dispatch_checks_the_base", 20, MissionGameTests::dispatch);
        ModGameTests.testAlone("wingman_mission_flies_to_station_and_returns", 20, MissionGameTests::station);
        ModGameTests.testAlone("wingman_mission_taxis_and_parks", 20, MissionGameTests::taxi);
    }

    private static DispatchOrderPayload payload(BlockPos pos, HeliEntity aircraft, String route, List<String> types) {
        return new DispatchOrderPayload(pos, aircraft.getUUID(), route, types, List.of("gun", " AAMissile "), Double.NaN,
                50.0, 1.0E9, 1.0E9, 500, -5, true, false, "");
    }

    /**
     * The screen offers the aircraft nobody flies; an order reaches it only from a player who can
     * edit the base marker, with a known mission type, and its numbers within their limits. The
     * tank is filled and the first weapon other than the gun is chosen.
     */
    private static void dispatch(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos baseRel = new BlockPos(1, 1, 1);
        h.setBlock(baseRel, ModBlocks.WINGMAN_MARKER.get().defaultBlockState());
        BlockPos pos = h.absolutePos(baseRel);
        HeliEntity free = GameTestSupport.spawn(h, GameTestSupport.AH64, new Vec3(6, 2, 2), 0.0F);
        HeliEntity flown = GameTestSupport.spawn(h, GameTestSupport.AH64, new Vec3(12, 2, 2), 0.0F);
        ServerPlayer pilot = h.makeMockServerPlayerInLevel();
        pilot.setGameMode(GameType.SURVIVAL);
        pilot.startRiding(flown, true, true);
        Player near = GameTestSupport.player(h, GameType.SURVIVAL, new Vec3(2.5, 1, 2.5));
        Player far = GameTestSupport.player(h, GameType.SURVIVAL, new Vec3(2.5, 1, 40.5));
        h.runAfterDelay(2, () -> {
            try {
                h.getBlockEntity(baseRel, WingmanMarkerBlockEntity.class).apply(MarkerType.BASE, "msn_disp_base", "", -1);
                OpenBaseScreenPayload screen = WingmanMarkerBlock.baseScreen(level, pos, "msn_disp_base");
                check(h, screen.aircraft().stream().anyMatch(a -> a.uuid().equals(free.getUUID()))
                        && screen.aircraft().stream().noneMatch(a -> a.uuid().equals(flown.getUUID())),
                        "the base screen offers the wrong aircraft");
                check(h, !MissionDispatch.canOrder(free, pos.offset(600, 0, 0)), "an aircraft out of range can be ordered");

                DispatchOrderPayload order = payload(pos, free, "msn_no_route", List.of("cap", " Recon "));
                ModNetwork.applyDispatchOrder(far, order);
                check(h, WingmanRegistry.get(free.getUUID()) == null, "an order applied out of reach");
                ModNetwork.applyDispatchOrder(near, payload(pos, free, "", List.of("bogus")));
                check(h, WingmanRegistry.get(free.getUUID()) == null, "an order without a known type applied");
                ModNetwork.applyDispatchOrder(near, payload(pos, flown, "", List.of("CAP")));
                check(h, WingmanRegistry.get(flown.getUUID()) == null, "an aircraft a player flies took an order");

                free.setFuel(0);
                ModNetwork.applyDispatchOrder(near, order);
                WingmanEntry entry = WingmanRegistry.get(free.getUUID());
                check(h, entry != null && entry.isAutonomous() && entry.leader == null
                        && entry.autoState == AutonomousState.NONE, "the order did not apply");
                MissionOrder o = entry.order;
                check(h, o.missionTypes.equals(new java.util.LinkedHashSet<>(List.of(MissionType.CAP, MissionType.RECON)))
                        && List.copyOf(o.weapons).equals(List.of("gun", "aamissile")) && o.baseId.equals("msn_disp_base"),
                        "the order's types, weapons or base are wrong");
                check(h, o.targetX == 0.0 && o.targetZ == 50.0 && o.orbitRadius == 10000.0
                        && o.cruiseAlt == level.getMaxY() + 256 && o.strikePasses == 100 && o.timeLimitSeconds == 0
                        && o.orbitAttack && !o.useVstol, "the order's numbers are not within their limits");
                check(h, "aamissile".equals(entry.weaponType), "the order's weapon is not its first one but the gun");
                check(h, entry.departureRouteId.isEmpty() && entry.assignedParkingId.isEmpty(),
                        "a route that does not exist was taken");
                check(h, free.getMaxFuel() <= 0 || free.getFuel() == free.getMaxFuel(), "the tank was not filled");

                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                DispatchOrderPayload.CODEC.encode(buf, order);
                DispatchOrderPayload decoded = DispatchOrderPayload.CODEC.decode(buf);
                check(h, decoded.aircraft().equals(order.aircraft()) && decoded.missionTypes().equals(order.missionTypes())
                        && Double.isNaN(decoded.targetX()) && decoded.timeLimitSeconds() == -5
                        && decoded.routeId().equals("msn_no_route") && decoded.orbitAttack(),
                        "the order changed in its codec");
                OpenBaseScreenPayload.CODEC.encode(buf, screen);
                check(h, OpenBaseScreenPayload.CODEC.decode(buf).equals(screen), "the base screen changed in its codec");
            } finally {
                WingmanRegistry.remove(free.getUUID());
                WingmanRegistry.remove(flown.getUUID());
                pilot.stopRiding();
                GameTestSupport.cleanUp(free);
                GameTestSupport.cleanUp(flown);
                h.setBlock(baseRel, Blocks.AIR);
            }
            h.succeed();
        });
    }

    private static MissionOrder order(MissionType type, double x, double z, double cruise) {
        MissionOrder order = new MissionOrder();
        order.missionTypes.add(type);
        order.baseId = "msn_nowhere";
        order.targetX = x;
        order.targetZ = z;
        order.cruiseAlt = cruise;
        order.orbitRadius = 50.0;
        return order;
    }

    private static WingmanEntry ordered(HeliEntity heli, MissionOrder order) {
        WingmanEntry entry = new WingmanEntry();
        entry.order = order;
        entry.strikePassesRemaining = order.strikePasses;
        WingmanRegistry.put(heli.getUUID(), entry);
        return entry;
    }

    /**
     * Upstream's order steps without a base: a helicopter starts in the air, flies to the area,
     * orbits it attacking until its time is up, and with nowhere to land comes down where it is.
     * A strike makes its passes and returns, and an aircraft low on fuel returns. Its chunks stay
     * loaded until a player takes the controls, which ends the order, as the aircraft being
     * destroyed does.
     */
    private static void station(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        HeliEntity heli = GameTestSupport.spawn(h, GameTestSupport.AH64, new Vec3(2, 2, 2), 0.0F);
        ServerPlayer pilot = h.makeMockServerPlayerInLevel();
        try {
            heli.setFuel(heli.getMaxFuel());
            double tx = heli.getX() + 100.0;
            double tz = heli.getZ();
            MissionOrder cap = order(MissionType.CAP, tx, tz, heli.getY() + 20.0);
            cap.timeLimitSeconds = 1;
            WingmanEntry entry = ordered(heli, cap);
            AutonomousFlightHandler.tickStart(level);
            check(h, entry.autoState == AutonomousState.TRANSIT_TO, "the order did not start in the air: " + entry.autoState);
            AutonomousFlightHandler.tickStart(level);
            check(h, entry.autoState == AutonomousState.TRANSIT_TO && entry.autoTargetX == tx
                    && entry.autoTargetY == cap.cruiseAlt, "the transit does not fly to the area");

            // Moved within 30 blocks of the area; moving the aircraft would take it out of the loaded chunks.
            tx = heli.getX() + 10.0;
            cap.targetX = tx;
            AutonomousFlightHandler.tickStart(level);
            check(h, entry.autoState == AutonomousState.ON_STATION && entry.orderTimer == 0,
                    "the aircraft did not arrive within 30 blocks");
            AutonomousFlightHandler.tickStart(level);
            check(h, entry.attackMode == WingmanEntry.ATK_AUTO && Math.abs(entry.missionOrbitAngle - 0.007) < 1e-9
                    && Math.abs(entry.autoTargetX - (tx + Math.cos(0.007) * 50.0)) < 1e-6,
                    "the aircraft does not orbit the area attacking: " + entry.attackMode + " " + entry.missionOrbitAngle
                            + " " + (entry.autoTargetX - tx) + " " + entry.autoState);
            for (int i = 0; i < 30 && entry.autoState == AutonomousState.ON_STATION; i++) {
                AutonomousFlightHandler.tickStart(level);
            }
            check(h, entry.autoState == AutonomousState.RTB && entry.orderTimer == 20
                    && entry.rtbReason.equals("time limit") && entry.attackMode == WingmanEntry.ATK_NONE,
                    "the time limit did not send it back: " + entry.orderTimer);
            AutonomousFlightHandler.tickStart(level);
            check(h, entry.autoState == AutonomousState.VTOL_LAND && entry.autoTargetX == heli.getX(),
                    "with no base the helicopter does not land where it is");

            MissionChunkLoader.tick(level);
            check(h, heli.chunkPosition().equals(MissionChunkLoader.loadedCenter(heli.getUUID())),
                    "the mission's chunks are not loaded");
            pilot.setGameMode(GameType.SURVIVAL);
            pilot.startRiding(heli, true, true);
            AutonomousFlightHandler.tickStart(level);
            check(h, !entry.isAutonomous() && entry.autoState == AutonomousState.NONE,
                    "a player at the controls did not end the order");
            MissionChunkLoader.tick(level);
            check(h, MissionChunkLoader.loadedCenter(heli.getUUID()) == null, "the chunks stayed loaded");
            pilot.stopRiding();

            MissionOrder strike = order(MissionType.STRIKE, heli.getX(), heli.getZ(), heli.getY() + 20.0);
            strike.strikePasses = 1;
            entry = ordered(heli, strike);
            for (int i = 0; i < 3; i++) {
                AutonomousFlightHandler.tickStart(level);
            }
            check(h, entry.autoState == AutonomousState.STRIKE_PASS, "the strike did not start a pass: " + entry.autoState);
            for (int i = 0; i < 300; i++) {
                AutonomousFlightHandler.tickStart(level);
            }
            check(h, entry.autoState == AutonomousState.RTB && entry.strikePassesRemaining == 0
                    && entry.rtbReason.equals("strike complete"), "the strike did not return after its pass");

            if (heli.getMaxFuel() > 0) {
                entry = ordered(heli, order(MissionType.CAP, heli.getX(), heli.getZ(), heli.getY()));
                entry.autoState = AutonomousState.ON_STATION;
                heli.setFuel(heli.getMaxFuel() / 10);
                AutonomousFlightHandler.tickStart(level);
                check(h, entry.autoState == AutonomousState.RTB && entry.rtbReason.equals("bingo fuel"),
                        "below a fifth of its fuel the aircraft did not return");
            }

            heli.setDespawnCount(1);
            AutonomousFlightHandler.tickStart(level);
            check(h, !entry.isAutonomous(), "a destroyed aircraft kept its order");
            heli.setDespawnCount(0);
        } finally {
            WingmanRegistry.remove(heli.getUUID());
            MissionChunkLoader.tick(level);
            pilot.stopRiding();
            GameTestSupport.cleanUp(heli);
        }
        h.succeed();
    }

    private static PlaneEntity plane(GameTestHelper h, String name, Vec3 relative) {
        AircraftItem item = (AircraftItem) ModItems.contentItems().stream()
                .filter(i -> i.type() == ContentType.PLANE && i.contentName().equals(name))
                .findFirst().orElseThrow().item().get();
        Vec3 pos = h.absoluteVec(relative);
        PlaneEntity plane = (PlaneEntity) item.createAircraft(h.getLevel(), pos.x, pos.y, pos.z);
        check(h, plane != null, "created " + name);
        plane.initRotationYaw(0.0F);
        h.getLevel().addFreshEntity(plane);
        return plane;
    }

    private static Vec3 center(MarkerRegistry.MarkerInfo m, double y) {
        return new Vec3(m.pos().getX() + 0.5, y, m.pos().getZ() + 0.5);
    }

    /**
     * A plane on a parking spot taxis out along its route's markers to runway end A, pushed at
     * taxi speed, then lines up; a free spot of the base is one without an aircraft on it. Taxied
     * in, it parks facing the spot's heading, its order ends and it leaves the registry.
     */
    private static void taxi(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String base = "msn_taxi_base";
        List<MarkerRegistry.MarkerInfo> markers = new ArrayList<>();
        MarkerRegistry.MarkerInfo p1 = new MarkerRegistry.MarkerInfo(h.absolutePos(new BlockPos(2, 1, 2)),
                MarkerType.PARKING, "msn_p1", base, 1);
        MarkerRegistry.MarkerInfo p2 = new MarkerRegistry.MarkerInfo(h.absolutePos(new BlockPos(2, 1, 24)),
                MarkerType.PARKING, "msn_p2", base, -1);
        MarkerRegistry.MarkerInfo wp = new MarkerRegistry.MarkerInfo(h.absolutePos(new BlockPos(6, 1, 2)),
                MarkerType.WAYPOINT, "msn_wp", base, -1);
        MarkerRegistry.MarkerInfo ra = new MarkerRegistry.MarkerInfo(h.absolutePos(new BlockPos(10, 1, 2)),
                MarkerType.RUNWAY_A, "msn_ra", base, -1);
        MarkerRegistry.MarkerInfo rb = new MarkerRegistry.MarkerInfo(h.absolutePos(new BlockPos(40, 1, 2)),
                MarkerType.RUNWAY_B, "msn_rb", base, -1);
        markers.addAll(List.of(p1, p2, wp, ra, rb));
        markers.forEach(m -> MarkerRegistry.register(level, m));
        TaxiRoute route = new TaxiRoute("msn_route", base, "msn_p1", "msn_ra", "msn_rb", List.of("msn_wp"), List.of(),
                -1, "");
        TaxiRouteRegistry.save(level, route);
        PlaneEntity plane = plane(h, "p-51d", new Vec3(2.5, 2, 2.5));
        try {
            MarkerRegistry.MarkerInfo free = MarkerRegistry.findAvailableParking(level, base);
            check(h, free != null && free.id().equals("msn_p2"), "the occupied spot was offered");

            MissionOrder cap = new MissionOrder();
            cap.missionTypes.add(MissionType.CAP);
            cap.baseId = base;
            WingmanEntry entry = new WingmanEntry();
            entry.order = cap;
            entry.assignedParkingId = "msn_p1";
            entry.departureRouteId = "msn_route";
            WingmanRegistry.put(plane.getUUID(), entry);
            AutonomousFlightHandler.tickStart(level);
            check(h, entry.autoState == AutonomousState.TAXI_OUT
                    && entry.taxiWpQueue.equals(List.of("msn_p1", "msn_wp", "msn_ra")) && entry.taxiWpIndex == 1,
                    "the plane did not taxi out along its route");
            AutonomousFlightHandler.tickStart(level);
            check(h, entry.autoTargetX == wp.pos().getX() + 0.5 && plane.getCurrentThrottle() == 0.0,
                    "the taxi does not head for the waypoint at idle");

            double x = plane.getX();
            WingmanTickHandler.tickStart(level);
            WingmanTickHandler.tickEnd(level);
            check(h, Math.abs(plane.getX() - x - 0.25) < 1e-6 && Math.abs(Mth.wrapDegrees(plane.getYaw()) + 90.0F) < 1e-3F,
                    "the taxi push did not move the plane a quarter block towards the waypoint");

            plane.setPos(center(wp, plane.getY()));
            AutonomousFlightHandler.tickStart(level);
            plane.setPos(center(ra, plane.getY()));
            AutonomousFlightHandler.tickStart(level);
            AutonomousFlightHandler.tickStart(level);
            check(h, entry.taxiWpIndex == 3 && entry.autoState == AutonomousState.ALIGN,
                    "the plane did not line up at the runway: " + entry.autoState);

            entry.autoState = AutonomousState.TAXI_IN;
            entry.taxiWpQueue = new ArrayList<>(List.of("msn_p1"));
            entry.taxiWpIndex = 0;
            plane.setPos(center(p1, plane.getY()));
            AutonomousFlightHandler.tickStart(level);
            AutonomousFlightHandler.tickStart(level);
            check(h, entry.autoState == AutonomousState.PARKED && !entry.isAutonomous()
                    && Math.abs(Mth.wrapDegrees(plane.getYaw()) + 90.0F) < 1e-3F,
                    "the plane did not park facing east");
            WingmanTickHandler.tickEnd(level);
            check(h, WingmanRegistry.get(plane.getUUID()) == null, "the parked plane stayed in the registry");
        } finally {
            WingmanRegistry.remove(plane.getUUID());
            markers.forEach(m -> MarkerRegistry.unregister(level, m.pos()));
            TaxiRouteRegistry.delete(level, "msn_route");
            GameTestSupport.cleanUp(plane);
        }
        h.succeed();
    }
}
