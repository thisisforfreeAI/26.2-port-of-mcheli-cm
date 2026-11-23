package io.github.mcheliceport.radar;

import io.github.mcheliceport.config.ServerConfig;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.entity.plane.PlaneEntity;
import io.github.mcheliceport.entity.seat.SeatEntity;
import io.github.mcheliceport.entity.weapon.BaseBulletEntity;
import io.github.mcheliceport.item.LightWeaponItem;
import io.github.mcheliceport.net.ModNetwork;
import io.github.mcheliceport.net.RwrThreatPayload;
import io.github.mcheliceport.platform.GameEvents;
import io.github.mcheliceport.weapon.WeaponBase;
import io.github.mcheliceport.weapon.WeaponSet;
import io.github.mcheliceport.wingman.wingman.WingmanRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * The server side of the radar warning receiver: upstream's {@code MCH_RWRThreatManager}, which
 * upstream wrote but never ran. Four times a second it works out, for every vehicle a player rides,
 * which radars sweep it, which seekers lock it and which missiles home on it, and sends each rider
 * the table.
 *
 * <ul>
 *   <li><b>Search:</b> a vehicle with a {@code Radar} section and its radar switched on (upstream),
 *       or, with {@code rwr.autoFit}, a crewed vehicle with {@code EntityRadar}, whose scan cone
 *       holds the receiver. Chaff and a running jammer hide the receiver from search radars, as
 *       upstream; stealth shortens the range.</li>
 *   <li><b>Lock:</b> seekers lock on the shooter's client, which reports the entity it is locking
 *       ({@link io.github.mcheliceport.net.RwrLockPayload}, upstream {@code PacketNotifyLock}); a BVR
 *       lock counts too, as upstream.</li>
 *   <li><b>Launch:</b> any missile in flight whose target is the receiver or someone aboard it.</li>
 * </ul>
 *
 * <p>Riders of a vehicle without a receiver are sent only locks and launches, for the lock tone
 * upstream's {@code PacketNotifyLock} played; the scope and search contacts need a receiver.
 */
public final class RwrThreatManager {

    private static final int INTERVAL = 4;
    private static final int LOCK_REPORT_TTL = 30;
    private static final int SEARCH_TTL = 40;
    private static final int LOCK_TTL = 10;
    private static final int LAUNCH_TTL = 8;
    /** An empty table is re-sent this often, so the client keeps showing the scope. */
    private static final int HEARTBEAT = 20;
    private static final int MAX_THREATS = 12;
    private static final double MIN_SCAN_DISTANCE = 15.0;
    /** Range of an {@code EntityRadar} vehicle's search radar under {@code rwr.autoFit}. */
    private static final double AUTO_RADAR_RANGE = 1000.0;

    private static final Map<UUID, LockReport> LOCKS = new HashMap<>();
    private static final Map<ThreatKey, Active> ACTIVE = new LinkedHashMap<>();
    private static final Set<UUID> SENT_NON_EMPTY = new HashSet<>();
    private static long tick;

    private RwrThreatManager() {
    }

    public static void register() {
        GameEvents.SERVER_TICK_POST.add(RwrThreatManager::onServerTick);
        GameEvents.SERVER_STOPPED.add(server -> clear());
        GameEvents.PLAYER_LOGGED_OUT.add(player -> {
            LOCKS.remove(player.getUUID());
            SENT_NON_EMPTY.remove(player.getUUID());
        });
    }

    public static void clear() {
        LOCKS.clear();
        ACTIVE.clear();
        SENT_NON_EMPTY.clear();
    }

    /** The threats now held against the vehicle {@code receiverId}, for the game tests. */
    public static List<RwrThreat> threatsFor(int receiverId) {
        List<RwrThreat> out = new ArrayList<>();
        for (Map.Entry<ThreatKey, Active> e : ACTIVE.entrySet()) {
            if (e.getKey().receiverId == receiverId && e.getValue().expireTick >= tick) {
                out.add(e.getValue().threat);
            }
        }
        return out;
    }

    private static boolean enabled() {
        return ServerConfig.get(ServerConfig.RWR_ENABLED);
    }

