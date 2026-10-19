package io.github.mcheliceport.gametest;

import static io.github.mcheliceport.gametest.GameTestSupport.AH64;
import static io.github.mcheliceport.gametest.GameTestSupport.check;

import io.github.mcheliceport.config.ServerConfig;
import io.github.mcheliceport.entity.NpcBattles;
import io.github.mcheliceport.entity.gunner.GunnerEntity;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.wingman.handler.WingmanTickHandler;
import io.github.mcheliceport.wingman.wingman.WingmanEntry;
import io.github.mcheliceport.wingman.wingman.WingmanRegistry;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/** NPC battles, a port addition: gunners and unmanned aircraft of different teams fight. */
final class NpcBattleGameTests {

    private NpcBattleGameTests() {
    }

    static void register() {
        ModGameTests.test("npc_battle_gunner_fires_at_other_team", 200, NpcBattleGameTests::gunnerFires);
        ModGameTests.testAlone("npc_battle_teams_and_targets", 20, NpcBattleGameTests::teams);
    }

    /** A vs-player gunner of one team turns on the gunner of another team in a helicopter ahead and fires. */
    private static void gunnerFires(GameTestHelper h) {
        HeliEntity red = GameTestSupport.spawn(h, AH64, new Vec3(8.5, 1, 4.5), 0.0F);
        HeliEntity blue = GameTestSupport.spawn(h, AH64, new Vec3(8.5, 1, 20.5), 0.0F);
        Scoreboard scoreboard = h.getLevel().getScoreboard();
        h.runAfterDelay(2, () -> {
            GunnerEntity redGunner = GunnerGameTests.seatGunner(h, red, GunnerEntity.VS_PLAYER);
            GunnerEntity blueGunner = GunnerGameTests.seatGunner(h, blue, GunnerEntity.VS_PLAYER);
            redGunner.setTeamName("npcb_fire_red");
            blueGunner.setTeamName("npcb_fire_blue");
            blue.setGunnerStatus(false);
            h.runAfterDelay(2, () -> GunnerGameTests.loadM230(h, red, redGunner));
            h.succeedWhen(() -> {
                check(h, redGunner.getTargetEntity() == blueGunner || redGunner.getTargetEntity() == blue,
                        "the gunner has not picked the other team's helicopter");
                check(h, GunnerGameTests.bulletsNear(h, red), "the gunner has not fired");
                for (GunnerEntity g : List.of(redGunner, blueGunner)) {
                    g.stopRiding();
                    g.discard();
                }
                GameTestSupport.cleanUp(red);
                GameTestSupport.cleanUp(blue);
            });
        });
    }

    private static WingmanEntry ordered(HeliEntity heli, String team) {
        WingmanEntry entry = new WingmanEntry();
        entry.teamName = team;
        WingmanRegistry.put(heli.getUUID(), entry);
        return entry;
    }

