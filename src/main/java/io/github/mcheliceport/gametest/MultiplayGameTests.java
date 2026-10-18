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
