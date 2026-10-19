package io.github.mcheliceport.wingman;

import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.net.MissionActionPayload;
import io.github.mcheliceport.net.MissionRouteActionPayload;
import io.github.mcheliceport.net.PlannerDataPayload;
import io.github.mcheliceport.wingman.handler.AutonomousFlightHandler;
import io.github.mcheliceport.wingman.mission.AutonomousState;
import io.github.mcheliceport.wingman.mission.MissionNode;
import io.github.mcheliceport.wingman.mission.MissionPlan;
import io.github.mcheliceport.wingman.registry.MarkerRegistry;
import io.github.mcheliceport.wingman.wingman.WingmanEntry;
import io.github.mcheliceport.wingman.wingman.WingmanRegistry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * The server half of upstream's mission planner ({@code /wingman gui}, {@code PacketMissionAction}
 * and {@code PacketRouteAction}), which {@code /wingman route} and {@code /wingman mission} share.
 *
 * <p>Upstream sent a route to any aircraft UUID a client named and left an order it was flying in
 * charge. The port only sends an aircraft in the player's dimension that no player flies, and a
 * route replaces the aircraft's order; an abort ends planner missions and leaves orders alone
 * (KNOWN_ISSUES 100).
 */
public final class MissionPlanner {

    private MissionPlanner() {
    }

    /** Upstream {@code executeGui}: every aircraft loaded in the dimension, nearest first, and the routes and markers. */
    public static PlannerDataPayload buildData(ServerPlayer player) {
        ServerLevel level = player.level();
        List<AircraftEntity> aircraft = new ArrayList<>();
        for (Entity e : level.getAllEntities()) {
            if (e instanceof AircraftEntity ac && !ac.isRemoved()) {
                aircraft.add(ac);
            }
        }
        aircraft.sort(Comparator.comparingDouble(player::distanceToSqr));
        Map<UUID, WingmanEntry> registry = WingmanRegistry.snapshot();
        List<PlannerDataPayload.Aircraft> uavs = new ArrayList<>();
        for (AircraftEntity ac : aircraft) {
            if (uavs.size() >= PlannerDataPayload.MAX_AIRCRAFT) {
                break;
            }
            WingmanEntry entry = registry.get(ac.getUUID());
            String state;
            int nodeIdx = 0;
            int nodeCount = 0;
            if (entry == null) {
                state = "UNASSIGNED";
            } else {
                state = entry.isAutonomous() ? entry.autoState.name() : entry.leader != null ? "FOLLOWING" : "IDLE";
                nodeIdx = entry.missionIndex;
                nodeCount = entry.mission != null ? entry.mission.size() : 0;
            }
            uavs.add(new PlannerDataPayload.Aircraft(ac.getUUID(), WingmanPanel.aircraftName(ac), state, nodeIdx,
                    nodeCount));
        }
        List<PlannerDataPayload.Route> routes = new ArrayList<>();
        for (String name : MissionPlan.names(level)) {
            if (routes.size() >= PlannerDataPayload.MAX_ROUTES) {
                break;
            }
            List<String> nodes = new ArrayList<>();
            List<MissionNode> parsed = MissionPlan.get(level, name);
            if (parsed != null) {
                for (MissionNode n : parsed) {
                    nodes.add(n.serialize());
                }
            }
            routes.add(new PlannerDataPayload.Route(name, nodes));
        }
        List<PlannerDataPayload.Marker> markers = new ArrayList<>();
        for (MarkerRegistry.MarkerInfo m : MarkerRegistry.snapshot(level)) {
            if (markers.size() >= PlannerDataPayload.MAX_MARKERS) {
                break;
            }
            markers.add(new PlannerDataPayload.Marker(m.type().name(), m.id(), m.pos()));
        }
        return new PlannerDataPayload(uavs, routes, markers, player.getX(), player.getY(), player.getZ());
    }