    /**
     * A client's report of the entity its seeker locks, 0 for none. The sender must be using a
     * seeking weapon (an aircraft weapon with a seeker, or a hand-held launcher), and the target
     * must be alive, in the sender's world, within the radar feed's range and not their own vehicle.
     */
    public static void reportLock(ServerPlayer player, int targetId) {
        if (targetId <= 0 || !enabled()) {
            LOCKS.remove(player.getUUID());
            return;
        }
        if (!hasSeeker(player)) {
            return;
        }
        Entity target = player.level().getEntity(targetId);
        if (target == null || !target.isAlive() || target == player || target.getRootVehicle() == player.getRootVehicle()) {
            return;
        }
        if (target.distanceToSqr(player) > EntityInfoFeed.RANGE * EntityInfoFeed.RANGE) {
            return;
        }
        LOCKS.put(player.getUUID(), new LockReport(targetId, tick + LOCK_REPORT_TTL));
    }

    private static boolean hasSeeker(ServerPlayer player) {
        AircraftEntity ac = ModNetwork.riddenAircraft(player);
        if (ac != null) {
            WeaponSet ws = ac.getCurrentWeapon(player);
            WeaponBase weapon = ws != null ? ws.getCurrentWeapon() : null;
            return weapon != null && weapon.getGuidanceSystem() != null;
        }
        return LightWeaponItem.isHeld(player);
    }

    private static void onServerTick(MinecraftServer server) {
        if (++tick % INTERVAL != 0) {
            return;
        }
        if (!enabled()) {
            if (!ACTIVE.isEmpty() || !LOCKS.isEmpty()) {
                clear();
            }
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            if (!level.players().isEmpty()) {
                processLevel(server, level);
            }
        }
        LOCKS.values().removeIf(r -> r.expireTick < tick);
        ACTIVE.values().removeIf(a -> a.expireTick < tick);
    }

