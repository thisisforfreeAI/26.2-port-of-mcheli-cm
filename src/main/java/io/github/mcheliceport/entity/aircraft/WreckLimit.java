package io.github.mcheliceport.entity.aircraft;

import io.github.mcheliceport.config.CommonConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToDoubleFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

/**
 * Caps how many wrecks can lie around at once, to protect the server's TPS: a burning hulk keeps
 * ticking, smoking and colliding for {@code wrecks.wreckLifetime}. Each time a vehicle is destroyed,
 * the oldest wrecks go first until the area around the new one holds at most
 * {@code wrecks.maxWrecksPerArea} and the whole server at most {@code wrecks.maxWrecks}.
 * The new wreck and wrecks a player still sits in are never removed.
 */
public final class WreckLimit {
    private WreckLimit() {}

    /** Called on the server right after {@code wreck} became a wreck. */
    public static void enforce(ServerLevel level, AircraftEntity wreck) {
        int maxTotal = CommonConfig.get(CommonConfig.MAX_WRECKS);
        int maxArea = CommonConfig.get(CommonConfig.MAX_WRECKS_PER_AREA);
        int radius = CommonConfig.get(CommonConfig.WRECK_AREA_RADIUS);
        if (maxTotal <= 0 && maxArea <= 0) {
            return;
        }
        List<AircraftEntity> wrecks = new ArrayList<>();
        for (ServerLevel l : level.getServer().getAllLevels()) {
            for (var entity : l.getAllEntities()) {
                if (entity instanceof AircraftEntity ac && ac != wreck && ac.isDestroyed() && !ac.isRemoved()
                        && !hasPlayerAboard(ac)) {
                    wrecks.add(ac);
                }
            }
        }
        double r2 = (double) radius * radius;
        List<AircraftEntity> doomed = pick(wrecks, maxTotal, maxArea,
                Comparator.comparingLong(AircraftEntity::getWreckSince).thenComparingInt(AircraftEntity::getId),
                ac -> ac.level() == wreck.level() ? ac.distanceToSqr(wreck) : Double.MAX_VALUE, r2);
        for (AircraftEntity ac : doomed) {
            ac.discard();
        }
    }

    private static boolean hasPlayerAboard(AircraftEntity ac) {
        return ac.hasPassenger(e -> e instanceof Player);
    }

    /**
     * The wrecks to remove so that, with the new wreck added, at most {@code maxArea} lie within
     * {@code sqrt(radiusSq)} of it and at most {@code maxTotal} exist; 0 or less turns a cap off.
     * {@code others} are the removable wrecks, without the new one; {@code oldestFirst} orders them by age.
     */
    static <T> List<T> pick(List<T> others, int maxTotal, int maxArea, Comparator<T> oldestFirst,
            ToDoubleFunction<T> distSqToNew, double radiusSq) {
        List<T> sorted = new ArrayList<>(others);
        sorted.sort(oldestFirst);
        List<T> doomed = new ArrayList<>();
        if (maxArea > 0) {
            List<T> near = new ArrayList<>();
            for (T t : sorted) {
                if (distSqToNew.applyAsDouble(t) <= radiusSq) {
                    near.add(t);
                }
            }
            int excess = near.size() + 1 - maxArea;
            for (int i = 0; i < excess && i < near.size(); i++) {
                doomed.add(near.get(i));
            }
        }
        if (maxTotal > 0) {
            int excess = sorted.size() - doomed.size() + 1 - maxTotal;
            for (int i = 0; i < sorted.size() && excess > 0; i++) {
                T t = sorted.get(i);
                if (!doomed.contains(t)) {
                    doomed.add(t);
                    excess--;
                }
            }
        }
        return doomed;
    }
}
