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
        ModGameTests.test("effects_range_permission_boundary", 20, AircraftMenuGameTests::rangePermission);
        ModGameTests.test("aircraft_appearance_menu_and_save", 30, AircraftMenuGameTests::appearance);
        ModGameTests.test("aircraft_command_needs_operator", 20, AircraftMenuGameTests::command);
    }


    private static void rangePermission(GameTestHelper h) {
        var player=(ServerPlayer)h.makeMockServerPlayer(GameType.SURVIVAL);var original=player.level().dimension();
        check(h,!io.github.mcheliceport.effectrange.EffectsRange.allowed(player),"ordinary players cannot enter range");
        check(h,!io.github.mcheliceport.effectrange.EffectsRange.act(player,0,0,0,0),"unprivileged request rejected");
        check(h,player.level().dimension().equals(original)&&player.gameMode()==GameType.SURVIVAL,"denial preserves world and mode");
        var players=h.getLevel().getServer().getPlayerList();var id=new NameAndId(player.getGameProfile());
        try {players.op(id,Optional.of(LevelBasedPermissionSet.GAMEMASTER),Optional.of(false));
            check(h,io.github.mcheliceport.effectrange.EffectsRange.allowed(player),"game master can use range");
            check(h,!io.github.mcheliceport.effectrange.EffectsRange.act(player,0,99,0,0),"malformed options rejected");
        } finally {players.deop(id);}
        check(h,player.level().dimension().equals(original),"invalid options never teleport");h.succeed();
    }

    private static void appearance(GameTestHelper h) {
        var heli=GameTestSupport.spawn(h,GameTestSupport.AH64,new Vec3(6.5,1,6.5),0);
        var other=GameTestSupport.spawn(h,GameTestSupport.AH64,new Vec3(20.5,1,6.5),0);
        var player=(ServerPlayer)h.makeMockServerPlayer(GameType.SURVIVAL);
        player.setPos(heli.position());
        var look=new io.github.mcheliceport.appearance.AircraftAppearance(127,2,1,1);
        String texture=heli.getAcInfo().getTextureNames().getLast();
        check(h,!io.github.mcheliceport.appearance.AircraftAppearance.apply(player,heli.getId(),texture,look.packed()),"closed menu cannot repaint");
        player.containerMenu=new io.github.mcheliceport.menu.AircraftMenu(3,player.getInventory(),heli);
        check(h,!io.github.mcheliceport.appearance.AircraftAppearance.apply(player,other.getId(),texture,look.packed()),"wrong menu vehicle rejected");
        check(h,!io.github.mcheliceport.appearance.AircraftAppearance.apply(player,heli.getId(),"../unknown",look.packed()),"arbitrary texture rejected");
        check(h,!io.github.mcheliceport.appearance.AircraftAppearance.apply(player,heli.getId(),texture,-1),"malformed packed appearance rejected");
        check(h,io.github.mcheliceport.appearance.AircraftAppearance.apply(player,heli.getId(),texture,look.packed()),"nearby menu applies valid paint");
        check(h,heli.getAppearance().equals(look)&&heli.getTextureName().equals(texture),"synced selection matches");
        player.setPos(other.position());
        check(h,!io.github.mcheliceport.appearance.AircraftAppearance.apply(player,heli.getId(),texture,0),"stale distant menu rejected");
        var saved=net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING,h.getLevel().registryAccess());heli.save(saved);
        var loaded=GameTestSupport.spawn(h,GameTestSupport.AH64,new Vec3(6.5,1,20.5),0);
        loaded.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING,h.getLevel().registryAccess(),saved.buildResult()));
        check(h,loaded.getAppearance().equals(look)&&loaded.getTextureName().equals(texture),"livery and cosmetic selections persist");
        player.containerMenu=player.inventoryMenu;
        GameTestSupport.cleanUp(heli);GameTestSupport.cleanUp(other);GameTestSupport.cleanUp(loaded);h.succeed();
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
