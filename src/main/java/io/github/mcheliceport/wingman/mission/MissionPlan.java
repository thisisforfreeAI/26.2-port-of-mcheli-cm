package io.github.mcheliceport.wingman.mission;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.mcheliceport.McheliCePort;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

/**
 * Upstream's {@code MissionPlan}: the mission planner's named routes, each a list of
 * {@link MissionNode}s, in the order they were saved. Upstream kept one {@code wingman_routes.json}
 * in the config folder for every world; the port saves a dimension's routes with that dimension,
 * as it does taxi routes, since their nodes name that dimension's markers.
 */
public final class MissionPlan extends SavedData {

    /** The most nodes one route takes, and the longest route name. */
    public static final int MAX_NODES = 64;
    public static final int MAX_NAME_LENGTH = 64;

    private record Route(String name, List<String> nodes) {
        static final Codec<Route> CODEC = RecordCodecBuilder.create(b -> b.group(
                Codec.STRING.fieldOf("name").forGetter(Route::name),
                Codec.STRING.listOf().fieldOf("nodes").forGetter(Route::nodes)).apply(b, Route::new));
    }

    public static final Codec<MissionPlan> CODEC = Route.CODEC.listOf().xmap(MissionPlan::new, MissionPlan::toList);
    public static final SavedDataType<MissionPlan> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "wingman_mission_routes"), MissionPlan::new, CODEC);

    private final Map<String, List<String>> routes = new LinkedHashMap<>();

    public MissionPlan() {
    }

    private MissionPlan(List<Route> list) {
        for (Route r : list) {
            this.routes.put(r.name(), List.copyOf(r.nodes()));
        }
    }

    private List<Route> toList() {
        List<Route> list = new ArrayList<>();
        this.routes.forEach((name, nodes) -> list.add(new Route(name, nodes)));
        return list;
    }

    private static MissionPlan get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    /** Saves the route, replacing any route of that name. */
    public static void put(ServerLevel level, String name, List<MissionNode> nodes) {
        List<String> serialized = new ArrayList<>();
        for (MissionNode n : nodes) {
            serialized.add(n.serialize());
        }
        MissionPlan plan = get(level);
        plan.routes.put(name, List.copyOf(serialized));
        plan.setDirty();
    }

    /** The route's nodes, or null when there is none of that name; a node that no longer parses is left out. */
    public static @Nullable List<MissionNode> get(ServerLevel level, String name) {
        List<String> raw = get(level).routes.get(name);
        if (raw == null) {
            return null;
        }
        List<MissionNode> nodes = new ArrayList<>();
        for (String s : raw) {
            try {
                nodes.add(MissionNode.parse(s));
            } catch (IllegalArgumentException e) {
                McheliCePort.LOGGER.warn("[MissionPlan] Skipping bad node '{}' of route '{}'", s, name);
            }
        }
        return nodes;
    }

    public static boolean remove(ServerLevel level, String name) {
        MissionPlan plan = get(level);
        if (plan.routes.remove(name) == null) {
            return false;
        }
        plan.setDirty();
        return true;
    }

    /** The route names in the order saved. */
    public static List<String> names(ServerLevel level) {
        return List.copyOf(get(level).routes.keySet());
    }

    public static boolean exists(ServerLevel level, String name) {
        return get(level).routes.containsKey(name);
    }

    /** A route name the planner and the command accept: 1 to 64 characters with no spaces. */
    public static boolean isValidName(String name) {
        return !name.isEmpty() && name.length() <= MAX_NAME_LENGTH && name.chars().noneMatch(Character::isWhitespace);
    }
}