    /** Runs one sweep of {@code level}; public for the game tests. */
    public static void processLevel(MinecraftServer server, ServerLevel level) {
        Map<AircraftEntity, List<ServerPlayer>> receivers = new LinkedHashMap<>();
        for (ServerPlayer player : level.players()) {
            AircraftEntity ac = ModNetwork.riddenAircraft(player);
            if (ac != null && !ac.isRemoved()) {
                receivers.computeIfAbsent(ac, k -> new ArrayList<>()).add(player);
            }
        }
        if (receivers.isEmpty()) {
            return;
        }
        List<AircraftEntity> fitted = new ArrayList<>();
        for (AircraftEntity ac : receivers.keySet()) {
            if (hasReceiver(ac)) {
                fitted.add(ac);
            }
        }
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof BaseBulletEntity bullet) {
                processMissile(bullet, receivers.keySet());
            } else if (!fitted.isEmpty() && entity instanceof AircraftEntity emitter) {
                processSearch(emitter, fitted);
            }
        }
        for (Map.Entry<UUID, LockReport> e : LOCKS.entrySet()) {
            ServerPlayer shooter = server.getPlayerList().getPlayer(e.getKey());
            if (shooter != null && shooter.level() == level && e.getValue().expireTick >= tick) {
                processLock(shooter, level.getEntity(e.getValue().targetId), receivers.keySet());
            }
        }
        sync(receivers, fitted);
    }

    private static void processMissile(BaseBulletEntity bullet, Set<AircraftEntity> receivers) {
        if (bullet.isRemoved()) {
            return;
        }
        AircraftEntity receiver = receiverOf(bullet.targetEntity);
        if (receiver == null || !receivers.contains(receiver)) {
            return;
        }
        String name = bullet.getInfo() != null ? bullet.getInfo().nameOnRWR : "MSL";
        touch(receiver, bullet, RwrThreat.MISSILE, RwrThreat.LAUNCH, name, LAUNCH_TTL);
    }

    private static void processLock(ServerPlayer shooter, @Nullable Entity target, Set<AircraftEntity> receivers) {
        AircraftEntity receiver = receiverOf(target);
        if (receiver == null || !receivers.contains(receiver)) {
            return;
        }
        AircraftEntity emitterAc = ModNetwork.riddenAircraft(shooter);
        if (emitterAc == receiver) {
            return;
        }
        if (emitterAc != null) {
            touch(receiver, emitterAc, kindOf(emitterAc), RwrThreat.LOCK, nameOf(emitterAc), LOCK_TTL);
        } else {
            touch(receiver, shooter, RwrThreat.INFANTRY, RwrThreat.LOCK, "IR", LOCK_TTL);
        }
    }

    private static void processSearch(AircraftEntity emitter, List<AircraftEntity> fitted) {
        AircraftInfo info = emitter.getAcInfo();
        if (info == null || emitter.isDestroyed() || emitter.getRadarBlankTicks() > 0) {
            return;
        }
        double range;
        float scanAz;
        float scanEl;
        if (info.enableRadar && emitter.isRadarEnabledRuntime()) {
            range = info.radarMaxTargetRange > 0.0F ? info.radarMaxTargetRange : 4096.0;
            scanAz = Mth.clamp(info.radarScanAzimuthDeg, 0.0F, 360.0F);
            scanEl = Mth.clamp(info.radarScanElevationDeg, 0.0F, 180.0F);
        } else if (ServerConfig.get(ServerConfig.RWR_AUTO_FIT) && info.isEnableEntityRadar && isCrewed(emitter)) {
            boolean air = emitter instanceof PlaneEntity || emitter instanceof HeliEntity;
            range = AUTO_RADAR_RANGE;
            scanAz = air ? 120.0F : 360.0F;
            scanEl = air ? 60.0F : 180.0F;
        } else {
            return;
        }
        String name = nameOf(emitter);
        byte kind = kindOf(emitter);
        for (AircraftEntity target : fitted) {
            if (target == emitter || target.isDestroyed() || target.isChaffUsing() || target.isECMJammerUsing()) {
                continue;
            }
            double effectiveRange = range * (1.0 - Mth.clamp(target.getStealth(), 0.0F, 1.0F));
            if (insideScanCone(emitter, target, effectiveRange, scanAz, scanEl)) {
                touch(target, emitter, kind, RwrThreat.SEARCH, name, SEARCH_TTL);
            }
        }
    }

    private static void touch(AircraftEntity receiver, Entity emitter, byte kind, byte mode, String name, int ttl) {
        ThreatKey key = new ThreatKey(receiver.getId(), emitter.getId(), mode);
        ACTIVE.put(key, new Active(new RwrThreat(emitter.getId(), kind, mode, name, emitter.getX(), emitter.getY(),
                emitter.getZ()), tick + ttl));
    }

    private static void sync(Map<AircraftEntity, List<ServerPlayer>> receivers, List<AircraftEntity> fitted) {
        Map<Integer, List<RwrThreat>> byReceiver = new HashMap<>();
        for (Map.Entry<ThreatKey, Active> e : ACTIVE.entrySet()) {
            if (e.getValue().expireTick >= tick) {
                byReceiver.computeIfAbsent(e.getKey().receiverId, k -> new ArrayList<>()).add(e.getValue().threat);
            }
        }
        for (Map.Entry<AircraftEntity, List<ServerPlayer>> e : receivers.entrySet()) {
            AircraftEntity ac = e.getKey();
            boolean hasReceiver = fitted.contains(ac);
            List<RwrThreat> threats = new ArrayList<>();
            for (RwrThreat t : byReceiver.getOrDefault(ac.getId(), List.of())) {
                if (hasReceiver || t.mode() != RwrThreat.SEARCH) {
                    threats.add(t);
                }
            }
            // Launches first, then locks, then the nearest search radars.
            threats.sort((a, b) -> {
                if (a.mode() != b.mode()) {
                    return Integer.compare(b.mode(), a.mode());
                }
                return Double.compare(ac.distanceToSqr(a.x(), a.y(), a.z()), ac.distanceToSqr(b.x(), b.y(), b.z()));
            });
            if (threats.size() > MAX_THREATS) {
                threats = new ArrayList<>(threats.subList(0, MAX_THREATS));
            }
            for (ServerPlayer player : e.getValue()) {
                boolean send;
                if (!threats.isEmpty()) {
                    SENT_NON_EMPTY.add(player.getUUID());
                    send = true;
                } else {
                    // One empty table clears the client's; a receiver's is re-sent as a heartbeat.
                    send = SENT_NON_EMPTY.remove(player.getUUID()) || hasReceiver && tick % HEARTBEAT == 0;
                }
                if (send) {
                    ModNetwork.sendToPlayer(player, new RwrThreatPayload(ac.getId(), hasReceiver, threats));
                }
            }
        }
    }

    /**
     * Whether {@code ac} carries a warning receiver: an {@code RWR} section, or with
     * {@code rwr.autoFit} flares, chaff or an entity radar, which mark a combat vehicle in the
     * bundled content (none of which has an {@code RWR} section).
     */
    public static boolean hasReceiver(AircraftEntity ac) {
        AircraftInfo info = ac.getAcInfo();
        if (info == null) {
            return false;
        }
        if (info.hasRWR) {
            return true;
        }
        return ServerConfig.get(ServerConfig.RWR_AUTO_FIT)
                && (ac.haveFlare() || info.haveChaff() || info.isEnableEntityRadar);
    }

    private static boolean isCrewed(AircraftEntity ac) {
        if (ac.getPilot() != null || WingmanRegistry.get(ac.getUUID()) != null) {
            return true;
        }
        for (int i = 1; i <= ac.getSeatNum(); i++) {
            if (ac.getEntityBySeatId(i) != null) {
                return true;
            }
        }
        return false;
    }

    /** The vehicle an entity is, or rides; null for anything else. */
    public static @Nullable AircraftEntity receiverOf(@Nullable Entity target) {
        for (int depth = 0; target != null && depth < 4; depth++) {
            if (target instanceof AircraftEntity ac) {
                return ac;
            }
            if (target instanceof SeatEntity seat) {
                return seat.getParent();
            }
            if (target instanceof Player player) {
                return ModNetwork.riddenAircraft(player);
            }
            target = target.getVehicle();
        }
        return null;
    }

    private static byte kindOf(AircraftEntity ac) {
        return ac instanceof PlaneEntity || ac instanceof HeliEntity ? RwrThreat.AIRCRAFT : RwrThreat.GROUND;
    }

    /** The emitter's symbol: its {@code NameOnRWR}, else the number in its name. */
    public static String nameOf(AircraftEntity ac) {
        AircraftInfo info = ac.getAcInfo();
        if (info == null) {
            return "?";
        }
        String n = info.nameOnRWR;
        return n != null && !n.isBlank() && !"?".equals(n) ? n : RwrThreat.symbolFor(info.name);
    }

    /** Upstream {@code isTargetInsideScanCone}. */
    static boolean insideScanCone(Entity emitter, Entity target, double maxRange, float scanAzDeg, float scanElDeg) {
        double dx = target.getX() - emitter.getX();
        double dy = target.getY() - emitter.getY();
        double dz = target.getZ() - emitter.getZ();
        double distSq = dx * dx + dy * dy + dz * dz;
        if (distSq < MIN_SCAN_DISTANCE * MIN_SCAN_DISTANCE || distSq > maxRange * maxRange) {
            return false;
        }
        double horiz = Math.sqrt(dx * dx + dz * dz);
        if (scanAzDeg < 360.0F) {
            if (horiz < 1.0E-6) {
                return false;
            }
            double yaw = Math.toRadians(emitter.getYRot());
            double fx = -Math.sin(yaw);
            double fz = Math.cos(yaw);
            double dot = Mth.clamp((fx * dx + fz * dz) / horiz, -1.0, 1.0);
            if (Math.toDegrees(Math.acos(dot)) > scanAzDeg * 0.5) {
                return false;
            }
        }
        double targetPitch = Math.toDegrees(Math.atan2(dy, horiz));
        double forwardPitch = scanAzDeg < 360.0F ? -emitter.getXRot() : 0.0;
        return Math.abs(targetPitch - forwardPitch) <= scanElDeg * 0.5;
    }

    private record LockReport(int targetId, long expireTick) {
    }

    private record ThreatKey(int receiverId, int emitterId, byte mode) {
    }

    private record Active(RwrThreat threat, long expireTick) {
    }
}
