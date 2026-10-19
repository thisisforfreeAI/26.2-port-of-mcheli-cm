package io.github.mcheliceport.wingman;

import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.net.DispatchOrderPayload;
import io.github.mcheliceport.net.OpenBaseScreenPayload;
import io.github.mcheliceport.wingman.block.MarkerType;
import io.github.mcheliceport.wingman.block.WingmanMarkerBlockEntity;
import io.github.mcheliceport.wingman.mission.AutonomousState;
import io.github.mcheliceport.wingman.mission.MissionOrder;
import io.github.mcheliceport.wingman.mission.MissionType;
import io.github.mcheliceport.wingman.mission.TaxiRoute;
import io.github.mcheliceport.wingman.registry.TaxiRouteRegistry;
import io.github.mcheliceport.wingman.wingman.WingmanEntry;
import io.github.mcheliceport.wingman.wingman.WingmanRegistry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * The server half of the base screen's Mission tab (upstream {@code PacketBaseAction}'s
 * {@code DISPATCH_ORDER}). Upstream took the base from the client and ordered any aircraft by
 * UUID; the port takes the base from the marker the player can reach and edit, and orders only an
 * aircraft within 512 blocks of it that no player flies, with its numbers clamped (KNOWN_ISSUES 99).
 */
public final class MissionDispatch {

    public static final double BASE_RANGE = 512.0;
    /** Upstream's eight aircraft buttons. */
    public static final int MAX_AIRCRAFT = 8;

    private MissionDispatch() {
    }

