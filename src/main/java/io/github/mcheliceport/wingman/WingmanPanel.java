package io.github.mcheliceport.wingman;

import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.net.WingmanPanelActionPayload;
import io.github.mcheliceport.net.WingmanPanelDataPayload;
import io.github.mcheliceport.wingman.config.WingmanConfig;
import io.github.mcheliceport.wingman.wingman.WingmanEntry;
import io.github.mcheliceport.wingman.wingman.WingmanRegistry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * The server half of upstream's wingman panel ({@code PacketWingmanPanelOpen} and
 * {@code PacketWingmanPanelAction}). The leader is the aircraft the player pilots.
 *
 * <p>Upstream acted on whatever aircraft UUID a client sent. The port only lets a player take an
 * aircraft within the panel's 512 blocks that nobody flies, and only order the wingmen of the
 * aircraft they pilot; a new wingman takes the lowest free slot (KNOWN_ISSUES 95).
 */
public final class WingmanPanel {

    public static final double NEARBY_RANGE = 512.0;
    public static final int MAX_NEARBY = 8;

    private WingmanPanel() {
    }

    public static @Nullable AircraftEntity leaderOf(Player player) {
        return player.getVehicle() instanceof AircraftEntity ac && ac.getPilot() == player ? ac : null;
    }

    /** Upstream {@code buildData}: the formation values, the leader's wingmen and up to 8 aircraft nearby. */
    public static WingmanPanelDataPayload buildData(Player player) {
        List<WingmanPanelDataPayload.Aircraft> nearby = new ArrayList<>();
        List<WingmanPanelDataPayload.Wingman> wingmen = new ArrayList<>();
        AircraftEntity leader = leaderOf(player);
        if (leader != null && leader.level() instanceof ServerLevel level) {
            for (Map.Entry<UUID, WingmanEntry> e : WingmanRegistry.snapshotForLeader(leader)) {
                WingmanEntry entry = e.getValue();
                if (wingmen.size() >= WingmanPanelDataPayload.MAX_LIST) {
                    break;
                }
                wingmen.add(new WingmanPanelDataPayload.Wingman(e.getKey(), aircraftName(level.getEntity(e.getKey())),
                        entry.formationSlot, entry.leader != null ? "FOLLOWING" : entry.autoState.name(), entry.attackMode,
                        entry.weaponType != null ? entry.weaponType : ""));
            }
            for (AircraftEntity ac : candidates(leader)) {
                nearby.add(new WingmanPanelDataPayload.Aircraft(ac.getUUID(), aircraftName(ac)));
                if (nearby.size() >= MAX_NEARBY) {
                    break;
                }
            }
        }
        return new WingmanPanelDataPayload(nearby, wingmen, WingmanConfig.formationSideDist,
                WingmanConfig.formationAltOffset, WingmanConfig.formationRearDist, WingmanConfig.maxWingmen,
                WingmanConfig.minAttackAltitude, WingmanConfig.maxAttackAltitude);
    }

    /** The aircraft a leader can take as wingmen, nearest first. */
    public static List<AircraftEntity> candidates(AircraftEntity leader) {
        List<AircraftEntity> list = leader.level().getEntitiesOfClass(AircraftEntity.class,
                leader.getBoundingBox().inflate(NEARBY_RANGE), ac -> canFollow(leader, ac));
        list.sort(Comparator.comparingDouble(leader::distanceToSqr));
        return list;
    }

    public static boolean canFollow(AircraftEntity leader, AircraftEntity ac) {
        return ac != leader && !ac.isRemoved() && !ac.isDestroyed() && !(ac.getPilot() instanceof Player)
                && WingmanRegistry.get(ac.getUUID()) == null
                && leader.distanceToSqr(ac) <= NEARBY_RANGE * NEARBY_RANGE;
    }

    /** Upstream {@code McheliReflect.getAircraftName}: the aircraft's type name. */
    public static String aircraftName(@Nullable Entity e) {
        if (e instanceof AircraftEntity ac && !ac.getAircraftTypeName().isEmpty()) {
            return ac.getAircraftTypeName();
        }
        return e != null ? e.getClass().getSimpleName() : "?";
    }

    /** Upstream {@code PacketWingmanPanelAction#handle}. */
    public static void apply(Player player, WingmanPanelActionPayload p) {
        if (p.action() == WingmanPanelActionPayload.FORMATION) {
            WingmanConfig.setFormation(p.sideDist(), p.altOffset(), p.rearDist(), p.maxWings(), p.minAlt(),
                    p.maxAlt());
            return;
        }
        AircraftEntity leader = leaderOf(player);
        if (leader == null || !(leader.level() instanceof ServerLevel level)) {
            return;
        }
        if (p.action() == WingmanPanelActionPayload.STOP_ALL) {
            WingmanRegistry.removeForLeader(leader);
            return;
        }
        UUID uid = p.uuid().orElse(null);
        if (uid == null) {
            return;
        }
        if (p.action() == WingmanPanelActionPayload.FOLLOW) {
            if (level.getEntity(uid) instanceof AircraftEntity wingman && canFollow(leader, wingman)) {
                if (WingmanRegistry.countForLeader(leader) < WingmanConfig.maxWingmen) {
                    WingmanRegistry.put(uid, new WingmanEntry(leader, freeSlot(leader)));
                }
            }
            return;
        }
        WingmanEntry e = WingmanRegistry.get(uid);
        if (e == null || e.leader != leader) {
            return;
        }
        switch (p.action()) {
            case WingmanPanelActionPayload.STOP -> WingmanRegistry.remove(uid);
            case WingmanPanelActionPayload.AUTO -> {
                e.attackMode = WingmanEntry.ATK_AUTO;
                e.manualTargetId = null;
            }
            case WingmanPanelActionPayload.HOLD -> {
                e.attackMode = WingmanEntry.ATK_NONE;
                e.manualTargetId = null;
            }
            case WingmanPanelActionPayload.WEAPON -> e.weaponType = p.extra().isEmpty() ? null : p.extra();
            default -> {
            }
        }
    }

    /** The lowest formation slot none of the leader's wingmen holds. */
    public static int freeSlot(Entity leader) {
        Set<Integer> used = new HashSet<>();
        for (Map.Entry<UUID, WingmanEntry> e : WingmanRegistry.snapshotForLeader(leader)) {
            used.add(e.getValue().formationSlot);
        }
        int slot = 0;
        while (used.contains(slot)) {
            slot++;
        }
        return slot;
    }
}
