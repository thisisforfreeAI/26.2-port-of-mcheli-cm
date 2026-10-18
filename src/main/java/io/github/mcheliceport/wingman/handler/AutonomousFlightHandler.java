package io.github.mcheliceport.wingman.handler;

import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.entity.plane.PlaneEntity;
import io.github.mcheliceport.wingman.block.MarkerType;
import io.github.mcheliceport.wingman.mission.AutonomousState;
import io.github.mcheliceport.wingman.mission.MissionOrder;
import io.github.mcheliceport.wingman.mission.MissionType;
import io.github.mcheliceport.wingman.mission.TaxiRoute;
import io.github.mcheliceport.wingman.registry.MarkerRegistry;
import io.github.mcheliceport.wingman.registry.TaxiRouteRegistry;
import io.github.mcheliceport.wingman.wingman.WingmanEntry;
import io.github.mcheliceport.wingman.wingman.WingmanRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Upstream's {@code AutonomousFlightHandler}, its mission-order half: the state machine that flies
 * an order from the base screen's Mission tab. It decides what the aircraft does next and sets the
 * entry's target, throttle and attack mode; {@link WingmanTickHandler} turns that into yaw, pitch
 * and roll each tick. A plane taxis out along its route, lines up, rolls and climbs; a helicopter
 * or VTOL plane on a helipad climbs straight up. It flies to the area, circles and attacks (or
 * flies strike passes, or counts the mobs), returns when the time is up or the fuel is low, flies
 * the landing circuit or lands on a helipad, and taxis back to a parking spot.
 *
 * <p>The route-node missions of upstream's planner and {@code /wingman} come with that increment.
 */
public final class AutonomousFlightHandler {

    private static final double ARRIVE_DIST = 30.0;
    private static final double ALIGN_TOLERANCE = 5.0;
    /** How far ahead along the runway centreline the aircraft steers. */
    private static final double LOOKAHEAD_DIST = 40.0;
    /** The take-off roll pitches up from this distance before runway end B. */
    private static final double ROTATION_DIST = 50.0;
    /** Below this share of its fuel the aircraft returns. */
    private static final double BINGO_FUEL_RATIO = 0.20;
    /** The landing circuit's distance to the side of the runway. */
    private static final double CIRCUIT_OFFSET = 80.0;
    /** The final approach starts this far before runway end A. */
    private static final double CIRCUIT_FINAL_DIST = 250.0;
    /** The roll-out ends this close to runway end B. */
    private static final double TOUCHDOWN_DIST = 20.0;

    private AutonomousFlightHandler() {
    }

    // ---- before the aircraft move ----

    /** Upstream {@code onWorldTick}: one step of each autonomous aircraft's mission. */
    public static void tickStart(ServerLevel level) {
        for (Map.Entry<UUID, WingmanEntry> e : WingmanRegistry.snapshot().entrySet()) {
            WingmanEntry entry = e.getValue();
            if (!entry.isAutonomous() || !(level.getEntity(e.getKey()) instanceof AircraftEntity wingman)
                    || wingman.isRemoved()) {
                continue;
            }
            // The port flies an order only on the server, which a player pilot's client takes over.
            if (wingman.getPilot() instanceof Player || wingman.isDestroyed()) {
                McheliCePort.LOGGER.info("[Order] {} order cancelled: {}", shortId(wingman),
                        wingman.isDestroyed() ? "destroyed" : "a player took the controls");
                cancelOrder(entry);
                continue;
            }
            tickOrder(level, wingman, entry);
        }
    }

    /** Ends the entry's order where it is; without a leader the entry then leaves the registry. */
    public static void cancelOrder(WingmanEntry entry) {
        entry.order = null;
        entry.autoState = AutonomousState.NONE;
        entry.attackMode = WingmanEntry.ATK_NONE;
        entry.vtolHoverMode = false;
    }

    // ---- after the aircraft move ----

    /**
     * Upstream {@code onWorldTickEnd}: after the flight model has moved the aircraft, it holds a
     * VTOL take-off or landing over the pad, levels a landing, holds the glide slope and the roll
     * out, and keeps the take-off roll on the ground and on the centreline before pitching up.
     */
    public static void tickEnd(ServerLevel level) {
        for (Map.Entry<UUID, WingmanEntry> e : WingmanRegistry.snapshot().entrySet()) {
            WingmanEntry entry = e.getValue();
            if (!entry.isAutonomous() || !(level.getEntity(e.getKey()) instanceof AircraftEntity wingman)
                    || wingman.isRemoved()) {
                continue;
            }
            tickEndOne(level, wingman, entry);
        }
    }

