package io.github.mcheliceport.combat;

import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.config.ServerConfig;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * M9 server checks on weapon fire, a port addition. The server already owns cooldown, heat, ammo
 * and turret limits; these checks cover what a modified client could still claim: how often it
 * sends fire packets, where it says it fired from, its aim, and the target of a BVR lock.
 *
 * <p>A failed check drops that shot or lock (a bad fire origin falls back to the server's own
 * position) and adds to the player's violation score, which decays with a one-minute time
 * constant. Over {@code antiCheat.threshold} the server acts once a minute per
 * {@code antiCheat.action}: log (the default), also tell operators, or kick. Thresholds leave room
 * for real lag so honest players stay well below them.
 */
public final class AntiCheat {

    /** What was checked, and how much one failure counts. */
    public enum Check {
        PACKET_RATE(1.0),
        FIRE_ORIGIN(2.0),
        AIM(2.0),
        LOCK_TARGET(2.0);

        public final double weight;

        Check(double weight) {
            this.weight = weight;
        }
    }

    public enum Action {
        LOG,
        NOTIFY_OPS,
        KICK
    }

    /** One-minute decay time constant, in ticks. */
    public static final double DECAY_TICKS = 1200.0;
    /** At most one alert per player per minute. */
    public static final long ALERT_INTERVAL_TICKS = 1200L;
    /** Fire packets a player may send in a burst, and per tick after it. */
    public static final double BURST = 4.0;
    public static final double PER_TICK = 2.0;
    /** Fire-origin slack: blocks, plus ticks of the vehicle's own travel. */
    public static final double ORIGIN_SLACK_BLOCKS = 3.0;
    public static final double ORIGIN_SLACK_TICKS = 2.0;
    /** Ticks of history searched beyond the shooter's lag-compensation rewind. */
    public static final int ORIGIN_EXTRA_TICKS = 4;
    private static final int RECENT = 10;

    private static final Map<UUID, Record> RECORDS = new HashMap<>();

    private AntiCheat() {
    }

    /** One logged failure. */
    public record Entry(long tick, Check check, String detail) {
    }

    /** A player's violation state. */
    public static final class Record {
        double score;
        long scoreTick;
        long lastAlertTick = Long.MIN_VALUE / 2;
        double tokens = BURST;
        long tokenTick = Long.MIN_VALUE;
        final EnumMap<Check, Integer> counts = new EnumMap<>(Check.class);
        final ArrayDeque<Entry> recent = new ArrayDeque<>();

        public double score(long now) {
            return decay(this.score, now - this.scoreTick);
        }

        public Map<Check, Integer> counts() {
            return this.counts;
        }

        public List<Entry> recent() {
            return new ArrayList<>(this.recent);
        }
    }

    /** A score after {@code ticks} of decay. */
    public static double decay(double score, long ticks) {
        return ticks <= 0 ? score : score * Math.exp(-ticks / DECAY_TICKS);
    }

    /**
     * The fire-packet token bucket: refills {@link #PER_TICK} a tick up to {@link #BURST}; true and
     * one token spent when a packet may pass.
     */
    public static boolean takeToken(Record r, long now) {
        if (r.tokenTick != Long.MIN_VALUE && now > r.tokenTick) {
            r.tokens = Math.min(BURST, r.tokens + (now - r.tokenTick) * PER_TICK);
        }
        r.tokenTick = now;
        if (r.tokens < 1.0) {
            return false;
        }
        r.tokens -= 1.0;
        return true;
    }

    /**
     * Whether a claimed fire origin is near where the vehicle was at some tick of the last
     * {@code window} ticks, with slack for the vehicle's own speed (blocks per tick). An empty
     * history (a vehicle that has not ticked yet) accepts any origin near its current position.
     */
    public static boolean originPlausible(PoseHistory history, long now, int window, double speed, double cx,
            double cy, double cz, double x, double y, double z) {
        double slack = ORIGIN_SLACK_BLOCKS + ORIGIN_SLACK_TICKS * speed;
        double slackSq = slack * slack;
        double[] at = new double[3];
        for (int k = 0; k <= window; k++) {
            if (!history.positionAt(now - k, at)) {
                at[0] = cx;
                at[1] = cy;
                at[2] = cz;
            }
            double dx = x - at[0];
            double dy = y - at[1];
            double dz = z - at[2];
            if (dx * dx + dy * dy + dz * dz <= slackSq) {
                return true;
            }
        }
        return false;
    }

