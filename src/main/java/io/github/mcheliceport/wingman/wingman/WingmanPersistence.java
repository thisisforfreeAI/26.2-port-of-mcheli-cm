package io.github.mcheliceport.wingman.wingman;

import com.mojang.serialization.Codec;
import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.platform.GameEvents;
import io.github.mcheliceport.wingman.handler.MissionChunkLoader;
import io.github.mcheliceport.wingman.mission.AutonomousState;
import io.github.mcheliceport.wingman.mission.MissionNode;
import io.github.mcheliceport.wingman.mission.MissionOrder;
import io.github.mcheliceport.wingman.mission.MissionType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

/**
 * A port addition (KNOWN_ISSUES 95, 98): keeps wingmen, base orders and planner missions across a
 * server restart. Upstream, and the port before this, forgot them when the server stopped.
 *
 * <p>The {@link WingmanRegistry} is written into the overworld's saved data whenever the world
 * saves, with each aircraft's dimension and last known position. When the server starts again:
 * <ul>
 *   <li>an aircraft on an order or planner mission, or parked by {@code /wingman order park}, goes
 *       straight back into the registry, and the chunks around its saved position are loaded so the
 *       mission carries on without a player nearby;</li>
 *   <li>a wingman waits here until both it and its leader are loaded again (usually when the
 *       leader's pilot logs back in) and then rejoins the formation in its old slot.</li>
 * </ul>
 * A wingman whose leader only unloaded (its pilot logged out or flew away) waits here the same way
 * while the server runs, instead of being dropped. A waiting wingman is dropped when a player
 * takes its controls, or when it has been loaded for {@link #LEADER_WAIT_TICKS} without its
 * leader. A restored mission whose aircraft does not turn up within {@link #MISSION_WAIT_TICKS} of
 * its chunks loading is dropped and its chunks released.
 */
public final class WingmanPersistence extends SavedData {

    private static final int VERSION = 1;
    /** How long a loaded wingman waits for its leader to load, in ticks. */
    public static final int LEADER_WAIT_TICKS = 600;
    /** How long a restored mission waits for its aircraft to load, in ticks. */
    public static final int MISSION_WAIT_TICKS = 600;
    /** How often the waiting entries are checked and the last known positions updated, in ticks. */
    private static final int CHECK_INTERVAL = 20;

