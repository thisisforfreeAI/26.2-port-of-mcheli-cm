package io.github.mcheliceport.entity;

import io.github.mcheliceport.config.ServerConfig;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.gunner.GunnerEntity;
import io.github.mcheliceport.entity.seat.SeatEntity;
import io.github.mcheliceport.wingman.wingman.WingmanEntry;
import io.github.mcheliceport.wingman.wingman.WingmanRegistry;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;
import org.jspecify.annotations.Nullable;

/**
 * Battles between NPCs on different teams, a port addition (KNOWN_ISSUES 101). Upstream's gunners
 * shot only monsters or players and its wingmen only mobs, target drones and gunners, so NPCs never
 * fought each other. With the server config's {@code npcBattles.enabled}, an NPC fights for a team:
 * a gunner for its own, an unmanned aircraft for the team of the player it flies for (its leader's
 * pilot, or whoever dispatched it), or else for its gunner crew's. NPCs of different teams then
 * attack each other; an NPC with no team keeps upstream's targets.
 */
public final class NpcBattles {

    private NpcBattles() {
    }

    public static boolean enabled() {
        return !ServerConfig.SPEC.isLoaded() || ServerConfig.NPC_BATTLES.get();
    }

    /** The name of a player's team, or "" for none. */
    public static String teamName(@Nullable Entity entity) {
        PlayerTeam team = entity != null ? entity.getTeam() : null;
        return team != null ? team.getName() : "";
    }

    /**
     * The team an NPC fights for, or "" for none: a gunner's team; for an aircraft no player is
     * aboard, the team of the player it flies for, else that of the first gunner aboard. Players
     * and what they ride are not NPCs.
     */
    public static String teamOf(@Nullable Entity e) {
        if (e instanceof GunnerEntity gunner) {
            return gunner.getTeamName();
        }
        if (!(e instanceof AircraftEntity ac) || ac.isRidePlayer()) {
            return "";
        }
        WingmanEntry entry = WingmanRegistry.get(ac.getUUID());
        if (entry != null) {
            if (entry.leader != null) {
                Entity pilot = entry.leader instanceof AircraftEntity la ? la.getPilot() : entry.leader.getFirstPassenger();
                if (pilot instanceof Player player) {
                    return teamName(player);
                }
            }
            if (!entry.teamName.isEmpty()) {
                return entry.teamName;
            }
        }
        if (ac.getPilot() instanceof GunnerEntity gunner && !gunner.getTeamName().isEmpty()) {
            return gunner.getTeamName();
        }
        for (SeatEntity seat : ac.getSeats()) {
            if (seat != null && seat.getRider() instanceof GunnerEntity gunner && !gunner.getTeamName().isEmpty()) {
                return gunner.getTeamName();
            }
        }
        return "";
    }

    /** Both have a team and the teams differ. */
    public static boolean areEnemies(String a, String b) {
        return !a.isEmpty() && !b.isEmpty() && !a.equals(b);
    }

    /** Whether, with battles on, an NPC of team {@code team} attacks the entity. */
    public static boolean isEnemyNpc(String team, Entity e) {
        if (!enabled() || team.isEmpty() || e.isRemoved()) {
            return false;
        }
        if (e instanceof GunnerEntity gunner) {
            return gunner.isAlive() && areEnemies(team, gunner.getTeamName());
        }
        if (e instanceof AircraftEntity ac) {
            return !ac.isDestroyed() && areEnemies(team, teamOf(ac));
        }
        return false;
    }
}