    /** Whether an aim is a real direction: finite, pitch within straight up and down. */
    public static boolean aimValid(float yaw, float pitch) {
        return Float.isFinite(yaw) && Float.isFinite(pitch) && Math.abs(pitch) <= 90.0F;
    }

    // ---- server state ----

    public static Record record(ServerPlayer player) {
        return RECORDS.computeIfAbsent(player.getUUID(), k -> new Record());
    }

    public static Record recordOf(UUID id) {
        return RECORDS.get(id);
    }

    /** Spends a fire-packet token, recording a violation when there is none. */
    public static boolean allowFirePacket(ServerPlayer player) {
        if (takeToken(record(player), player.level().getGameTime())) {
            return true;
        }
        violation(player, Check.PACKET_RATE, "more than " + (int) PER_TICK + " fire packets a tick");
        return false;
    }

    /**
     * Whether the shooter's claimed origin is plausible for its vehicle, checked against the
     * lag-compensation history over the shooter's rewind plus a few ticks.
     */
    public static boolean checkOrigin(ServerPlayer player, io.github.mcheliceport.entity.aircraft.AircraftEntity vehicle,
            double x, double y, double z) {
        PoseHistory history = LagCompensation.history(vehicle);
        int window = LagCompensation.rewindTicksFor(player) + ORIGIN_EXTRA_TICKS;
        double speed = vehicle.getDeltaMovement().length();
        if (history == null) {
            history = new PoseHistory();
        }
        if (originPlausible(history, vehicle.level().getGameTime(), window, speed, vehicle.getX(), vehicle.getY(),
                vehicle.getZ(), x, y, z)) {
            return true;
        }
        violation(player, Check.FIRE_ORIGIN, String.format("fired from %.1f blocks off %s",
                new Vec3(x, y, z).distanceTo(vehicle.position()), vehicle.getAircraftTypeName()));
        return false;
    }

    /** Records a failed check and acts when the player's score passes the threshold. */
    public static void violation(ServerPlayer player, Check check, String detail) {
        long now = player.level().getGameTime();
        Record r = record(player);
        r.score = r.score(now) + check.weight;
        r.scoreTick = now;
        r.counts.merge(check, 1, Integer::sum);
        r.recent.addFirst(new Entry(now, check, detail));
        while (r.recent.size() > RECENT) {
            r.recent.removeLast();
        }
        int threshold = ServerConfig.get(ServerConfig.ANTI_CHEAT_THRESHOLD);
        if (threshold <= 0 || r.score < threshold || now - r.lastAlertTick < ALERT_INTERVAL_TICKS) {
            return;
        }
        r.lastAlertTick = now;
        Action action = ServerConfig.get(ServerConfig.ANTI_CHEAT_ACTION);
        String message = String.format("[McHeli anti-cheat] %s scored %.0f (last: %s, %s)",
                player.getGameProfile().name(), r.score, check, detail);
        McheliCePort.LOGGER.warn(message);
        MinecraftServer server = player.level().getServer();
        if (action != Action.LOG && server != null) {
            for (ServerPlayer op : server.getPlayerList().getPlayers()) {
                if (op != player && Commands.LEVEL_GAMEMASTERS.check(op.permissions())) {
                    op.sendSystemMessage(Component.literal(message));
                }
            }
        }
        if (action == Action.KICK && player.connection != null) {
            player.connection.disconnect(Component.literal("Kicked by McHeli anti-cheat: " + check));
        }
    }

    public static void register() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.server.ServerStoppedEvent event) -> clear());
    }

    /** Forgets everyone (server stop). */
    public static void clear() {
        RECORDS.clear();
    }
}
