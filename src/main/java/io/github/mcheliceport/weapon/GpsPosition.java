package io.github.mcheliceport.weapon;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Upstream's {@code GPSPosition}: the GPS point a player entered. The client keeps its own for the
 * HUD marker and sends it to the server, which keeps each player's. GPS missiles and bombs
 * ({@code IsGPSMissile}) fired by the player fly to the server's copy.
 */
public final class GpsPosition {

    /** The world border's farthest reach; points beyond it are refused. */
    private static final double MAX_COORD = 30_000_000.0;
    private static final Map<UUID, Point> SERVER = new ConcurrentHashMap<>();
    private static volatile @Nullable Point client;

    private GpsPosition() {
    }

    public record Point(double x, double y, double z, boolean active) {
    }

    /** Upstream {@code PacketResetGPS.onReceive}. */
    public static void setServer(Player player, Point point) {
        if (Double.isFinite(point.x()) && Double.isFinite(point.y()) && Double.isFinite(point.z())
                && Math.abs(point.x()) <= MAX_COORD && Math.abs(point.z()) <= MAX_COORD
                && Math.abs(point.y()) <= MAX_COORD) {
            SERVER.put(player.getUUID(), point);
        }
    }

    /** The point a GPS weapon fired by {@code shooter} flies to: the player's active point, or null. */
    public static @Nullable Vec3 target(@Nullable Entity shooter) {
        Point point = shooter instanceof Player player ? SERVER.get(player.getUUID()) : null;
        return point != null && point.active() ? new Vec3(point.x(), point.y(), point.z()) : null;
    }

    /** Forgets a player's point when they leave the server. */
    public static void forget(Player player) {
        SERVER.remove(player.getUUID());
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
