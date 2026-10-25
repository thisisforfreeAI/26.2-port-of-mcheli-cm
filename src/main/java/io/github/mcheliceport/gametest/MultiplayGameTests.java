package io.github.mcheliceport.gametest;

import static io.github.mcheliceport.gametest.GameTestSupport.check;

import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.multiplay.MultiplayManager;
import io.github.mcheliceport.net.MultiplayActionPayload;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/** The multiplay manager's server actions (upstream {@code MCH_Multiplay} and {@code PacketHandleCommand}). */
final class MultiplayGameTests {

    private MultiplayGameTests() {
    }

    static void register() {
        ModGameTests.test("multiplay_shuffle_team", 20, MultiplayGameTests::shuffle);
        ModGameTests.test("multiplay_jump_spawn_point", 20, MultiplayGameTests::jumpSpawn);
        ModGameTests.testAlone("multiplay_actions_need_operator", 20, MultiplayGameTests::operator);
        ModGameTests.testAlone("multiplay_join_and_leave_team", 20, MultiplayGameTests::joinTeam);
    }

    /**
     * The manager's team buttons: a player joins an existing team, moves to another, leaves, and
     * cannot join a team that does not exist; with {@code playersChooseTeam} off only a game master
     * can.
     */
    private static void joinTeam(GameTestHelper h) {
        ServerPlayer player = (ServerPlayer) h.makeMockServerPlayer(GameType.SURVIVAL);
        MinecraftServer server = h.getLevel().getServer();
        Scoreboard scoreboard = server.getScoreboard();
        String name = player.getScoreboardName();
        PlayerTeam red = scoreboard.addPlayerTeam("mch_test_red");
        PlayerTeam blue = scoreboard.addPlayerTeam("mch_test_blue");
        boolean allowed = io.github.mcheliceport.config.ServerConfig.PLAYERS_CHOOSE_TEAM.get();
        PlayerList players = server.getPlayerList();
        NameAndId id = new NameAndId(player.getGameProfile());
        try {
            io.github.mcheliceport.config.ServerConfig.PLAYERS_CHOOSE_TEAM.set(true);
            MultiplayManager.handle(player, MultiplayActionPayload.Action.JOIN_TEAM, "mch_test_red");
            check(h, scoreboard.getPlayersTeam(name) == red, "the player joined red");
            MultiplayManager.handle(player, MultiplayActionPayload.Action.JOIN_TEAM, "mch_test_blue");
            check(h, scoreboard.getPlayersTeam(name) == blue, "the player moved to blue");
            check(h, !MultiplayManager.joinTeam(player, "mch_test_none"), "a missing team cannot be joined");
            check(h, scoreboard.getPlayersTeam(name) == blue, "and the player stays in blue");
            MultiplayManager.handle(player, MultiplayActionPayload.Action.LEAVE_TEAM);
            check(h, scoreboard.getPlayersTeam(name) == null, "the player left their team");

            io.github.mcheliceport.config.ServerConfig.PLAYERS_CHOOSE_TEAM.set(false);
            check(h, !MultiplayManager.canChooseTeam(player), "with the setting off a player cannot choose");
            MultiplayManager.handle(player, MultiplayActionPayload.Action.JOIN_TEAM, "mch_test_red");
            check(h, scoreboard.getPlayersTeam(name) == null, "so the join does nothing");
            players.op(id, Optional.of(LevelBasedPermissionSet.GAMEMASTER), Optional.of(false));
            MultiplayManager.handle(player, MultiplayActionPayload.Action.JOIN_TEAM, "mch_test_red");
            check(h, scoreboard.getPlayersTeam(name) == red, "but a game master can");
        } finally {
            players.deop(id);
            io.github.mcheliceport.config.ServerConfig.PLAYERS_CHOOSE_TEAM.set(allowed);
            scoreboard.removePlayerTeam(red);
            scoreboard.removePlayerTeam(blue);
        }
        h.succeed();
    }

