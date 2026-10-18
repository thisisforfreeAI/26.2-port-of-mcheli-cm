package io.github.mcheliceport.wingman.registry;

import com.mojang.serialization.Codec;
import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.wingman.mission.TaxiRoute;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

/** Upstream's {@code TaxiRouteRegistry}: a dimension's taxi routes by route ID, in the order they were saved. */
public final class TaxiRouteRegistry extends SavedData {

    public static final Codec<TaxiRouteRegistry> CODEC =
            TaxiRoute.CODEC.listOf().xmap(TaxiRouteRegistry::new, r -> List.copyOf(r.routes.values()));
    public static final SavedDataType<TaxiRouteRegistry> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "wingman_taxi_routes"), TaxiRouteRegistry::new,
            CODEC);

    private final Map<String, TaxiRoute> routes = new LinkedHashMap<>();

    public TaxiRouteRegistry() {
    }

    private TaxiRouteRegistry(List<TaxiRoute> routes) {
        for (TaxiRoute route : routes) {
            this.routes.put(route.routeId(), route);
        }
    }

    private static TaxiRouteRegistry get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    /** Saves the route, replacing any route with its ID. */
    public static void save(ServerLevel level, TaxiRoute route) {
        TaxiRouteRegistry registry = get(level);
        registry.routes.put(route.routeId(), route);
        registry.setDirty();
    }

    public static void delete(ServerLevel level, String routeId) {
        TaxiRouteRegistry registry = get(level);
        if (registry.routes.remove(routeId) != null) {
            registry.setDirty();
        }
    }

    public static List<TaxiRoute> getForBase(ServerLevel level, String baseId) {
        List<TaxiRoute> result = new ArrayList<>();
        for (TaxiRoute route : get(level).routes.values()) {
            if (baseId.equals(route.baseId())) {
                result.add(route);
            }
        }
        return result;
    }

    public static @Nullable TaxiRoute findByParking(ServerLevel level, String parkingId) {
        for (TaxiRoute route : get(level).routes.values()) {
            if (parkingId.equals(route.parkingId())) {
                return route;
            }
        }
        return null;
    }

    /** The route ending at the runway end or helipad, for taxiing in after a vertical landing. */
    public static @Nullable TaxiRoute findByRunway(ServerLevel level, String runwayId) {
        for (TaxiRoute route : get(level).routes.values()) {
            if (runwayId.equals(route.runwayId())) {
                return route;
            }
        }
        return null;
    }

    public static @Nullable TaxiRoute findById(ServerLevel level, String routeId) {
        return get(level).routes.get(routeId);
    }

    public static List<TaxiRoute> snapshot(ServerLevel level) {
        return new ArrayList<>(get(level).routes.values());
    }
}