    /** Upstream {@code PacketMissionAction#onReceive}. */
    public static void apply(Player player, MissionActionPayload p) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (p.action() == MissionActionPayload.ASSIGN) {
            UUID uid = p.aircraft().orElse(null);
            if (uid == null) {
                return;
            }
            player.sendSystemMessage(assign(level, uid, p.routeName()));
        } else if (p.action() == MissionActionPayload.ABORT) {
            UUID uid = p.aircraft().orElse(null);
            if (uid == null) {
                player.sendSystemMessage(Component.literal("§aAborted " + abortAll() + " mission(s)."));
            } else {
                abort(uid);
                player.sendSystemMessage(Component.literal("§aMission aborted."));
            }
        }
    }

    /** Upstream {@code PacketRouteAction#onReceive}: saves a route from its nodes' text, or deletes one. */
    public static void apply(Player player, MissionRouteActionPayload p) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (p.action() == MissionRouteActionPayload.CREATE) {
            player.sendSystemMessage(createRoute(level, p.name(), p.nodes()));
        } else if (p.action() == MissionRouteActionPayload.DELETE) {
            player.sendSystemMessage(deleteRoute(level, p.name()));
        }
    }

    /** Saves the route, or says why not. */
    public static Component createRoute(ServerLevel level, String name, List<String> tokens) {
        if (!MissionPlan.isValidName(name) || tokens.isEmpty()) {
            return Component.literal("§cRoute name and nodes required.");
        }
        if (tokens.size() > MissionPlan.MAX_NODES) {
            return Component.literal("§cA route takes at most " + MissionPlan.MAX_NODES + " nodes.");
        }
        List<MissionNode> parsed = new ArrayList<>();
        for (String s : tokens) {
            try {
                parsed.add(MissionNode.parse(s));
            } catch (IllegalArgumentException e) {
                return Component.literal("§cInvalid node: " + s);
            }
        }
        MissionPlan.put(level, name, parsed);
        return Component.literal("§aRoute §e" + name + "§a saved (" + parsed.size() + " nodes).");
    }

    public static Component deleteRoute(ServerLevel level, String name) {
        return MissionPlan.remove(level, name)
                ? Component.literal("§aRoute §e" + name + "§a deleted.")
                : Component.literal("§cRoute not found: " + name);
    }

    /** Whether a planner route can be sent to the aircraft: one that is there and no player flies. */
    public static boolean canAssign(@Nullable Entity e) {
        return e instanceof AircraftEntity ac && !ac.isRemoved() && !ac.isDestroyed()
                && !(ac.getPilot() instanceof Player);
    }

    /** Upstream's {@code ASSIGN}: the aircraft flies the route from its first node. */
    public static Component assign(ServerLevel level, UUID uid, String routeName) {
        Entity wingman = level.getEntity(uid);
        if (!canAssign(wingman)) {
            return Component.literal("§cEntity not found.");
        }
        List<MissionNode> nodes = MissionPlan.get(level, routeName);
        if (nodes == null || nodes.isEmpty()) {
            return Component.literal("§cRoute not found: " + routeName);
        }
        WingmanEntry entry = WingmanRegistry.get(uid);
        if (entry == null) {
            entry = new WingmanEntry();
            WingmanRegistry.put(uid, entry);
        }
        AutonomousFlightHandler.cancelOrder(entry);
        entry.startMission(nodes);
        return Component.literal("§aMission §e" + routeName + "§a assigned to "
                + uid.toString().substring(0, 8) + "... (" + nodes.size() + " nodes).");
    }

    /** Ends the aircraft's planner mission; a wingman returns to its leader. */
    public static void abort(UUID uid) {
        WingmanEntry entry = WingmanRegistry.get(uid);
        if (entry != null && entry.mission != null) {
            endMission(entry);
        }
    }

    /** Ends every planner mission; the number ended. */
    public static int abortAll() {
        int count = 0;
        for (WingmanEntry e : WingmanRegistry.snapshot().values()) {
            if (e.mission != null && e.isAutonomous()) {
                endMission(e);
                count++;
            }
        }
        return count;
    }

    private static void endMission(WingmanEntry entry) {
        entry.mission = null;
        entry.missionIndex = 0;
        if (entry.order == null) {
            entry.autoState = AutonomousState.NONE;
            entry.attackMode = WingmanEntry.ATK_NONE;
            entry.vtolHoverMode = false;
        }
    }
}
