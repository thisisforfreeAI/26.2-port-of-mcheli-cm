package io.github.mcheliceport.gametest;

import static io.github.mcheliceport.gametest.GameTestSupport.check;

import io.github.mcheliceport.entity.heli.HeliEntity;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** The aircraft menu's command field (upstream {@code MCH_AircraftGui} and {@code setCommand}). */
final class AircraftMenuGameTests {

    private AircraftMenuGameTests() {
    }

    static void register() {
        ModGameTests.test("aircraft_command_needs_operator", 20, AircraftMenuGameTests::command);
    }

    /**
     * Only a game master may set the command, since it runs with full permissions when the vehicle
     * is destroyed by anything but a player.
     */
    private static void command(GameTestHelper h) {
        HeliEntity heli = GameTestSupport.spawn(h, GameTestSupport.AH64, new Vec3(6.5, 1, 6.5), 0.0F);
        ServerPlayer player = (ServerPlayer) h.makeMockServerPlayer(GameType.SURVIVAL);
        BlockPos marker = h.absolutePos(new BlockPos(20, 1, 20));
        String command = "/setblock " + marker.getX() + " " + marker.getY() + " " + marker.getZ()
                + " minecraft:gold_block";

        heli.setCommand(command, player);
        check(h, heli.getCommand().isEmpty(), "a player without permission cannot set the command");

        PlayerList players = h.getLevel().getServer().getPlayerList();
        NameAndId id = new NameAndId(player.getGameProfile());
        try {
            players.op(id, Optional.of(LevelBasedPermissionSet.MODERATOR), Optional.of(false));
            heli.setCommand(command, player);
            check(h, heli.getCommand().isEmpty(), "a moderator cannot set the command");
            players.op(id, Optional.of(LevelBasedPermissionSet.GAMEMASTER), Optional.of(false));
            heli.setCommand(command, player);
        } finally {
            players.deop(id);
        }
        check(h, heli.getCommand().equals(command), "a game master sets the command");

        heli.hurtServer(h.getLevel(), heli.damageSources().generic(), heli.getMaxHP() * 2.0F);
        check(h, heli.isDestroyed(), "the vehicle is destroyed");
        check(h, h.getLevel().getBlockState(marker).is(Blocks.GOLD_BLOCK), "the command ran on destruction");
        GameTestSupport.cleanUp(heli);
        h.succeed();
    }
}