    private static void tickEndOne(ServerLevel level, AircraftEntity wingman, WingmanEntry entry) {
        MissionOrder order = entry.order;
        if (order == null) {
            return;
        }
        boolean vtolPadCorrect = entry.autoState == AutonomousState.VTOL_TAKEOFF && entry.missionNodeTimer < 180
                || entry.autoState == AutonomousState.VTOL_LAND && entry.vtolHoverMode;
        if (vtolPadCorrect) {
            MarkerRegistry.MarkerInfo pad = findMarkerInfoById(level, entry.assignedParkingId, order.baseId);
            Vec3 m = wingman.getDeltaMovement();
            if (pad != null) {
                double px = pad.pos().getX() + 0.5 - wingman.getX();
                double pz = pad.pos().getZ() + 0.5 - wingman.getZ();
                double d = Math.sqrt(px * px + pz * pz);
                // Landing, it only slides over the pad within 30 blocks of the ground, so it does not
                // drift sideways high up.
                boolean applyHoriz = true;
                if (entry.autoState == AutonomousState.VTOL_LAND) {
                    double gy = pad.type() == MarkerType.HELIPAD ? pad.pos().getY() + 1.0
                            : groundHeight(level, pad.pos().getX(), pad.pos().getZ());
                    applyHoriz = wingman.getY() <= gy + 30.0;
                }
                double mx = m.x;
                double mz = m.z;
                if (applyHoriz) {
                    if (d > 0.2) {
                        double spd = Math.min(0.5, d * 0.05);
                        mx = px / d * spd;
                        mz = pz / d * spd;
                    } else {
                        mx = 0;
                        mz = 0;
                    }
                }
                double my = m.y;
                if (entry.autoState == AutonomousState.VTOL_TAKEOFF) {
                    my = 0;
                } else if (entry.autoState == AutonomousState.VTOL_LAND) {
                    // At most 2.4 blocks a second down, whatever the aircraft's hover throttle.
                    my = Math.max(my, -0.12);
                }
                wingman.setDeltaMovement(mx, my, mz);
            } else {
                wingman.setDeltaMovement(0, m.y, 0);
            }
        }

        if (entry.autoState == AutonomousState.VTOL_LAND && (entry.vtolHoverMode || entry.missionNodeTimer < 0)) {
            wingman.setPitch(0.0F);
            wingman.setRoll(0.0F);
            return;
        }
        if (entry.autoState == AutonomousState.VTOL_TAKEOFF && entry.missionNodeTimer < 180) {
            double dx = entry.autoTargetX - wingman.getX();
            double dz = entry.autoTargetZ - wingman.getZ();
            if (Math.sqrt(dx * dx + dz * dz) >= 1.0) {
                wingman.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));
            }
            return;
        }
        if (entry.autoState == AutonomousState.LANDING) {
            wingman.setPitch(0.0F);
            wingman.setRoll(0.0F);
            return;
        }
        if (entry.autoState == AutonomousState.CIRCUIT_FINAL) {
            MarkerRegistry.MarkerInfo[] rwy = getOrderRunway(level, entry, order);
            if (rwy != null) {
                holdGlideSlope(wingman, rwy[0], rwy[1]);
            }
            return;
        }
        if (entry.autoState == AutonomousState.CLIMB) {
            // Upstream re-applied the climb attitude after the flight model: -3 to -25 degrees.
            double dyToTarget = entry.autoTargetY - wingman.getY();
            float climbPitch = -(float) Math.toDegrees(Math.atan2(dyToTarget, LOOKAHEAD_DIST));
            wingman.setPitch(Math.max(-25.0F, Math.min(-3.0F, climbPitch)));
            return;
        }
        if (entry.autoState != AutonomousState.TAKEOFF_ROLL) {
            return;
        }
        MarkerRegistry.MarkerInfo[] rwy = getOrderRunway(level, entry, order);
        if (rwy == null) {
            return;
        }
        double ax = rwy[0].pos().getX() + 0.5;
        double az = rwy[0].pos().getZ() + 0.5;
        double rdx = rwy[1].pos().getX() + 0.5 - ax;
        double rdz = rwy[1].pos().getZ() + 0.5 - az;
        double rlen = Math.sqrt(rdx * rdx + rdz * rdz);
        if (rlen < 1) {
            return;
        }
        double dirX = rdx / rlen;
        double dirZ = rdz / rlen;
        wingman.setYaw((float) Math.toDegrees(Math.atan2(-dirX, dirZ)));
        double rollDist = (wingman.getX() - ax) * dirX + (wingman.getZ() - az) * dirZ;
        double distToB = rlen - rollDist;
        // The first 60 ticks stay on the ground whatever lift the flight model makes.
        if (entry.missionNodeTimer <= 60 && entry.groundRollY > 0 && wingman.getY() > entry.groundRollY + 0.3) {
            wingman.setPos(wingman.getX(), entry.groundRollY, wingman.getZ());
            Vec3 m = wingman.getDeltaMovement();
            wingman.setDeltaMovement(m.x, 0, m.z);
        }
        // Then, within ROTATION_DIST of end B, the nose comes up to 20 degrees.
        if (distToB <= ROTATION_DIST && entry.missionNodeTimer > 60) {
            double factor = Math.max(0.0, Math.min(1.0, 1.0 - distToB / ROTATION_DIST));
            wingman.setPitch(-(float) (factor * 20.0));
        }
    }

    /**
     * Upstream's final approach after the flight model: in the air it steers the climb rate to the
     * glide slope from 80 blocks above end A, 250 blocks out, to touchdown; on the ground it rolls
     * on to end A, level.
     */
    private static void holdGlideSlope(AircraftEntity wingman, MarkerRegistry.MarkerInfo rwyA,
            MarkerRegistry.MarkerInfo rwyB) {
        double ax = rwyA.pos().getX() + 0.5;
        double ay = rwyA.pos().getY();
        double az = rwyA.pos().getZ() + 0.5;
        double rdx = rwyB.pos().getX() + 0.5 - ax;
        double rdz = rwyB.pos().getZ() + 0.5 - az;
        double rlen = Math.sqrt(rdx * rdx + rdz * rdz);
        if (rlen < 1) {
            return;
        }
        double dirX = rdx / rlen;
        double dirZ = rdz / rlen;
        double circuitY = ay + 80;
        double touchdown = ay + 1;
        double proj = (wingman.getX() - ax) * -dirX + (wingman.getZ() - az) * -dirZ;
        double glide = touchdown + Math.max(0, proj) * ((circuitY - touchdown) / CIRCUIT_FINAL_DIST);
        glide = Math.max(touchdown, Math.min(circuitY, glide));
        Vec3 m = wingman.getDeltaMovement();
        if (wingman.getY() > ay + 2) {
            // Too high descends at up to 0.4 a tick; too low climbs at up to 0.1.
            double target = Math.max(-0.4, Math.min(0.1, -(wingman.getY() - glide) * 0.15));
            wingman.setDeltaMovement(m.x, target, m.z);
        } else {
            double dx = ax - wingman.getX();
            double dz = az - wingman.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > 1.0) {
                double spd = Math.min(0.4, dist * 0.05);
                wingman.setPos(wingman.getX() + dx / dist * spd, wingman.getY(), wingman.getZ() + dz / dist * spd);
            }
            wingman.setPitch(0.0F);
            wingman.setRoll(0.0F);
        }
    }

    // ---- the order's state machine ----

    private static void tickOrder(ServerLevel level, AircraftEntity wingman, WingmanEntry entry) {
        MissionOrder order = entry.order;
        if (order == null) {
            return;
        }
        if (entry.diagTick % 200 == 1) {
            double dx = entry.autoTargetX - wingman.getX();
            double dz = entry.autoTargetZ - wingman.getZ();
            McheliCePort.LOGGER.debug("[Order/HB] {} state={} vtol={} timer={}/{}sec fuel={}/{} pos=({},{},{}) "
                    + "target=({},{},{}) dist={}", shortId(wingman), entry.autoState, entry.vtolHoverMode ? "ON" : "off",
                    entry.orderTimer, order.timeLimitSeconds, wingman.getFuel(), wingman.getMaxFuel(),
                    (int) wingman.getX(), (int) wingman.getY(), (int) wingman.getZ(), (int) entry.autoTargetX,
                    (int) entry.autoTargetY, (int) entry.autoTargetZ, (int) Math.sqrt(dx * dx + dz * dz));
        }
        if (entry.autoState == AutonomousState.ON_STATION || entry.autoState == AutonomousState.STRIKE_PASS) {
            entry.orderTimer++;
            if (order.timeLimitSeconds > 0 && entry.orderTimer >= order.timeLimitTicks()) {
                triggerRtb(level, wingman, entry, "time limit");
                return;
            }
            if (isBingo(wingman)) {
                triggerRtb(level, wingman, entry, "bingo fuel");
                return;
            }
        }
        switch (entry.autoState) {
            case NONE, PARKED -> initOrder(level, wingman, entry, order);
            case TAXI_OUT -> tickOrderTaxiOut(level, wingman, entry, order);
            case ALIGN, TAKEOFF_ROLL, CLIMB -> {
                MarkerRegistry.MarkerInfo[] rwy = getOrderRunway(level, entry, order);
                if (rwy == null) {
                    entry.autoState = AutonomousState.TRANSIT_TO;
                } else if (tickAlignToClimb(wingman, entry, rwy[0], rwy[1], order.cruiseAlt)) {
                    entry.autoState = AutonomousState.TRANSIT_TO;
                    McheliCePort.LOGGER.info("[Order] {} cruise altitude reached, TRANSIT_TO ({},{})", shortId(wingman),
                            (int) order.targetX, (int) order.targetZ);
                }
            }
            case TRANSIT_TO -> tickOrderTransit(wingman, entry, order);
            case ON_STATION -> tickOrderOnStation(level, wingman, entry, order);
            case STRIKE_PASS -> tickOrderStrikePass(level, wingman, entry, order);
            case RTB -> tickOrderRtb(level, wingman, entry, order);
            case VTOL_TAKEOFF -> tickOrderVtolTakeoff(level, wingman, entry, order);
            case VTOL_LAND -> tickOrderVtolLand(level, wingman, entry, order);
            case DESCEND, CIRCUIT_DOWNWIND, CIRCUIT_BASE, CIRCUIT_FINAL, LANDING -> {
                MarkerRegistry.MarkerInfo[] rwy = getOrderRunway(level, entry, order);
                if (rwy == null) {
                    finishLanding(level, wingman, entry, order, null, null);
                } else if (tickLandingCircuit(wingman, entry, rwy[0], rwy[1])) {
                    finishLanding(level, wingman, entry, order, rwy[0].id(), rwy[1].id());
                }
            }
            case TAXI_IN -> tickOrderTaxiIn(level, wingman, entry, order);
            default -> {
            }
        }
    }

    /**
     * Upstream {@code initOrder}: on a helipad a helicopter or VTOL plane climbs straight up; on a
     * parking spot with a taxi route it taxis out; otherwise it flies straight to the area.
     */
    private static void initOrder(ServerLevel level, AircraftEntity wingman, WingmanEntry entry, MissionOrder order) {
        if (!entry.assignedParkingId.isEmpty()) {
            MarkerRegistry.MarkerInfo parking = findMarkerInfoById(level, entry.assignedParkingId, order.baseId);
            if (parking != null && parking.type() == MarkerType.HELIPAD) {
                if (canUseHelipad(wingman)) {
                    startVtolTakeoff(entry, parking, order);
                    McheliCePort.LOGGER.info("[Order] {} VTOL_TAKEOFF from helipad='{}'", shortId(wingman),
                            entry.assignedParkingId);
                    return;
                }
                McheliCePort.LOGGER.warn("[Order] {} assigned to HELIPAD '{}' but cannot use helipad, airborne start",
                        shortId(wingman), entry.assignedParkingId);
            } else {
                TaxiRoute route = !entry.departureRouteId.isEmpty()
                        ? TaxiRouteRegistry.findById(level, entry.departureRouteId)
                        : TaxiRouteRegistry.findByParking(level, entry.assignedParkingId);
                if (route != null) {
                    entry.taxiWpQueue = new ArrayList<>(route.fullDeparture());
                    // The first entry is the parking spot itself; upstream starts from the next one so
                    // aircraft that are not on the spot do not all gather on it first.
                    entry.taxiWpIndex = entry.taxiWpQueue.size() > 1 ? 1 : 0;
                    entry.autoState = AutonomousState.TAXI_OUT;
                    McheliCePort.LOGGER.info("[Order] {} TAXI_OUT start, parking='{}' route='{}' queue={}",
                            shortId(wingman), entry.assignedParkingId, route.routeId(), entry.taxiWpQueue);
                    return;
                }
                McheliCePort.LOGGER.warn("[Order] {} no TaxiRoute found for parking='{}', falling back to airborne start",
                        shortId(wingman), entry.assignedParkingId);
            }
        }
        entry.autoState = AutonomousState.TRANSIT_TO;
        McheliCePort.LOGGER.info("[Order] {} TRANSIT_TO ({},{}) baseId='{}' timeLimitSec={}", shortId(wingman),
                (int) order.targetX, (int) order.targetZ, order.baseId, order.timeLimitSeconds);
    }

    private static void startVtolTakeoff(WingmanEntry entry, MarkerRegistry.MarkerInfo pad, MissionOrder order) {
        entry.autoState = AutonomousState.VTOL_TAKEOFF;
        entry.vtolHoverMode = true;
        entry.vtolOnSent = false;
        entry.missionNodeTimer = 0;
        entry.assignedParkingId = pad.id();
        entry.autoTargetX = pad.pos().getX() + 0.5;
        entry.autoTargetY = order.cruiseAlt;
        entry.autoTargetZ = pad.pos().getZ() + 0.5;
    }

    /** Upstream {@code tickOrderTaxiOut}: taxis through the route's markers to the runway or helipad. */
    private static void tickOrderTaxiOut(ServerLevel level, AircraftEntity wingman, WingmanEntry entry,
            MissionOrder order) {
        if (entry.taxiWpIndex >= entry.taxiWpQueue.size()) {
            String lastId = entry.taxiWpQueue.isEmpty() ? "" : entry.taxiWpQueue.get(entry.taxiWpQueue.size() - 1);
            MarkerRegistry.MarkerInfo last = findMarkerInfoById(level, lastId, order.baseId);
            if (last != null && last.type() == MarkerType.HELIPAD && canUseHelipad(wingman)) {
                startVtolTakeoff(entry, last, order);
                McheliCePort.LOGGER.info("[Order] {} TAXI_OUT complete -> VTOL_TAKEOFF from helipad '{}'",
                        shortId(wingman), lastId);
                return;
            }
            // A helicopter cannot take off along a runway; a VTOL plane can.
            if (wingman instanceof HeliEntity) {
                McheliCePort.LOGGER.error("[Order] {} ERROR: helicopter on CTOL runway route '{}' - assign a helipad "
                        + "route instead. Aborting to PARKED.", shortId(wingman), entry.departureRouteId);
                entry.autoState = AutonomousState.PARKED;
                entry.order = null;
                return;
            }
            entry.autoState = AutonomousState.ALIGN;
            entry.missionNodeTimer = 0;
            McheliCePort.LOGGER.info("[Order] {} reached runway, starting ALIGN", shortId(wingman));
            return;
        }
        taxiTowards(level, wingman, entry, order, "TAXI_OUT");
    }

    /** Steers the taxi to the queue's next marker and moves on within a block of it. */
    private static void taxiTowards(ServerLevel level, AircraftEntity wingman, WingmanEntry entry, MissionOrder order,
            String phase) {
        String wpId = entry.taxiWpQueue.get(entry.taxiWpIndex);
        MarkerRegistry.MarkerInfo wp = findMarkerInfoById(level, wpId, order.baseId);
        if (wp == null) {
            McheliCePort.LOGGER.warn("[Order] {} {} WP {}/{} id='{}' NOT FOUND in MarkerRegistry - skipping!",
                    shortId(wingman), phase, entry.taxiWpIndex + 1, entry.taxiWpQueue.size(), wpId);
            entry.taxiWpIndex++;
            return;
        }
        double tx = wp.pos().getX() + 0.5;
        double tz = wp.pos().getZ() + 0.5;
        entry.autoTargetX = tx;
        entry.autoTargetY = wingman.getY();
        entry.autoTargetZ = tz;
        setThrottle(wingman, 0.0);
        double dist = Math.sqrt(Math.pow(tx - wingman.getX(), 2) + Math.pow(tz - wingman.getZ(), 2));
        if (dist < 1.0) {
            McheliCePort.LOGGER.info("[Order] {} {} reached WP {}/{}: {}", shortId(wingman), phase,
                    entry.taxiWpIndex + 1, entry.taxiWpQueue.size(), wpId);
            entry.taxiWpIndex++;
        }
    }

    private static void tickOrderTransit(AircraftEntity wingman, WingmanEntry entry, MissionOrder order) {
        cruiseThrottle(wingman, 1.0);
        entry.autoTargetX = order.targetX;
        entry.autoTargetY = order.cruiseAlt;
        entry.autoTargetZ = order.targetZ;
        double dx = order.targetX - wingman.getX();
        double dz = order.targetZ - wingman.getZ();
        if (Math.sqrt(dx * dx + dz * dz) < ARRIVE_DIST) {
            entry.autoState = AutonomousState.ON_STATION;
            entry.orderTimer = 0;
            McheliCePort.LOGGER.info("[Order] {} ON_STATION start types={} target=({},{}) timeLimitSec={}",
                    shortId(wingman), order.missionTypes, (int) order.targetX, (int) order.targetZ,
                    order.timeLimitSeconds);
        }
    }

    /** Upstream {@code tickOrderOnStation}: what each mission type does in the area. */
    private static void tickOrderOnStation(ServerLevel level, AircraftEntity wingman, WingmanEntry entry,
            MissionOrder order) {
        if (order.hasType(MissionType.STRIKE) && entry.strikePassesRemaining > 0) {
            entry.autoState = AutonomousState.STRIKE_PASS;
            entry.missionNodeTimer = 0;
            return;
        }
        if (order.hasType(MissionType.FERRY)) {
            if (entry.orderTimer >= 100) {
                triggerRtb(level, wingman, entry, "ferry complete");
            }
            return;
        }
        if (order.hasType(MissionType.RECON) && !order.hasType(MissionType.CAP) && !order.hasType(MissionType.CAS)
                && !order.hasType(MissionType.ESCORT)) {
            orbit(wingman, entry, order);
            entry.attackMode = WingmanEntry.ATK_NONE;
            countMobs(level, order, entry);
            return;
        }
        orbit(wingman, entry, order);
        entry.attackMode = WingmanEntry.ATK_AUTO;
        if (order.hasType(MissionType.RECON)) {
            countMobs(level, order, entry);
        }
    }

    /** Upstream {@code tickOrderStrikePass}: 15-second passes over the target from the far side. */
    private static void tickOrderStrikePass(ServerLevel level, AircraftEntity wingman, WingmanEntry entry,
            MissionOrder order) {
        entry.missionNodeTimer++;
        entry.attackMode = WingmanEntry.ATK_AUTO;
        cruiseThrottle(wingman, 1.0);
        double angle = entry.missionOrbitAngle + Math.PI;
        double r = Math.min(order.orbitRadius * 0.3, 100.0);
        entry.autoTargetX = order.targetX + Math.cos(angle) * r;
        entry.autoTargetY = order.cruiseAlt;
        entry.autoTargetZ = order.targetZ + Math.sin(angle) * r;
        entry.missionOrbitAngle += 0.012;
        if (entry.missionNodeTimer >= 300) {
            entry.strikePassesRemaining--;
            entry.missionNodeTimer = 0;
            McheliCePort.LOGGER.info("[Order] {} strike pass done, remaining={}", shortId(wingman),
                    entry.strikePassesRemaining);
            if (entry.strikePassesRemaining <= 0) {
                triggerRtb(level, wingman, entry, "strike complete");
            } else {
                entry.autoState = AutonomousState.ON_STATION;
            }
        }
    }

    /**
     * Upstream {@code tickOrderRtb}: a helicopter, or a VTOL plane told to land vertically, lands on
     * the base's helipad (or the one it left from, or where it is); a plane flies to runway end B
     * and starts the landing circuit within 200 blocks of it. A plane with no runway end B stops.
     */
    private static void tickOrderRtb(ServerLevel level, AircraftEntity wingman, WingmanEntry entry, MissionOrder order) {
        cruiseThrottle(wingman, 1.0);
        entry.attackMode = WingmanEntry.ATK_NONE;
        boolean canVtol = wingman instanceof HeliEntity || isVtol(wingman) && order.useVstol;
        if (canVtol) {
            MarkerRegistry.MarkerInfo helipad = MarkerRegistry.findByBase(level, order.baseId, MarkerType.HELIPAD);
            if (helipad == null && !entry.assignedParkingId.isEmpty()) {
                MarkerRegistry.MarkerInfo candidate = findMarkerInfoById(level, entry.assignedParkingId, order.baseId);
                if (candidate != null && candidate.type() == MarkerType.HELIPAD) {
                    helipad = candidate;
                }
            }
            if (helipad != null) {
                entry.assignedParkingId = helipad.id();
                entry.autoTargetX = helipad.pos().getX() + 0.5;
                entry.autoTargetZ = helipad.pos().getZ() + 0.5;
                entry.autoState = AutonomousState.VTOL_LAND;
                entry.missionNodeTimer = 0;
                entry.climbHold = false;
                setVtolHoverMode(wingman, entry, false, "RTB to helipad");
                McheliCePort.LOGGER.info("[Order] {} RTB: VTOL_LAND at helipad='{}'", shortId(wingman), helipad.id());
                return;
            }
        }
        MarkerRegistry.MarkerInfo rwyB = MarkerRegistry.findByBase(level, order.baseId, MarkerType.RUNWAY_B);
        if (rwyB == null) {
            rwyB = MarkerRegistry.findById(level, MarkerType.RUNWAY_B, order.baseId);
        }
        if (rwyB == null) {
            if (canVtol) {
                entry.autoTargetX = wingman.getX();
                entry.autoTargetZ = wingman.getZ();
                entry.autoState = AutonomousState.VTOL_LAND;
                setVtolHoverMode(wingman, entry, true, "RTB in place");
                McheliCePort.LOGGER.warn("[Order] {} RTB: no runway/helipad for base='{}' -> VTOL_LAND in place",
                        shortId(wingman), order.baseId);
            } else {
                entry.order = null;
                entry.autoState = AutonomousState.PARKED;
                McheliCePort.LOGGER.warn("[Order] {} RTB: RUNWAY_B not found for base='{}' - aborting mission",
                        shortId(wingman), order.baseId);
            }
            return;
        }
        double bx = rwyB.pos().getX() + 0.5;
        double bz = rwyB.pos().getZ() + 0.5;
        entry.autoTargetX = bx;
        entry.autoTargetY = order.cruiseAlt;
        entry.autoTargetZ = bz;
        double dist = Math.sqrt(Math.pow(bx - wingman.getX(), 2) + Math.pow(bz - wingman.getZ(), 2));
        if (dist < 200.0) {
            entry.autoState = AutonomousState.DESCEND;
            entry.missionNodeTimer = 0;
            McheliCePort.LOGGER.info("[Order] {} entering landing circuit, dist={} base='{}'", shortId(wingman),
                    (int) dist, order.baseId);
        }
    }

    /**
     * Upstream {@code finishLanding}: taxis to a free parking spot of the base (else the one it left
     * from) along the arrival route, the route from the runway it landed on, the departure route or
     * the spot's route; from runway end B when it stopped there. Without one it parks where it is.
     */
    private static void finishLanding(ServerLevel level, AircraftEntity wingman, WingmanEntry entry, MissionOrder order,
            @Nullable String runwayAId, @Nullable String rwyBId) {
        entry.attackMode = WingmanEntry.ATK_NONE;
        MarkerRegistry.MarkerInfo parking = MarkerRegistry.findAvailableParking(level, order.baseId);
        if (parking == null && !entry.assignedParkingId.isEmpty()) {
            parking = MarkerRegistry.findById(level, MarkerType.PARKING, entry.assignedParkingId, order.baseId);
            if (parking == null) {
                parking = MarkerRegistry.findById(level, MarkerType.HELIPAD, entry.assignedParkingId, order.baseId);
            }
        }
        if (parking != null) {
            TaxiRoute route = null;
            if (!order.arrivalRouteId.isEmpty()) {
                route = TaxiRouteRegistry.findById(level, order.arrivalRouteId);
                if (route == null) {
                    McheliCePort.LOGGER.warn("[Order] {} finishLanding: arrivalRouteId='{}' not found, falling back",
                            shortId(wingman), order.arrivalRouteId);
                }
            }
            if (route == null && runwayAId != null && !runwayAId.isEmpty()) {
                route = TaxiRouteRegistry.findByRunway(level, runwayAId);
                if (route != null && !runwayAId.equals(route.runwayId())) {
                    route = null;
                }
            }
            if (route == null && !entry.departureRouteId.isEmpty()) {
                route = TaxiRouteRegistry.findById(level, entry.departureRouteId);
            }
            if (route == null) {
                route = TaxiRouteRegistry.findByParking(level, parking.id());
            }
            if (route != null) {
                entry.assignedParkingId = parking.id();
                if (rwyBId != null && !rwyBId.isEmpty()) {
                    // Stopped at end B: the arrival queue starts there instead of at end A.
                    List<String> fromA = route.fullArrival();
                    List<String> queue = new ArrayList<>();
                    queue.add(rwyBId);
                    if (fromA.size() > 1) {
                        queue.addAll(fromA.subList(1, fromA.size()));
                    }
                    entry.taxiWpQueue = queue;
                } else {
                    entry.taxiWpQueue = new ArrayList<>(route.fullArrival());
                }
                entry.taxiWpIndex = 0;
                entry.taxiPreX = wingman.getX();
                entry.taxiPreZ = wingman.getZ();
                entry.autoState = AutonomousState.TAXI_IN;
                McheliCePort.LOGGER.info("[Order] {} TAXI_IN to parking '{}' via route '{}' queue={}", shortId(wingman),
                        parking.id(), route.routeId(), entry.taxiWpQueue);
                return;
            }
        }
        park(level, wingman, entry);
        McheliCePort.LOGGER.info("[Order] {} mission complete (no taxi route). parking={} pos=({},{},{})",
                shortId(wingman), parking != null ? parking.id() : "none", (int) wingman.getX(), (int) wingman.getY(),
                (int) wingman.getZ());
    }

    private static void tickOrderTaxiIn(ServerLevel level, AircraftEntity wingman, WingmanEntry entry,
            MissionOrder order) {
        if (entry.taxiWpIndex >= entry.taxiWpQueue.size()) {
            setThrottle(wingman, 0.0);
            park(level, wingman, entry);
            McheliCePort.LOGGER.info("[Order] {} PARKED at '{}' headingYaw={}", shortId(wingman),
                    entry.assignedParkingId, Float.isNaN(entry.parkingHeadingYaw) ? "any"
                            : String.format(Locale.ROOT, "%.0f", entry.parkingHeadingYaw));
            return;
        }
        taxiTowards(level, wingman, entry, order, "TAXI_IN");
    }

    /** Ends the order parked, turned to the parking spot's heading when it has one. */
    private static void park(ServerLevel level, AircraftEntity wingman, WingmanEntry entry) {
        entry.autoState = AutonomousState.PARKED;
        entry.order = null;
        entry.attackMode = WingmanEntry.ATK_NONE;
        applyParkingHeading(level, wingman, entry);
        wingman.setDeltaMovement(Vec3.ZERO);
    }

    // ---- VTOL take-off and landing ----

    private static void setVtolHoverMode(AircraftEntity wingman, WingmanEntry entry, boolean mode, String reason) {
        if (entry.vtolHoverMode != mode) {
            McheliCePort.LOGGER.info("[VTOL] {} hoverMode {} -> {} ({})", shortId(wingman),
                    entry.vtolHoverMode ? "ON" : "OFF", mode ? "ON" : "OFF", reason);
            entry.vtolHoverMode = mode;
        }
    }

    /**
     * Upstream {@code tickOrderVtolTakeoff}: faces 50 blocks out towards the pad's Helipad-B marker
     * (else towards the mission area), waits 80 ticks for the nozzles, idles 60 ticks, runs the
     * throttle up over 100 and climbs to the cruise altitude, held over the pad.
     */
    private static void tickOrderVtolTakeoff(ServerLevel level, AircraftEntity wingman, WingmanEntry entry,
            MissionOrder order) {
        setVtolHoverMode(wingman, entry, true, "VTOL_TAKEOFF ascending");
        double padX = wingman.getX();
        double padZ = wingman.getZ();
        MarkerRegistry.MarkerInfo pad = findMarkerInfoById(level, entry.assignedParkingId, order.baseId);
        if (pad != null) {
            padX = pad.pos().getX() + 0.5;
            padZ = pad.pos().getZ() + 0.5;
        }
        MarkerRegistry.MarkerInfo padB = pad != null && !pad.baseId().isEmpty()
                ? MarkerRegistry.findByBase(level, pad.baseId(), MarkerType.HELIPAD_B) : null;
        double fx = padB != null ? padB.pos().getX() + 0.5 - padX : order.targetX - padX;
        double fz = padB != null ? padB.pos().getZ() + 0.5 - padZ : order.targetZ - padZ;
        double flen = Math.sqrt(fx * fx + fz * fz);
        if (flen > 1.0) {
            // Upstream put the point behind the pad, the way McHeli's heading pointed it.
            entry.autoTargetX = padX - fx / flen * 50.0;
            entry.autoTargetZ = padZ - fz / flen * 50.0;
        } else {
            entry.autoTargetX = padX;
            entry.autoTargetZ = padZ + 50.0;
        }
        entry.missionNodeTimer++;
        if (entry.missionNodeTimer < 80) {
            setThrottle(wingman, 0.0);
            entry.autoTargetY = wingman.getY();
            return;
        }
        if (entry.missionNodeTimer < 140) {
            setThrottle(wingman, 0.1);
            entry.autoTargetY = wingman.getY();
            return;
        }
        if (entry.missionNodeTimer < 240) {
            setThrottle(wingman, 0.1 + (entry.missionNodeTimer - 140) / 100.0 * 0.8);
            entry.autoTargetY = wingman.getY();
            return;
        }
        setThrottle(wingman, 0.9);
        entry.autoTargetY = order.cruiseAlt;
        if (wingman.getY() >= order.cruiseAlt - 5) {
            setVtolHoverMode(wingman, entry, false, "VTOL_TAKEOFF complete -> TRANSIT_TO");
            entry.autoState = AutonomousState.TRANSIT_TO;
            McheliCePort.LOGGER.info("[VTOL_TAKEOFF] {} complete alt={} -> TRANSIT_TO target=({},{})", shortId(wingman),
                    String.format(Locale.ROOT, "%.1f", wingman.getY()), (int) order.targetX, (int) order.targetZ);
        }
    }

    /**
     * Upstream {@code tickOrderVtolLand}: flies in at the cruise altitude; within 20 blocks of the
     * pad it hovers 80 ticks, then descends more slowly as it nears the ground, waits 100 ticks
     * after touching down and taxis to its parking spot.
     */
    private static void tickOrderVtolLand(ServerLevel level, AircraftEntity wingman, WingmanEntry entry,
            MissionOrder order) {
        MarkerRegistry.MarkerInfo pad = findMarkerInfoById(level, entry.assignedParkingId, order.baseId);
        double padX;
        double padZ;
        double groundY;
        if (pad != null) {
            padX = pad.pos().getX() + 0.5;
            padZ = pad.pos().getZ() + 0.5;
            groundY = pad.type() == MarkerType.HELIPAD ? pad.pos().getY() + 1.0
                    : groundHeight(level, (int) Math.floor(padX), (int) Math.floor(padZ));
        } else {
            padX = entry.autoTargetX;
            padZ = entry.autoTargetZ;
            groundY = groundHeight(level, (int) Math.floor(padX), (int) Math.floor(padZ));
        }
        boolean heli = wingman instanceof HeliEntity;
        if (entry.missionNodeTimer < 0) {
            setThrottle(wingman, 0.0);
            setVtolHoverMode(wingman, entry, false, "post-landing wait");
            wingman.setDeltaMovement(Vec3.ZERO);
            entry.autoTargetX = padX;
            entry.autoTargetY = groundY;
            entry.autoTargetZ = padZ;
            entry.missionNodeTimer++;
            if (entry.missionNodeTimer >= 0) {
                finishVtolLanding(level, wingman, entry);
            }
            return;
        }
        double dx = padX - wingman.getX();
        double dz = padZ - wingman.getZ();
        double hDist = Math.sqrt(dx * dx + dz * dz);
        if (hDist > 20) {
            // A VTOL plane flies in as a plane; it cannot move forward in hover mode.
            setVtolHoverMode(wingman, entry, false, "VTOL_LAND approach hDist=" + (int) hDist);
            entry.autoTargetX = padX;
            entry.autoTargetZ = padZ;
            entry.autoTargetY = order.cruiseAlt;
            if (heli && wingman.getY() < groundY + 5.0) {
                // Below a raised pad (after attacking lower down), it climbs over the spot where it
                // started climbing before closing in, so it does not fly in under a roof or deck.
                if (!entry.climbHold) {
                    entry.climbHold = true;
                    entry.climbHoldX = wingman.getX();
                    entry.climbHoldZ = wingman.getZ();
                }
                entry.autoTargetX = entry.climbHoldX;
                entry.autoTargetZ = entry.climbHoldZ;
            } else {
                entry.climbHold = false;
            }
            setThrottle(wingman, heli ? WingmanTickHandler.heliAltitudeThrottle(wingman, order.cruiseAlt) : 0.6);
            return;
        }
        setVtolHoverMode(wingman, entry, true, "VTOL_LAND hDist=" + (int) hDist);
        MarkerRegistry.MarkerInfo padB = pad != null && !pad.baseId().isEmpty()
                ? MarkerRegistry.findByBase(level, pad.baseId(), MarkerType.HELIPAD_B) : null;
        double blen = padB != null ? Math.hypot(padB.pos().getX() + 0.5 - padX, padB.pos().getZ() + 0.5 - padZ) : 0;
        if (padB != null && blen > 1.0) {
            entry.autoTargetX = padX - (padB.pos().getX() + 0.5 - padX) / blen * 50.0;
            entry.autoTargetZ = padZ - (padB.pos().getZ() + 0.5 - padZ) / blen * 50.0;
        } else if (padB == null) {
            double facing = Math.toRadians(wingman.getYaw());
            entry.autoTargetX = padX - Math.sin(facing) * 10.0;
            entry.autoTargetZ = padZ + Math.cos(facing) * 10.0;
        }
        entry.missionNodeTimer++;
        // Upstream held the height it arrived at. Arriving below a raised pad (a roof or a deck), a
        // helicopter climbs above it first rather than holding and then "landing" under it.
        if (entry.missionNodeTimer < 80 || heli && wingman.getY() <= groundY - 2.0) {
            double holdY = heli ? Math.max(wingman.getY(), groundY + 10.0) : wingman.getY();
            entry.autoTargetY = holdY;
            setThrottle(wingman, heli ? WingmanTickHandler.heliAltitudeThrottle(wingman, holdY) : 0.5);
            return;
        }
        double altAboveGround = Math.max(0, wingman.getY() - groundY);
        double descent = altAboveGround > 30 ? 0.45 : 0.45 + (30 - altAboveGround) / 30.0 * 0.12;
        // A helicopter descends the same amount below its own hover throttle as upstream's planes
        // do below 0.57, and 0.03 more so that it still settles at the bottom (KNOWN_ISSUES 97).
        setThrottle(wingman, heli
                ? Math.max(0.0, WingmanTickHandler.heliAltitudeThrottle(wingman, wingman.getY()) + descent - 0.60)
                : descent);
        entry.autoTargetY = groundY;
        // Upstream also took any height below the pad as landed.
        if ((wingman.onGround() || wingman.getY() <= groundY + 1.0 && wingman.getY() > groundY - 2.0) && hDist < 15) {
            entry.missionNodeTimer = -100;
            setThrottle(wingman, 0.0);
            McheliCePort.LOGGER.info("[VTOL_LAND] {} LANDED at ({},{},{}) -> waiting 5s before taxi", shortId(wingman),
                    (int) wingman.getX(), (int) wingman.getY(), (int) wingman.getZ());
        }
    }

    /** Upstream {@code finishVtolLanding}: taxis along the route ending at this helipad, else parks. */
    private static void finishVtolLanding(ServerLevel level, AircraftEntity wingman, WingmanEntry entry) {
        setThrottle(wingman, 0.0);
        setVtolHoverMode(wingman, entry, false, "VTOL landing complete");
        String padId = entry.assignedParkingId;
        TaxiRoute route = padId.isEmpty() ? null : TaxiRouteRegistry.findByRunway(level, padId);
        if (route != null && !padId.equals(route.runwayId())) {
            route = null;
        }
        if (route != null) {
            entry.assignedParkingId = route.parkingId();
            entry.taxiWpQueue = new ArrayList<>(route.fullArrival());
            entry.taxiWpIndex = 0;
            entry.taxiPreX = wingman.getX();
            entry.taxiPreZ = wingman.getZ();
            entry.autoState = AutonomousState.TAXI_IN;
            McheliCePort.LOGGER.info("[Order] {} VTOL landing complete -> TAXI_IN route='{}' queue={}", shortId(wingman),
                    route.routeId(), entry.taxiWpQueue);
            return;
        }
        park(level, wingman, entry);
        McheliCePort.LOGGER.info("[Order] {} VTOL landing complete (no taxi route). pad='{}'", shortId(wingman), padId);
    }

    // ---- helpers ----

    /** Upstream {@code orbit}: around the mission area at the orbit radius, a turn in about 900 ticks. */
    private static void orbit(AircraftEntity wingman, WingmanEntry entry, MissionOrder order) {
        cruiseThrottle(wingman, 1.0);
        entry.missionOrbitAngle += 0.007;
        entry.autoTargetX = order.targetX + Math.cos(entry.missionOrbitAngle) * order.orbitRadius;
        entry.autoTargetY = order.cruiseAlt;
        entry.autoTargetZ = order.targetZ + Math.sin(entry.missionOrbitAngle) * order.orbitRadius;
    }

    /** Upstream {@code countMobs}: every 5 seconds, the hostile mobs within the orbit of the area. */
    private static void countMobs(ServerLevel level, MissionOrder order, WingmanEntry entry) {
        if (entry.orderTimer % 100 != 0) {
            return;
        }
        double r = order.orbitRadius;
        AABB box = new AABB(order.targetX - r, level.getMinY(), order.targetZ - r, order.targetX + r, level.getMaxY(),
                order.targetZ + r);
        entry.reconMobCount = level.getEntitiesOfClass(LivingEntity.class, box, e -> {
            double dx = e.getX() - order.targetX;
            double dz = e.getZ() - order.targetZ;
            return e instanceof Enemy && e.isAlive() && dx * dx + dz * dz < r * r;
        }).size();
    }

    /** Upstream {@code triggerRtb}: a reconnaissance reports its count to players within 500 blocks. */
    private static void triggerRtb(ServerLevel level, AircraftEntity wingman, WingmanEntry entry, String reason) {
        entry.rtbReason = reason;
        entry.autoState = AutonomousState.RTB;
        entry.attackMode = WingmanEntry.ATK_NONE;
        McheliCePort.LOGGER.info("[Order] {} RTB triggered: {}", shortId(wingman), reason);
        if (entry.order != null && entry.order.hasType(MissionType.RECON)) {
            for (ServerPlayer p : level.players()) {
                if (p.distanceTo(wingman) < 500) {
                    p.sendSystemMessage(Component.literal("§e[Recon] " + shortId(wingman) + ": §f"
                            + entry.reconMobCount + " mobs detected in target area (RTB: " + reason + ")"));
                }
            }
        }
    }

    /** Upstream {@code isBingo}: below a fifth of its fuel, for an aircraft that uses fuel. */
    private static boolean isBingo(AircraftEntity wingman) {
        int max = wingman.getMaxFuel();
        if (max <= 0) {
            return false;
        }
        return (double) wingman.getFuel() / max < BINGO_FUEL_RATIO;
    }

    /**
     * Upstream {@code getOrderRunway}: runway end A from the departure route (else the base's), end
     * B from the route's end B (else the base's). Null when either is missing.
     */
    static MarkerRegistry.MarkerInfo @Nullable [] getOrderRunway(ServerLevel level, WingmanEntry entry,
            MissionOrder order) {
        TaxiRoute route = null;
        if (!entry.departureRouteId.isEmpty()) {
            route = TaxiRouteRegistry.findById(level, entry.departureRouteId);
        } else if (!entry.assignedParkingId.isEmpty()) {
            route = TaxiRouteRegistry.findByParking(level, entry.assignedParkingId);
        }
        MarkerRegistry.MarkerInfo rwyA = route != null
                ? MarkerRegistry.findById(level, MarkerType.RUNWAY_A, route.runwayId())
                : MarkerRegistry.findByBase(level, order.baseId, MarkerType.RUNWAY_A);
        MarkerRegistry.MarkerInfo rwyB = null;
        if (route != null && !route.runwayBId().isEmpty()) {
            rwyB = MarkerRegistry.findById(level, MarkerType.RUNWAY_B, route.runwayBId());
        }
        if (rwyB == null) {
            rwyB = MarkerRegistry.findByBase(level, order.baseId, MarkerType.RUNWAY_B);
        }
        if (rwyA == null || rwyB == null) {
            return null;
        }
        return new MarkerRegistry.MarkerInfo[] {rwyA, rwyB};
    }

    /**
     * Upstream {@code tickAlignToClimb}: on the runway it waits to touch down, then turns along it
     * until lined up and slow (or 80 ticks), rolls at full throttle along the centreline, rotates
     * within 50 blocks of end B and climbs to the altitude. True once there.
     */
    static boolean tickAlignToClimb(AircraftEntity wingman, WingmanEntry entry, MarkerRegistry.MarkerInfo rwyA,
            MarkerRegistry.MarkerInfo rwyB, double targetAlt) {
        double ax = rwyA.pos().getX() + 0.5;
        double ay = rwyA.pos().getY();
        double az = rwyA.pos().getZ() + 0.5;
        double rdx = rwyB.pos().getX() + 0.5 - ax;
        double rdz = rwyB.pos().getZ() + 0.5 - az;
        double rlen = Math.sqrt(rdx * rdx + rdz * rdz);
        if (rlen < 1) {
            return true;
        }
        double dirX = rdx / rlen;
        double dirZ = rdz / rlen;
        float runwayYaw = (float) Math.toDegrees(Math.atan2(-dirX, dirZ));
        switch (entry.autoState) {
            case ALIGN -> {
                setThrottle(wingman, 0.0);
                entry.autoTargetX = wingman.getX() + dirX * 1000;
                entry.autoTargetY = ay;
                entry.autoTargetZ = wingman.getZ() + dirZ * 1000;
                if (wingman.getY() - ay > 2.5) {
                    Vec3 m = wingman.getDeltaMovement();
                    wingman.setDeltaMovement(m.x, -0.25, m.z);
                    return false;
                }
                entry.missionNodeTimer++;
                boolean aligned = Math.abs(net.minecraft.util.Mth.wrapDegrees(runwayYaw - wingman.getYaw()))
                        < ALIGN_TOLERANCE;
                boolean slow = wingman.getDeltaMovement().horizontalDistance() < 0.4;
                boolean settled = entry.missionNodeTimer >= 20;
                boolean timedOut = entry.missionNodeTimer > 80;
                if (settled && (aligned && slow || timedOut)) {
                    wingman.setYaw(runwayYaw);
                    entry.missionNodeTimer = 0;
                    entry.groundRollY = wingman.getY();
                    entry.autoState = AutonomousState.TAKEOFF_ROLL;
                    McheliCePort.LOGGER.info("[Order] {} ALIGN->TAKEOFF_ROLL aligned={} timedOut={}", shortId(wingman),
                            aligned, timedOut);
                }
                return false;
            }
            case TAKEOFF_ROLL -> {
                setThrottle(wingman, 1.0);
                wingman.setYaw(runwayYaw);
                entry.missionNodeTimer++;
                double projFromA = (wingman.getX() - ax) * dirX + (wingman.getZ() - az) * dirZ;
                double distToB = rlen - projFromA;
                boolean groundRoll = entry.missionNodeTimer <= 60;
                if (distToB > ROTATION_DIST || groundRoll) {
                    centerlineTarget(entry, wingman, ax, ay, az, dirX, dirZ);
                } else {
                    double factor = Math.max(0.0, Math.min(1.0, 1.0 - distToB / ROTATION_DIST));
                    centerlineTarget(entry, wingman, ax, ay + factor * (targetAlt - ay), az, dirX, dirZ);
                }
                // Airborne: 3 blocks up after rotating and at least 60 ticks rolling, or after 200 ticks.
                if (distToB <= ROTATION_DIST && wingman.getY() > ay + 3 && entry.missionNodeTimer >= 60
                        || entry.missionNodeTimer > 200) {
                    entry.autoState = AutonomousState.CLIMB;
                    McheliCePort.LOGGER.info("[Order] {} TAKEOFF_ROLL->CLIMB posY={} distToB={} timer={}",
                            shortId(wingman), String.format(Locale.ROOT, "%.1f", wingman.getY()),
                            String.format(Locale.ROOT, "%.1f", distToB), entry.missionNodeTimer);
                }
                return false;
            }
            case CLIMB -> {
                setThrottle(wingman, 1.0);
                centerlineTarget(entry, wingman, ax, Math.min(targetAlt, wingman.getY() + 15.0), az, dirX, dirZ);
                return wingman.getY() >= targetAlt - 5;
            }
            default -> {
                entry.autoState = AutonomousState.ALIGN;
                entry.missionNodeTimer = 0;
                return false;
            }
        }
    }

    /** Upstream {@code centerlineTarget}: the point on the runway centreline 40 blocks ahead of the aircraft. */
    private static void centerlineTarget(WingmanEntry entry, AircraftEntity wingman, double ax, double targetY,
            double az, double dirX, double dirZ) {
        double t = (wingman.getX() - ax) * dirX + (wingman.getZ() - az) * dirZ + LOOKAHEAD_DIST;
        entry.autoTargetX = ax + dirX * t;
        entry.autoTargetY = targetY;
        entry.autoTargetZ = az + dirZ * t;
    }

    /**
     * Upstream {@code tickLandingCircuit}: descends to 80 blocks above end A abeam end B, flies
     * downwind to 250 blocks short of end A, turns base onto the centreline, flies final down the
     * glide slope to touch down at end A and rolls out towards end B. True when stopped near end B.
     */
    static boolean tickLandingCircuit(AircraftEntity wingman, WingmanEntry entry, MarkerRegistry.MarkerInfo rwyA,
            MarkerRegistry.MarkerInfo rwyB) {
        double ax = rwyA.pos().getX() + 0.5;
        double ay = rwyA.pos().getY();
        double az = rwyA.pos().getZ() + 0.5;
        double bx = rwyB.pos().getX() + 0.5;
        double bz = rwyB.pos().getZ() + 0.5;
        double rdx = bx - ax;
        double rdz = bz - az;
        double rlen = Math.sqrt(rdx * rdx + rdz * rdz);
        if (rlen < 1) {
            return true;
        }
        double dirX = rdx / rlen;
        double dirZ = rdz / rlen;
        double perpX = -dirZ;
        double perpZ = dirX;
        double circuitY = ay + 80;
        switch (entry.autoState) {
            case DESCEND -> {
                setThrottle(wingman, Math.max(0.35, Math.min(0.9, 0.65 + (circuitY - wingman.getY()) * 0.02)));
                double epX = bx + perpX * CIRCUIT_OFFSET;
                double epZ = bz + perpZ * CIRCUIT_OFFSET;
                setTarget(entry, epX, circuitY, epZ);
                if (hdist(wingman, epX, epZ) < 30) {
                    entry.autoState = AutonomousState.CIRCUIT_DOWNWIND;
                    McheliCePort.LOGGER.info("[Circuit] {} -> CIRCUIT_DOWNWIND", shortId(wingman));
                }
                return false;
            }
            case CIRCUIT_DOWNWIND -> {
                setThrottle(wingman, Math.max(0.4, Math.min(0.9, 0.65 + (circuitY - wingman.getY()) * 0.02)));
                double dwX = ax - dirX * CIRCUIT_FINAL_DIST + perpX * CIRCUIT_OFFSET;
                double dwZ = az - dirZ * CIRCUIT_FINAL_DIST + perpZ * CIRCUIT_OFFSET;
                setTarget(entry, dwX, circuitY, dwZ);
                entry.missionNodeTimer++;
                if (hdist(wingman, dwX, dwZ) < 30) {
                    entry.missionNodeTimer = 0;
                    entry.autoState = AutonomousState.CIRCUIT_BASE;
                    McheliCePort.LOGGER.info("[Circuit] {} -> CIRCUIT_BASE", shortId(wingman));
                }
                return false;
            }
            case CIRCUIT_BASE -> {
                setThrottle(wingman, Math.max(0.4, Math.min(0.9, 0.65 + (circuitY - wingman.getY()) * 0.02)));
                double fX = ax - dirX * CIRCUIT_FINAL_DIST;
                double fZ = az - dirZ * CIRCUIT_FINAL_DIST;
                setTarget(entry, fX, circuitY, fZ);
                entry.missionNodeTimer++;
                if (hdist(wingman, fX, fZ) < 40) {
                    entry.missionNodeTimer = 0;
                    entry.autoState = AutonomousState.CIRCUIT_FINAL;
                    McheliCePort.LOGGER.info("[Circuit] {} -> CIRCUIT_FINAL", shortId(wingman));
                }
                return false;
            }
            case CIRCUIT_FINAL -> {
                double projFromA = (wingman.getX() - ax) * -dirX + (wingman.getZ() - az) * -dirZ;
                double tProj = Math.max(0, projFromA - LOOKAHEAD_DIST);
                double touchdownAlt = ay + 1;
                double glideAlt = touchdownAlt + Math.max(0, projFromA) * ((circuitY - touchdownAlt) / CIRCUIT_FINAL_DIST);
                glideAlt = Math.max(touchdownAlt, Math.min(circuitY, glideAlt));
                if (wingman.getY() <= ay + 1) {
                    setThrottle(wingman, 0.0);
                    setTarget(entry, ax - dirX * tProj, ay - 10, az - dirZ * tProj);
                } else {
                    setThrottle(wingman, Math.max(0.1, Math.min(0.75, 0.55 - (wingman.getY() - glideAlt) * 0.02)));
                    setTarget(entry, ax - dirX * tProj, glideAlt, az - dirZ * tProj);
                }
                entry.missionNodeTimer++;
                double hDist = hdist(wingman, ax, az);
                if (hDist < 8.0 && wingman.getY() <= ay + 6 || projFromA <= 0) {
                    entry.autoState = AutonomousState.LANDING;
                    entry.landingRollSpeed = Math.max(wingman.getDeltaMovement().horizontalDistance(), 0.5);
                    entry.taxiPreX = wingman.getX();
                    entry.taxiPreZ = wingman.getZ();
                    McheliCePort.LOGGER.info("[Circuit] {} LANDING at A-end hDist={} posY={} projFromA={}",
                            shortId(wingman), String.format(Locale.ROOT, "%.1f", hDist),
                            String.format(Locale.ROOT, "%.1f", wingman.getY()),
                            String.format(Locale.ROOT, "%.1f", projFromA));
                }
                return false;
            }
            case LANDING -> {
                setThrottle(wingman, 0.0);
                setTarget(entry, bx, ay - 10, bz);
                if (hdist(wingman, bx, bz) < TOUCHDOWN_DIST) {
                    McheliCePort.LOGGER.info("[Order] {} stopped near B-end at ({},{},{})", shortId(wingman),
                            (int) wingman.getX(), (int) wingman.getY(), (int) wingman.getZ());
                    return true;
                }
                return false;
            }
            default -> {
                entry.autoState = AutonomousState.DESCEND;
                return false;
            }
        }
    }

    private static void setTarget(WingmanEntry entry, double x, double y, double z) {
        entry.autoTargetX = x;
        entry.autoTargetY = y;
        entry.autoTargetZ = z;
    }

    private static double hdist(AircraftEntity wingman, double x, double z) {
        double dx = x - wingman.getX();
        double dz = z - wingman.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * Upstream {@code findMarkerInfoById}: the marker of any type with the ID, preferring one of the
     * base.
     */
    static MarkerRegistry.@Nullable MarkerInfo findMarkerInfoById(ServerLevel level, @Nullable String markerId,
            String baseId) {
        if (markerId == null || markerId.isEmpty()) {
            return null;
        }
        MarkerRegistry.MarkerInfo fallback = null;
        for (MarkerRegistry.MarkerInfo m : MarkerRegistry.snapshot(level)) {
            if (!markerId.equals(m.id())) {
                continue;
            }
            if (!baseId.isEmpty() && baseId.equals(m.baseId())) {
                return m;
            }
            if (fallback == null) {
                fallback = m;
            }
        }
        return fallback;
    }

    /**
     * Upstream {@code applyParkingHeading}: the heading set on the parking marker. Upstream then
     * held it only while the order was still active, which it no longer was once parked, so the
     * port turns the aircraft to it as it parks.
     */
    private static void applyParkingHeading(ServerLevel level, AircraftEntity wingman, WingmanEntry entry) {
        entry.parkingHeadingYaw = Float.NaN;
        if (entry.assignedParkingId.isEmpty()) {
            return;
        }
        for (MarkerRegistry.MarkerInfo m : MarkerRegistry.snapshot(level)) {
            if (m.type() == MarkerType.PARKING && entry.assignedParkingId.equals(m.id())) {
                if (m.parkingHeading() >= 0) {
                    entry.parkingHeadingYaw = compassToYaw(m.parkingHeading());
                    wingman.setYaw(entry.parkingHeadingYaw);
                }
                return;
            }
        }
    }

    /** North, east, south, west to Minecraft yaw. */
    static float compassToYaw(int heading) {
        return switch (heading) {
            case 0 -> 180.0F;
            case 1 -> -90.0F;
            case 3 -> 90.0F;
            default -> 0.0F;
        };
    }

    private static double groundHeight(ServerLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
    }

    public static boolean isVtol(AircraftEntity aircraft) {
        return aircraft instanceof PlaneEntity plane && plane.partNozzle != null;
    }

    /** Helicopters and VTOL planes can use helipads. */
    public static boolean canUseHelipad(AircraftEntity aircraft) {
        return aircraft instanceof HeliEntity || isVtol(aircraft);
    }

    private static void setThrottle(AircraftEntity aircraft, double throttle) {
        aircraft.setCurrentThrottle(throttle);
    }

    /**
     * The cruising throttle of transit, orbit, strike and return. A helicopter keeps the throttle
     * {@link WingmanTickHandler} set for its target height instead, as in formation (KNOWN_ISSUES 97).
     */
    private static void cruiseThrottle(AircraftEntity aircraft, double throttle) {
        if (!(aircraft instanceof HeliEntity)) {
            setThrottle(aircraft, throttle);
        }
    }

    static String shortId(AircraftEntity e) {
        return e.getUUID().toString().substring(0, 8);
    }
}
