package io.github.mcheliceport.multiplay;

import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.net.ModNetwork;
import io.github.mcheliceport.net.MultiplayActionPayload;
import io.github.mcheliceport.net.MultiplayStatePayload;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/**
 * The server side of the multiplay manager's buttons: upstream {@code MCH_Multiplay.shuffleTeam}
 * and {@code jumpSpawnPoint}, {@code MultiplayerHandler.destoryAllAircraft} and the SETPVP case of
 * {@code PacketHandleCommand}.
 *
 * <p>Upstream checked the permission of the vanilla command each action stands for (scoreboard,
 * teleport, summon); all of them are the game master level here, as they were upstream.
 */
public final class MultiplayManager {

    /** The PVP state the server last sent this client; upstream's client {@code enablePVP} starts on. */
    private static volatile boolean clientPvp = true;

    private MultiplayManager() {
    }

    public static boolean clientPvp() {
        return clientPvp;
    }

    public static void setClientPvp(boolean pvp) {
        clientPvp = pvp;
    }

    public static boolean canManage(ServerPlayer player) {
        return player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    public static void handle(ServerPlayer player, MultiplayActionPayload.Action action) {
        MinecraftServer server = player.level().getServer();
        switch (action) {
            case STATE -> ModNetwork.sendToPlayer(player, new MultiplayStatePayload(isPvp(server)));
            case SHUFFLE_TEAM -> {
                if (canManage(player)) {
                    List<String> names = new ArrayList<>();
                    server.getPlayerList().getPlayers().forEach(p -> names.add(p.getScoreboardName()));
                    shuffleTeam(server.getScoreboard(), names);
                }
            }
            case JUMP_SPAWNPOINT -> {
                if (canManage(player)) {
                    jumpSpawnPoint(player, server.getPlayerList().getPlayers());
                }
            }
            case SET_PVP -> {
                if (canManage(player)) {
                    server.getGameRules().set(GameRules.PVP, !isPvp(server), server);
                    ModNetwork.sendToAllPlayers(server, new MultiplayStatePayload(isPvp(server)));
                }
            }
            case DESTROY_AIRCRAFT -> {
                if (canManage(player)) {
                    destroyAllAircraft(player.level());
                }
            }
        }
    }

    public static boolean isPvp(MinecraftServer server) {
        return server.getGameRules().get(GameRules.PVP);
    }

    /**
     * Upstream {@code shuffleTeam}: the players, shuffled, join the teams, shuffled, in turn. With no
     * team nothing changes.
     */
    public static void shuffleTeam(Scoreboard scoreboard, List<String> playerNames) {
        List<PlayerTeam> teams = new ArrayList<>(scoreboard.getPlayerTeams());
        if (teams.isEmpty()) {
            return;
        }
        List<String> players = new ArrayList<>(playerNames);
        Collections.shuffle(players);
        Collections.shuffle(teams);
        for (int i = 0; i < players.size(); i++) {
            scoreboard.addPlayerToTeam(players.get(i), teams.get(i % teams.size()));
        }
    }

    /**
     * Upstream {@code jumpSpawnPoint}: every player in the sender's dimension goes to their bed (or
     * other respawn point) in that dimension, else to the dimension's spawn, as {@code /tp} would
     * take them: off their vehicle and facing the way they faced.
     *
     * @return how many players were moved
     */
    public static int jumpSpawnPoint(ServerPlayer sender, List<ServerPlayer> players) {
        ServerLevel level = sender.level();
        int moved = 0;
        for (ServerPlayer player : List.copyOf(players)) {
            if (player.level() != level) {
                continue;
            }
            TeleportTransition respawn = player.findRespawnPositionAndUseSpawnBlock(false,
                    TeleportTransition.DO_NOTHING);
            Vec3 pos;
            if (respawn.newLevel() == level && !respawn.missingRespawnBlock()) {
                pos = respawn.position();
            } else {
                BlockPos spawn = player.adjustSpawnLocation(level, level.getRespawnData().pos());
                pos = new Vec3(spawn.getX() + 0.5, spawn.getY() + 0.1, spawn.getZ() + 0.5);
            }
            player.stopRiding();
            player.teleportTo(level, pos.x, pos.y, pos.z, Set.of(), player.getYRot(), player.getXRot(), true);
            moved++;
        }
        return moved;
    }

    /**
     * Upstream {@code destoryAllAircraft}: every vehicle in the sender's dimension goes away, without
     * dropping its inventory (upstream's {@code setDead()}).
     *
     * @return how many vehicles were removed
     */
    public static int destroyAllAircraft(ServerLevel level) {
        List<AircraftEntity> aircraft = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof AircraftEntity ac && ac.isAlive()) {
                aircraft.add(ac);
            }
        }
        aircraft.forEach(ac -> ac.removeVehicle(false));
        return aircraft.size();
    }
}