    public static final Codec<WingmanPersistence> CODEC =
            CompoundTag.CODEC.xmap(WingmanPersistence::fromTag, WingmanPersistence::toTag);
    public static final SavedDataType<WingmanPersistence> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "wingman_orders"), WingmanPersistence::new,
            CODEC, null);

    /** Where an aircraft was last seen. */
    public record LastSeen(ResourceKey<Level> dimension, double x, double y, double z) {
    }

    /** A wingman waiting for its leader. */
    private static final class Waiting {
        final WingmanEntry entry;
        final UUID leaderId;
        /** Ticks the wingman has been loaded without its leader. */
        int loadedTicks;

        Waiting(WingmanEntry entry, UUID leaderId) {
            this.entry = entry;
            this.leaderId = leaderId;
        }
    }

    private static final Map<UUID, Waiting> WAITING = new LinkedHashMap<>();
    private static final Map<UUID, LastSeen> LAST_SEEN = new HashMap<>();
    /** Restored missions whose aircraft has not been found yet, with the ticks waited. */
    private static final Map<UUID, Integer> MISSION_WAIT = new HashMap<>();
    private static @Nullable MinecraftServer server;
    private static @Nullable WingmanPersistence instance;

    /** The data as read from disk, applied once the server has started. */
    private final CompoundTag loaded;

    public WingmanPersistence() {
        this(new CompoundTag());
    }

    private WingmanPersistence(CompoundTag loaded) {
        this.loaded = loaded;
    }

    private static WingmanPersistence fromTag(CompoundTag tag) {
        return new WingmanPersistence(tag.copy());
    }

    public static void register() {
        GameEvents.SERVER_STARTED.add(WingmanPersistence::onServerStarted);
        GameEvents.SERVER_TICK_POST.add(WingmanPersistence::onServerTick);
        GameEvents.SERVER_STOPPED.add(s -> {
            server = null;
            instance = null;
            WAITING.clear();
            LAST_SEEN.clear();
            MISSION_WAIT.clear();
        });
    }

    // ---- start ----

    private static void onServerStarted(MinecraftServer s) {
        server = s;
        WAITING.clear();
        LAST_SEEN.clear();
        MISSION_WAIT.clear();
        WingmanPersistence data = s.overworld().getDataStorage().computeIfAbsent(TYPE);
        instance = data;
        data.restore(s);
        data.setDirty();
    }

    private void restore(MinecraftServer s) {
        int missions = 0;
        int wingmen = 0;
        for (Tag t : this.loaded.getListOrEmpty("Entries")) {
            if (!(t instanceof CompoundTag tag)) {
                continue;
            }
            try {
                UUID id = UUID.fromString(tag.getStringOr("Id", ""));
                WingmanEntry entry = readEntry(tag);
                LastSeen seen = readLastSeen(tag);
                if (seen != null) {
                    LAST_SEEN.put(id, seen);
                }
                String leader = tag.getStringOr("Leader", "");
                if (entry.isAutonomous() || entry.parkingHold) {
                    WingmanRegistry.put(id, entry);
                    if (entry.isAutonomous() && seen != null) {
                        ServerLevel level = s.getLevel(seen.dimension());
                        if (level != null) {
                            MissionChunkLoader.restore(level, id,
                                    new ChunkPos((int) Math.floor(seen.x()) >> 4, (int) Math.floor(seen.z()) >> 4));
                            MISSION_WAIT.put(id, 0);
                        }
                    }
                    missions++;
                } else if (!leader.isEmpty()) {
                    WAITING.put(id, new Waiting(entry, UUID.fromString(leader)));
                    wingmen++;
                }
            } catch (RuntimeException ex) {
                McheliCePort.LOGGER.warn("[Wingman] skipped a saved wingman entry: {}", ex.toString());
            }
        }
        this.loaded.remove("Entries");
        if (missions + wingmen > 0) {
            McheliCePort.LOGGER.info("[Wingman] restored {} mission/parked aircraft and {} wingmen waiting for their leader",
                    missions, wingmen);
        }
    }

    // ---- while running ----

    /**
     * Keeps a wingman whose leader unloaded (its pilot logged out, or it flew out of loaded chunks)
     * until the leader is back. Called instead of dropping the entry.
     */
    public static void waitForLeader(UUID id, WingmanEntry entry, Entity leader) {
        WingmanRegistry.remove(id);
        WAITING.put(id, new Waiting(entry, leader.getUUID()));
        McheliCePort.LOGGER.info("[Wingman] {} waiting for its leader {} to load again", id, leader.getUUID());
    }

    /** Whether the leader only left the loaded world, so its wingmen should wait for it. */
    public static boolean leaderUnloaded(Entity leader) {
        Entity.RemovalReason reason = leader.getRemovalReason();
        return reason == Entity.RemovalReason.UNLOADED_TO_CHUNK || reason == Entity.RemovalReason.UNLOADED_WITH_PLAYER;
    }

    /** An aircraft was destroyed or discarded: forget its orders and its waiting entry. */
    public static void forget(UUID id) {
        WAITING.remove(id);
        MISSION_WAIT.remove(id);
        LAST_SEEN.remove(id);
        WingmanRegistry.remove(id);
    }

    /** Wingmen of this leader that are waiting for it to load (for the panel and tests). */
    public static int waitingCount() {
        return WAITING.size();
    }

    public static boolean isWaiting(UUID id) {
        return WAITING.containsKey(id);
    }

    private static void onServerTick(MinecraftServer s) {
        if (s.getTickCount() % CHECK_INTERVAL != 0) {
            return;
        }
        WingmanPersistence data = instance;
        if (data != null) {
            data.setDirty();
        }
        for (UUID id : WingmanRegistry.snapshot().keySet()) {
            Entity e = find(s, id, LAST_SEEN.get(id));
            if (e != null) {
                LAST_SEEN.put(id, lastSeen(e));
                MISSION_WAIT.remove(id);
            }
        }
        tickMissionWait(s);
        tickWaiting(s);
    }

    private static void tickMissionWait(MinecraftServer s) {
        Iterator<Map.Entry<UUID, Integer>> it = MISSION_WAIT.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> e = it.next();
            WingmanEntry entry = WingmanRegistry.get(e.getKey());
            if (entry == null || !entry.isAutonomous()) {
                it.remove();
                continue;
            }
            int waited = e.getValue() + CHECK_INTERVAL;
            if (waited < MISSION_WAIT_TICKS) {
                e.setValue(waited);
                continue;
            }
            it.remove();
            WingmanRegistry.remove(e.getKey());
            LAST_SEEN.remove(e.getKey());
            McheliCePort.LOGGER.info("[Order] {} restored order dropped: the aircraft was not found", e.getKey());
        }
    }

    private static void tickWaiting(MinecraftServer s) {
        Iterator<Map.Entry<UUID, Waiting>> it = WAITING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Waiting> e = it.next();
            UUID id = e.getKey();
            Waiting w = e.getValue();
            Entity found = find(s, id, LAST_SEEN.get(id));
            if (!(found instanceof AircraftEntity wingman) || wingman.isRemoved()) {
                continue;
            }
            LAST_SEEN.put(id, lastSeen(wingman));
            if (wingman.getPilot() instanceof Player || wingman.isDestroyed()) {
                it.remove();
                McheliCePort.LOGGER.info("[Wingman] {} no longer waits for its leader: {}", id,
                        wingman.isDestroyed() ? "destroyed" : "a player took the controls");
                continue;
            }
            Entity leader = wingman.level() instanceof ServerLevel level ? level.getEntity(w.leaderId) : null;
            if (leader != null && !leader.isRemoved()) {
                it.remove();
                if (WingmanRegistry.get(id) == null) {
                    w.entry.leader = leader;
                    w.entry.formationSlot = freeSlot(leader, w.entry.formationSlot);
                    WingmanRegistry.put(id, w.entry);
                    McheliCePort.LOGGER.info("[Wingman] {} rejoined its leader {} (slot {})", id, w.leaderId,
                            w.entry.formationSlot);
                }
                continue;
            }
            w.loadedTicks += CHECK_INTERVAL;
            if (w.loadedTicks >= LEADER_WAIT_TICKS) {
                it.remove();
                McheliCePort.LOGGER.info("[Wingman] {} unregistered - leader {} did not come back", id, w.leaderId);
            }
        }
    }

    /** The saved slot if no other wingman of the leader holds it, else the lowest free one. */
    private static int freeSlot(Entity leader, int wanted) {
        List<Integer> taken = new ArrayList<>();
        for (Map.Entry<UUID, WingmanEntry> e : WingmanRegistry.snapshotForLeader(leader)) {
            taken.add(e.getValue().formationSlot);
        }
        if (!taken.contains(wanted)) {
            return wanted;
        }
        int slot = 0;
        while (taken.contains(slot)) {
            slot++;
        }
        return slot;
    }

    private static @Nullable Entity find(MinecraftServer s, UUID id, @Nullable LastSeen hint) {
        if (hint != null) {
            ServerLevel level = s.getLevel(hint.dimension());
            Entity e = level != null ? level.getEntity(id) : null;
            if (e != null) {
                return e;
            }
        }
        for (ServerLevel level : s.getAllLevels()) {
            Entity e = level.getEntity(id);
            if (e != null) {
                return e;
            }
        }
        return null;
    }

    private static LastSeen lastSeen(Entity e) {
        return new LastSeen(e.level().dimension(), e.getX(), e.getY(), e.getZ());
    }

    // ---- saving ----

    private CompoundTag toTag() {
        CompoundTag root = this.loaded.copy();
        root.putInt("Version", VERSION);
        ListTag list = new ListTag();
        MinecraftServer s = server;
        for (Map.Entry<UUID, WingmanEntry> e : WingmanRegistry.snapshot().entrySet()) {
            WingmanEntry entry = e.getValue();
            UUID leaderId = entry.leader != null ? entry.leader.getUUID() : null;
            if (!entry.isAutonomous() && !entry.parkingHold && leaderId == null) {
                continue;
            }
            if (entry.leader != null && entry.leader.isRemoved() && !leaderUnloaded(entry.leader)
                    && !entry.isAutonomous() && !entry.parkingHold) {
                continue;
            }
            list.add(writeEntry(s, e.getKey(), entry, leaderId));
        }
        for (Map.Entry<UUID, Waiting> e : WAITING.entrySet()) {
            if (WingmanRegistry.get(e.getKey()) == null) {
                list.add(writeEntry(s, e.getKey(), e.getValue().entry, e.getValue().leaderId));
            }
        }
        root.put("Entries", list);
        return root;
    }

    private static CompoundTag writeEntry(@Nullable MinecraftServer s, UUID id, WingmanEntry entry,
            @Nullable UUID leaderId) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", id.toString());
        if (leaderId != null && !entry.isAutonomous()) {
            tag.putString("Leader", leaderId.toString());
        }
        LastSeen seen = LAST_SEEN.get(id);
        Entity e = s != null ? find(s, id, seen) : null;
        if (e != null && !e.isRemoved()) {
            seen = lastSeen(e);
        }
        if (seen != null) {
            tag.putString("Dimension", seen.dimension().identifier().toString());
            tag.putDouble("X", seen.x());
            tag.putDouble("Y", seen.y());
            tag.putDouble("Z", seen.z());
        }
        tag.putString("State", entry.state.name());
        tag.putInt("Slot", entry.formationSlot);
        tag.putInt("AttackMode", entry.attackMode);
        if (entry.manualTargetId != null) {
            tag.putString("ManualTarget", entry.manualTargetId.toString());
        }
        tag.putInt("WeaponSeat", entry.weaponSeat);
        if (entry.weaponType != null) {
            tag.putString("WeaponType", entry.weaponType);
        }
        tag.putDouble("OrbitAngle", entry.orbitAngle);
        tag.putString("AutoState", entry.autoState.name());
        tag.putInt("NodeTimer", entry.missionNodeTimer);
        tag.putDouble("AutoTargetX", entry.autoTargetX);
        tag.putDouble("AutoTargetY", entry.autoTargetY);
        tag.putDouble("AutoTargetZ", entry.autoTargetZ);
        if (entry.mission != null) {
            ListTag nodes = new ListTag();
            for (MissionNode node : entry.mission) {
                nodes.add(StringTag.valueOf(node.serialize()));
            }
            tag.put("Mission", nodes);
        }
        tag.putInt("MissionIndex", entry.missionIndex);
        if (entry.order != null) {
            tag.put("Order", writeOrder(entry.order));
        }
        tag.putInt("OrderTimer", entry.orderTimer);
        tag.putDouble("MissionOrbitAngle", entry.missionOrbitAngle);
        tag.putInt("StrikePasses", entry.strikePassesRemaining);
        tag.putString("Parking", entry.assignedParkingId);
        tag.putBoolean("ParkingHold", entry.parkingHold);
        tag.putString("Team", entry.teamName);
        tag.putString("DepartureRoute", entry.departureRouteId);
        ListTag taxi = new ListTag();
        for (String wp : entry.taxiWpQueue) {
            taxi.add(StringTag.valueOf(wp));
        }
        tag.put("TaxiQueue", taxi);
        tag.putInt("TaxiIndex", entry.taxiWpIndex);
        tag.putInt("ReconMobs", entry.reconMobCount);
        tag.putString("RtbReason", entry.rtbReason);
        tag.putBoolean("VtolHover", entry.vtolHoverMode);
        tag.putInt("VtolNozzle", entry.vtolNozzleMode);
        tag.putDouble("LandingRoll", entry.landingRollSpeed);
        if (!Float.isNaN(entry.parkingHeadingYaw)) {
            tag.putFloat("ParkingYaw", entry.parkingHeadingYaw);
        }
        tag.putDouble("GroundRollY", entry.groundRollY);
        tag.putBoolean("ClimbHold", entry.climbHold);
        tag.putDouble("ClimbHoldX", entry.climbHoldX);
        tag.putDouble("ClimbHoldZ", entry.climbHoldZ);
        return tag;
    }

    private static CompoundTag writeOrder(MissionOrder order) {
        CompoundTag tag = new CompoundTag();
        ListTag types = new ListTag();
        for (MissionType type : order.missionTypes) {
            types.add(StringTag.valueOf(type.name()));
        }
        tag.put("Types", types);
        ListTag weapons = new ListTag();
        for (String w : order.weapons) {
            weapons.add(StringTag.valueOf(w));
        }
        tag.put("Weapons", weapons);
        tag.putDouble("TargetX", order.targetX);
        tag.putDouble("TargetZ", order.targetZ);
        tag.putInt("TimeLimit", order.timeLimitSeconds);
        tag.putString("Base", order.baseId);
        tag.putDouble("OrbitRadius", order.orbitRadius);
        tag.putDouble("CruiseAlt", order.cruiseAlt);
        tag.putInt("StrikePasses", order.strikePasses);
        tag.putString("FerryDest", order.ferryDestBase);
        tag.putBoolean("OrbitAttack", order.orbitAttack);
        tag.putBoolean("UseVstol", order.useVstol);
        tag.putString("ArrivalRoute", order.arrivalRouteId);
        return tag;
    }

    // ---- loading ----

    private static @Nullable LastSeen readLastSeen(CompoundTag tag) {
        String dim = tag.getStringOr("Dimension", "");
        Identifier id = dim.isEmpty() ? null : Identifier.tryParse(dim);
        if (id == null) {
            return null;
        }
        return new LastSeen(ResourceKey.create(Registries.DIMENSION, id), tag.getDoubleOr("X", 0.0),
                tag.getDoubleOr("Y", 0.0), tag.getDoubleOr("Z", 0.0));
    }

    static WingmanEntry readEntry(CompoundTag tag) {
        WingmanEntry entry = new WingmanEntry(null, tag.getIntOr("Slot", 0));
        entry.state = enumOr(WingmanState.class, tag.getStringOr("State", ""), WingmanState.FOLLOWING);
        entry.attackMode = tag.getIntOr("AttackMode", WingmanEntry.ATK_NONE);
        entry.manualTargetId = uuidOrNull(tag.getStringOr("ManualTarget", ""));
        entry.weaponSeat = tag.getIntOr("WeaponSeat", 0);
        String weaponType = tag.getStringOr("WeaponType", "");
        entry.weaponType = weaponType.isEmpty() ? null : weaponType;
        entry.orbitAngle = tag.getDoubleOr("OrbitAngle", 0.0);
        entry.autoState = enumOr(AutonomousState.class, tag.getStringOr("AutoState", ""), AutonomousState.NONE);
        entry.missionNodeTimer = tag.getIntOr("NodeTimer", 0);
        entry.autoTargetX = tag.getDoubleOr("AutoTargetX", 0.0);
        entry.autoTargetY = tag.getDoubleOr("AutoTargetY", 0.0);
        entry.autoTargetZ = tag.getDoubleOr("AutoTargetZ", 0.0);
        if (tag.contains("Mission")) {
            List<MissionNode> nodes = new ArrayList<>();
            for (Tag t : tag.getListOrEmpty("Mission")) {
                nodes.add(MissionNode.parse(t.asString().orElse("")));
            }
            entry.mission = List.copyOf(nodes);
        }
        entry.missionIndex = tag.getIntOr("MissionIndex", 0);
        CompoundTag order = tag.getCompoundOrEmpty("Order");
        if (!order.isEmpty()) {
            entry.order = readOrder(order);
        }
        entry.orderTimer = tag.getIntOr("OrderTimer", 0);
        entry.missionOrbitAngle = tag.getDoubleOr("MissionOrbitAngle", 0.0);
        entry.strikePassesRemaining = tag.getIntOr("StrikePasses", 0);
        entry.assignedParkingId = tag.getStringOr("Parking", "");
        entry.parkingHold = tag.getBooleanOr("ParkingHold", false);
        entry.teamName = tag.getStringOr("Team", "");
        entry.departureRouteId = tag.getStringOr("DepartureRoute", "");
        for (Tag t : tag.getListOrEmpty("TaxiQueue")) {
            entry.taxiWpQueue.add(t.asString().orElse(""));
        }
        entry.taxiWpIndex = tag.getIntOr("TaxiIndex", 0);
        entry.reconMobCount = tag.getIntOr("ReconMobs", 0);
        entry.rtbReason = tag.getStringOr("RtbReason", "");
        entry.vtolHoverMode = tag.getBooleanOr("VtolHover", false);
        entry.vtolNozzleMode = tag.getIntOr("VtolNozzle", 0);
        // The hover switch is sent again, since the aircraft's own state may differ after loading.
        entry.vtolOnSent = false;
        entry.landingRollSpeed = tag.getDoubleOr("LandingRoll", 0.0);
        entry.parkingHeadingYaw = tag.contains("ParkingYaw") ? tag.getFloatOr("ParkingYaw", 0.0F) : Float.NaN;
        entry.groundRollY = tag.getDoubleOr("GroundRollY", 0.0);
        entry.climbHold = tag.getBooleanOr("ClimbHold", false);
        entry.climbHoldX = tag.getDoubleOr("ClimbHoldX", 0.0);
        entry.climbHoldZ = tag.getDoubleOr("ClimbHoldZ", 0.0);
        return entry;
    }

    private static MissionOrder readOrder(CompoundTag tag) {
        MissionOrder order = new MissionOrder();
        for (Tag t : tag.getListOrEmpty("Types")) {
            MissionType type = enumOr(MissionType.class, t.asString().orElse(""), null);
            if (type != null) {
                order.missionTypes.add(type);
            }
        }
        for (Tag t : tag.getListOrEmpty("Weapons")) {
            order.weapons.add(t.asString().orElse(""));
        }
        order.targetX = tag.getDoubleOr("TargetX", 0.0);
        order.targetZ = tag.getDoubleOr("TargetZ", 0.0);
        order.timeLimitSeconds = tag.getIntOr("TimeLimit", 600);
        order.baseId = tag.getStringOr("Base", "");
        order.orbitRadius = tag.getDoubleOr("OrbitRadius", 300.0);
        order.cruiseAlt = tag.getDoubleOr("CruiseAlt", 80.0);
        order.strikePasses = tag.getIntOr("StrikePasses", 2);
        order.ferryDestBase = tag.getStringOr("FerryDest", "");
        order.orbitAttack = tag.getBooleanOr("OrbitAttack", false);
        order.useVstol = tag.getBooleanOr("UseVstol", false);
        order.arrivalRouteId = tag.getStringOr("ArrivalRoute", "");
        return order;
    }

    private static <E extends Enum<E>> @Nullable E enumOr(Class<E> type, String name, @Nullable E fallback) {
        try {
            return name.isEmpty() ? fallback : Enum.valueOf(type, name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private static @Nullable UUID uuidOrNull(String s) {
        try {
            return s.isEmpty() ? null : UUID.fromString(s);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    // ---- tests ----

    /** Writes the registry and the waiting wingmen as they would be saved (for game tests). */
    public static CompoundTag snapshotForTest(MinecraftServer s) {
        if (server == null) {
            server = s;
        }
        return new WingmanPersistence().toTag();
    }

    /**
     * Forgets the saved aircraft and restores them from the tag, as at server start (for game
     * tests); other entries are left alone.
     */
    public static void restoreForTest(MinecraftServer s, CompoundTag tag) {
        for (Tag t : tag.getListOrEmpty("Entries")) {
            if (t instanceof CompoundTag entry) {
                UUID id = uuidOrNull(entry.getStringOr("Id", ""));
                if (id != null) {
                    WingmanRegistry.remove(id);
                    WAITING.remove(id);
                    MISSION_WAIT.remove(id);
                }
            }
        }
        new WingmanPersistence(tag.copy()).restore(s);
    }
}