    /** The players join the teams in turn, so team sizes differ by at most one. */
    private static void shuffle(GameTestHelper h) {
        Scoreboard scoreboard = new Scoreboard();
        MultiplayManager.shuffleTeam(scoreboard, List.of("a", "b", "c"));
        check(h, scoreboard.getPlayersTeam("a") == null, "with no team nobody joins one");

        PlayerTeam red = scoreboard.addPlayerTeam("red");
        PlayerTeam blue = scoreboard.addPlayerTeam("blue");
        scoreboard.addPlayerToTeam("e", red);
        MultiplayManager.shuffleTeam(scoreboard, List.of("a", "b", "c", "d", "e"));
        for (String name : List.of("a", "b", "c", "d", "e")) {
            check(h, scoreboard.getPlayersTeam(name) != null, name + " joined a team");
        }
        int reds = red.getPlayers().size();
        int blues = blue.getPlayers().size();
        check(h, reds + blues == 5 && Math.abs(reds - blues) == 1, "teams of 3 and 2, not " + reds + " and " + blues);
        h.succeed();
    }

    /** A player goes to their respawn point in the sender's dimension, off their vehicle. */
    private static void jumpSpawn(GameTestHelper h) {
        ServerPlayer sender = (ServerPlayer) h.makeMockServerPlayer(GameType.SURVIVAL);
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(h.absoluteVec(new Vec3(2.5, 1, 2.5)));
        BlockPos spawn = h.absolutePos(new BlockPos(10, 1, 10));
        player.setRespawnPosition(new ServerPlayer.RespawnConfig(
                LevelData.RespawnData.of(h.getLevel().dimension(), spawn, 0.0F, 0.0F), true), false);
        HeliEntity heli = GameTestSupport.spawn(h, GameTestSupport.AH64, new Vec3(4.5, 1, 4.5), 0.0F);
        check(h, player.startRiding(heli, true, true), "the player boards");

        int moved = MultiplayManager.jumpSpawnPoint(sender, List.of(player));
        check(h, moved == 1, "one player moved, not " + moved);
        check(h, player.getVehicle() == null, "the player left the vehicle");
        Vec3 expected = new Vec3(spawn.getX() + 0.5, spawn.getY() + 0.1, spawn.getZ() + 0.5);
        check(h, player.position().distanceTo(expected) < 0.01, "at the spawn point, not " + player.position());
        GameTestSupport.cleanUp(heli);
        h.succeed();
    }

    /**
     * The buttons do nothing for a player who is not a game master; for one, PVP flips and every
     * vehicle goes away without dropping its inventory.
     */
    private static void operator(GameTestHelper h) {
        HeliEntity heli = GameTestSupport.spawn(h, GameTestSupport.AH64, new Vec3(6.5, 1, 6.5), 0.0F);
        ServerPlayer player = (ServerPlayer) h.makeMockServerPlayer(GameType.SURVIVAL);
        MinecraftServer server = h.getLevel().getServer();
        boolean pvp = MultiplayManager.isPvp(server);

        MultiplayManager.handle(player, MultiplayActionPayload.Action.SET_PVP);
        MultiplayManager.handle(player, MultiplayActionPayload.Action.DESTROY_AIRCRAFT);
        check(h, MultiplayManager.isPvp(server) == pvp, "PVP is unchanged without permission");
        check(h, heli.isAlive(), "the vehicle stays without permission");

        PlayerList players = server.getPlayerList();
        NameAndId id = new NameAndId(player.getGameProfile());
        boolean flipped;
        try {
            players.op(id, Optional.of(LevelBasedPermissionSet.MODERATOR), Optional.of(false));
            MultiplayManager.handle(player, MultiplayActionPayload.Action.DESTROY_AIRCRAFT);
            check(h, heli.isAlive(), "a moderator cannot remove the vehicles");
            players.op(id, Optional.of(LevelBasedPermissionSet.GAMEMASTER), Optional.of(false));
            MultiplayManager.handle(player, MultiplayActionPayload.Action.SET_PVP);
            flipped = MultiplayManager.isPvp(server) != pvp;
            MultiplayManager.handle(player, MultiplayActionPayload.Action.SET_PVP);
            MultiplayManager.handle(player, MultiplayActionPayload.Action.DESTROY_AIRCRAFT);
        } finally {
            players.deop(id);
        }
        check(h, flipped, "a game master flips PVP");
        check(h, MultiplayManager.isPvp(server) == pvp, "and flips it back");
        check(h, !heli.isAlive(), "a game master removes the vehicles");
        check(h, h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                heli.getBoundingBox().inflate(8.0)).isEmpty(), "without dropping their inventory");
        h.succeed();
    }
}