    /**
     * An NPC fights for its team: a gunner for its own, an aircraft sent out for its player's, a
     * wingman for its leader's pilot's, an unregistered aircraft for its gunner's, and nothing a
     * player rides. Wingmen and gunners attack the other teams' gunners and aircraft and spare
     * their own; with the option off they keep upstream's targets.
     */
    private static void teams(GameTestHelper h) {
        HeliEntity redAc = GameTestSupport.spawn(h, AH64, new Vec3(2, 2, 2), 0.0F);
        HeliEntity blueAc = GameTestSupport.spawn(h, AH64, new Vec3(14, 2, 2), 0.0F);
        HeliEntity crewed = GameTestSupport.spawn(h, AH64, new Vec3(2, 2, 14), 0.0F);
        HeliEntity leader = GameTestSupport.spawn(h, AH64, new Vec3(14, 2, 14), 0.0F);
        HeliEntity wingman = GameTestSupport.spawn(h, AH64, new Vec3(26, 2, 2), 0.0F);
        List<HeliEntity> helis = List.of(redAc, blueAc, crewed, leader, wingman);
        Scoreboard scoreboard = h.getLevel().getScoreboard();
        PlayerTeam red = scoreboard.addPlayerTeam("npcb_red");
        PlayerTeam blue = scoreboard.addPlayerTeam("npcb_blue");
        ServerPlayer pilot = h.makeMockServerPlayerInLevel();
        h.runAfterDelay(2, () -> {
            GunnerEntity redGunner = GunnerGameTests.seatGunner(h, redAc, GunnerEntity.VS_PLAYER);
            GunnerEntity blueGunner = GunnerGameTests.seatGunner(h, crewed, GunnerEntity.VS_PLAYER);
            try {
                redGunner.setTeamName("npcb_red");
                blueGunner.setTeamName("npcb_blue");
                ordered(redAc, "npcb_red");
                ordered(blueAc, "npcb_blue");
                pilot.setGameMode(GameType.SURVIVAL);
                pilot.startRiding(leader, true, true);
                scoreboard.addPlayerToTeam(pilot.getScoreboardName(), red);
                WingmanRegistry.put(wingman.getUUID(), new WingmanEntry(leader, 0));

                check(h, NpcBattles.teamOf(redGunner).equals("npcb_red") && NpcBattles.teamOf(redAc).equals("npcb_red")
                        && NpcBattles.teamOf(blueAc).equals("npcb_blue") && NpcBattles.teamOf(crewed).equals("npcb_blue")
                        && NpcBattles.teamOf(wingman).equals("npcb_red") && NpcBattles.teamOf(leader).isEmpty(),
                        "an NPC fights for the wrong team");

                check(h, WingmanTickHandler.isEnemy(null, "npcb_red", blueAc)
                        && WingmanTickHandler.isEnemy(null, "npcb_red", crewed)
                        && WingmanTickHandler.isEnemy(null, "npcb_red", blueGunner),
                        "an aircraft does not attack the other team");
                check(h, !WingmanTickHandler.isEnemy(null, "npcb_red", wingman)
                        && !WingmanTickHandler.isEnemy(null, "npcb_red", redGunner)
                        && !WingmanTickHandler.isEnemy(null, "npcb_red", leader)
                        && !WingmanTickHandler.isEnemy(null, "", blueAc),
                        "an aircraft attacks its own team, a player or without a team");
                check(h, WingmanTickHandler.isEnemy(leader, NpcBattles.teamOf(wingman), blueGunner)
                        && !WingmanTickHandler.isEnemy(leader, NpcBattles.teamOf(wingman), redGunner),
                        "a wingman does not fight for its leader's team");

                var ws = redAc.getCurrentWeapon(redGunner);
                check(h, redGunner.canAttackNpc(blueAc, redAc, ws) && redGunner.canAttackNpc(blueGunner, redAc, ws)
                        && !redGunner.canAttackNpc(redAc, redAc, ws) && !redGunner.canAttackNpc(wingman, redAc, ws)
                        && !redGunner.canAttackNpc(leader, redAc, ws), "the gunner picks the wrong NPCs");

                GameTestSupport.withConfig(ServerConfig.NPC_BATTLES, false, () -> check(h,
                        !WingmanTickHandler.isEnemy(null, "npcb_red", blueAc)
                                && !redGunner.canAttackNpc(blueAc, redAc, ws)
                                && WingmanTickHandler.isEnemy(leader, NpcBattles.teamOf(wingman), blueGunner),
                        "with NPC battles off the targets are not upstream's"));
            } finally {
                helis.forEach(ac -> WingmanRegistry.remove(ac.getUUID()));
                for (GunnerEntity g : List.of(redGunner, blueGunner)) {
                    g.stopRiding();
                    g.discard();
                }
                pilot.stopRiding();
                scoreboard.removePlayerTeam(red);
                scoreboard.removePlayerTeam(blue);
                helis.forEach(GameTestSupport::cleanUp);
            }
            h.succeed();
        });
    }
}