    /**
     * Upstream's nearby aircraft of the base screen: those the base can order, the nearest eight.
     * Upstream listed every aircraft within range and showed the first eight it happened to find.
     */
    public static List<OpenBaseScreenPayload.Aircraft> nearbyAircraft(ServerLevel level, BlockPos base) {
        AABB box = new AABB(base).inflate(BASE_RANGE, level.getHeight(), BASE_RANGE);
        List<AircraftEntity> list = level.getEntitiesOfClass(AircraftEntity.class, box, ac -> canOrder(ac, base));
        list.sort(Comparator.comparingDouble(ac -> ac.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(base))));
        List<OpenBaseScreenPayload.Aircraft> result = new ArrayList<>();
        for (AircraftEntity ac : list) {
            if (result.size() >= MAX_AIRCRAFT) {
                break;
            }
            result.add(new OpenBaseScreenPayload.Aircraft(ac.getUUID(), WingmanPanel.aircraftName(ac)));
        }
        return result;
    }

    public static boolean canOrder(AircraftEntity ac, BlockPos base) {
        double dx = ac.getX() - (base.getX() + 0.5);
        double dz = ac.getZ() - (base.getZ() + 0.5);
        return !ac.isRemoved() && !ac.isDestroyed() && !(ac.getPilot() instanceof Player)
                && dx * dx + dz * dz <= BASE_RANGE * BASE_RANGE;
    }

    /** Upstream {@code handleDispatchOrder}. */
    public static void apply(Player player, DispatchOrderPayload p,
            @Nullable WingmanMarkerBlockEntity marker) {
        if (marker == null || marker.getMarkerType() != MarkerType.BASE || marker.getMarkerId().isEmpty()
                || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (!(level.getEntity(p.aircraft()) instanceof AircraftEntity wingman) || !canOrder(wingman, p.pos())) {
            player.sendSystemMessage(Component.literal("§cEntity not found."));
            return;
        }
        MissionOrder order = order(p, marker.getMarkerId(), level);
        if (order == null) {
            player.sendSystemMessage(Component.literal("§cNo valid mission types."));
            return;
        }
        WingmanEntry entry = startOrder(wingman, order);
        entry.assignedParkingId = "";
        if (!p.routeId().isEmpty()) {
            TaxiRoute route = TaxiRouteRegistry.findById(level, p.routeId());
            if (route != null) {
                entry.assignedParkingId = route.parkingId();
                entry.departureRouteId = p.routeId();
                player.sendSystemMessage(Component.literal("§aTaxi route §e" + route.routeId()
                        + "§a → parking §e" + route.parkingId()));
            } else {
                player.sendSystemMessage(Component.literal("§cRoute not found: " + p.routeId() + " — airborne start"));
            }
        }
        player.sendSystemMessage(Component.literal("§aOrder dispatched: §e" + String.join(",", p.missionTypes())
                + "§a → " + p.aircraft().toString().substring(0, 8) + "..."));
    }

    /**
     * Upstream's order start, shared by the base screen and {@code /wingman order dispatch}: fills
     * the tank, so a freshly placed aircraft does not return at once, and puts the order in the
     * aircraft's entry, replacing any planner mission. The parking spot it leaves from is kept.
     */
    public static WingmanEntry startOrder(AircraftEntity wingman, MissionOrder order) {
        if (wingman.getMaxFuel() > 0) {
            wingman.setFuel(wingman.getMaxFuel());
        }
        WingmanEntry entry = WingmanRegistry.get(wingman.getUUID());
        if (entry == null) {
            entry = new WingmanEntry();
            WingmanRegistry.put(wingman.getUUID(), entry);
        }
        entry.order = order;
        entry.mission = null;
        entry.missionIndex = 0;
        entry.parkingHold = false;
        entry.orderTimer = 0;
        entry.missionOrbitAngle = 0.0;
        entry.strikePassesRemaining = order.strikePasses;
        entry.reconMobCount = 0;
        entry.rtbReason = "";
        entry.attackMode = WingmanEntry.ATK_NONE;
        entry.manualTargetId = null;
        entry.diagTick = 0;
        entry.autoState = AutonomousState.NONE;
        entry.missionNodeTimer = 0;
        entry.vtolHoverMode = false;
        entry.vtolOnSent = true;
        entry.climbHold = false;
        entry.departureRouteId = "";
        entry.weaponType = primaryWeapon(order);
        return entry;
    }

    /** The order the payload describes, or null without a valid mission type. */
    public static @Nullable MissionOrder order(DispatchOrderPayload p, String baseId, ServerLevel level) {
        MissionOrder order = new MissionOrder();
        for (String s : p.missionTypes()) {
            try {
                order.missionTypes.add(MissionType.valueOf(s.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                // Upstream skipped unknown types.
            }
        }
        if (order.missionTypes.isEmpty()) {
            return null;
        }
        for (String s : p.weapons()) {
            String t = s.trim().toLowerCase(Locale.ROOT);
            if (!t.isEmpty()) {
                order.weapons.add(t);
            }
        }
        order.baseId = baseId;
        order.targetX = finite(p.targetX(), 0.0, -3.0E7, 3.0E7);
        order.targetZ = finite(p.targetZ(), 0.0, -3.0E7, 3.0E7);
        order.orbitRadius = finite(p.orbitRadius(), 300.0, 0.0, 10000.0);
        order.cruiseAlt = finite(p.cruiseAlt(), 80.0, level.getMinY(), level.getMaxY() + 256);
        order.strikePasses = Mth.clamp(p.strikePasses(), 0, 100);
        order.timeLimitSeconds = Mth.clamp(p.timeLimitSeconds(), 0, 86400);
        order.orbitAttack = p.orbitAttack();
        order.useVstol = p.useVstol();
        order.arrivalRouteId = p.arrivalRouteId();
        return order;
    }

    private static double finite(double v, double fallback, double min, double max) {
        return Double.isFinite(v) ? Mth.clamp(v, min, max) : fallback;
    }

    /** Upstream {@code pickPrimaryWeapon}: the first weapon other than the gun, else every weapon. */
    public static @Nullable String primaryWeapon(MissionOrder order) {
        for (String w : order.weapons) {
            if (!"gun".equals(w)) {
                return w;
            }
        }
        return null;
    }
}
