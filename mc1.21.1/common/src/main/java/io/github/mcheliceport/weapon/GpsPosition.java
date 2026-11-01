package io.github.mcheliceport.weapon;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * Upstream's {@code GPSPosition}: the GPS point a player entered. The client keeps its own for the
 * HUD marker and sends it to the server, which keeps each player's; as upstream, nothing on the
 * server reads it yet (GPS missiles do not steer to it).
 */
public final class GpsPosition {

    private static final Map<UUID, Point> SERVER = new ConcurrentHashMap<>();
    private static volatile @Nullable Point client;

    private GpsPosition() {
    }

    public record Point(double x, double y, double z, boolean active) {
    }

    /** Upstream {@code PacketResetGPS.onReceive}. */
    public static void setServer(Player player, Point point) {
        SERVER.put(player.getUUID(), point);
    }

    public static @Nullable Point get(Player player) {
        return SERVER.get(player.getUUID());
    }

    public static void setClient(@Nullable Point point) {
        client = point;
    }

    /** The client's active GPS point, or null. */
    public static @Nullable Point client() {
        Point point = client;
        return point != null && point.active() ? point : null;
    }

    /** Upstream {@code handleGps}: the point goes inactive once the player is off any vehicle. */
    public static void clientTick(@Nullable Player player) {
        Point point = client;
        if (player != null && player.getVehicle() == null && point != null && point.active()) {
            client = new Point(point.x(), point.y(), point.z(), false);
        }
    }
}
