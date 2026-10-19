package io.github.mcheliceport.entity.aircraft;

import io.github.mcheliceport.config.ServerConfig;
import net.minecraft.world.entity.Entity;

/**
 * Upstream's {@code TrackerHook} for its long-distance patch: a vehicle is sent to a player within
 * its own tracking range rather than within the smaller of that range and the player's view
 * distance, and whether or not the player has its chunk loaded ({@code shouldForceWatch}). The
 * client keeps such vehicles moving and draws them past its render distance
 * ({@code FarVehicles}). Upstream's UAV range for its operator is the port's UAV session instead.
 */
public final class LongDistanceTracking {

    private LongDistanceTracking() {
    }

    /** Whether the server sends this entity past the view distance. */
    public static boolean applies(Entity entity) {
        return entity instanceof AircraftEntity && ServerConfig.SPEC.isLoaded()
                && ServerConfig.LONG_DISTANCE_TRACKING.get();
    }

    /** Upstream {@code getRenderDistance}: the tracking range, unclamped for vehicles. */
    public static int visibleRange(Entity entity, int trackingRange, int viewRange) {
        return applies(entity) ? trackingRange : Math.min(trackingRange, viewRange);
    }
}
