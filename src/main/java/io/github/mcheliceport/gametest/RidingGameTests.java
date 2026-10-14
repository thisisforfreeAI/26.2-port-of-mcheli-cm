package io.github.mcheliceport.gametest;

import static io.github.mcheliceport.gametest.GameTestSupport.AH64;
import static io.github.mcheliceport.gametest.GameTestSupport.check;
import static io.github.mcheliceport.gametest.GameTestSupport.player;
import static io.github.mcheliceport.gametest.GameTestSupport.spawn;

import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.entity.seat.SeatEntity;
import io.github.mcheliceport.net.HeliControlPayload;
import io.github.mcheliceport.net.ModNetwork;
import io.github.mcheliceport.net.PlayerControlSwitches;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** Exit criterion E6: boarding, seat switching and getting out, as upstream. */
final class RidingGameTests {

    private RidingGameTests() {
    }

    static void register() {
        ModGameTests.test("riding_canopy_then_board", 160, RidingGameTests::canopyThenBoard);
        ModGameTests.test("riding_switch_seat", 60, RidingGameTests::switchSeat);
        ModGameTests.test("riding_unmount", 60, RidingGameTests::unmount);
        ModGameTests.test("riding_destroyed", 40, RidingGameTests::destroyed);
        ModGameTests.test("riding_landing_does_not_hurt", 200, RidingGameTests::landingDoesNotHurt);
    }

    private static Player rider(GameTestHelper helper) {
        return player(helper, GameType.SURVIVAL, new Vec3(12.5, 1.0, 9.5));
    }

    private static void canopyThenBoard(GameTestHelper helper) {
        HeliEntity heli = spawn(helper, "ah-1z", new Vec3(12.5, 1.0, 12.5), 0.0F);
        Player pilot = rider(helper);
        Player gunner = rider(helper);
        helper.runAfterDelay(5, () -> {
            check(helper, heli.isCanopyClose(), "the canopy starts closed");
            heli.interact(pilot, InteractionHand.MAIN_HAND, Vec3.ZERO);
            check(helper, pilot.getVehicle() == null, "the first use opens the canopy");
            check(helper, !heli.isCanopyClose(), "the canopy opened");
        });
        helper.runAfterDelay(60, () -> {
            heli.interact(pilot, InteractionHand.MAIN_HAND, Vec3.ZERO);
            check(helper, heli.getPilot() == pilot, "the next use boards the pilot's seat");
            heli.interact(gunner, InteractionHand.MAIN_HAND, Vec3.ZERO);
            check(helper, gunner.getVehicle() instanceof SeatEntity seat && seat.getParent() == heli,
                    "a second player boards a crew seat");
            check(helper, heli.getSeatIdByEntity(gunner) == 1, "the gunner sits in seat 1");
        });
        helper.runAfterDelay(100, () -> {
            check(helper, heli.isCanopyClose(), "boarding closes the canopy (its animation takes 26 ticks)");
            GameTestSupport.cleanUp(heli);
            helper.succeed();
        });
    }

    private static void switchSeat(GameTestHelper helper) {
        HeliEntity heli = spawn(helper, "uh-1c", new Vec3(12.5, 1.0, 12.5), 0.0F);
        Player pilot = rider(helper);
        Player crew = rider(helper);
        helper.runAfterDelay(5, () -> {
            check(helper, heli.getSeatNum() == 3, "the UH-1C has three crew seats, got " + heli.getSeatNum());
            heli.interact(pilot, InteractionHand.MAIN_HAND, Vec3.ZERO);
            heli.interact(crew, InteractionHand.MAIN_HAND, Vec3.ZERO);
            check(helper, heli.getSeatIdByEntity(crew) == 1, "the crew boards seat 1");
            heli.switchNextSeat(crew);
            check(helper, heli.getSeatIdByEntity(crew) == 2, "next seat: 2, got " + heli.getSeatIdByEntity(crew));
            heli.switchNextSeat(crew);
            heli.switchNextSeat(crew);
            check(helper, heli.getSeatIdByEntity(crew) == 1, "past the last seat it wraps to seat 1, got "
                    + heli.getSeatIdByEntity(crew));
            heli.switchPrevSeat(crew);
            check(helper, heli.getSeatIdByEntity(crew) == 3, "previous seat wraps to 3, got "
                    + heli.getSeatIdByEntity(crew));
            check(helper, heli.getPilot() == pilot, "the pilot stays put");
            GameTestSupport.cleanUp(heli);
            helper.succeed();
        });
    }

    private static void unmount(GameTestHelper helper) {
        HeliEntity heli = spawn(helper, AH64, new Vec3(12.5, 1.0, 12.5), 0.0F);
        Player pilot = rider(helper);
        Player gunner = rider(helper);
        helper.runAfterDelay(5, () -> {
            heli.interact(pilot, InteractionHand.MAIN_HAND, Vec3.ZERO);
            heli.interact(gunner, InteractionHand.MAIN_HAND, Vec3.ZERO);
            check(helper, heli.getPilot() == pilot && heli.getSeatIdByEntity(gunner) == 1, "both aboard");
            HeliControlPayload unmount = new HeliControlPayload(heli.getId(),
                    HeliControlPayload.UnmountAction.UNMOUNT_SELF, HeliControlPayload.ModeSwitch.NONE,
                    HeliControlPayload.HatchSwitch.NONE, HeliControlPayload.GearSwitch.NONE,
                    PlayerControlSwitches.NONE, (byte) 0, HeliControlPayload.BladeStatus.NONE, (byte) 0);
            ModNetwork.applyHeliControl(heli, gunner, unmount);
            check(helper, gunner.getVehicle() == null, "the gunner's unmount key lets the gunner out (port fix)");
            check(helper, heli.getPilot() == pilot, "and leaves the pilot aboard");
            ModNetwork.applyHeliControl(heli, pilot, unmount);
            check(helper, pilot.getVehicle() == null, "the pilot got out");
            for (Player p : new Player[] {pilot, gunner}) {
                Vec3 rel = helper.relativeVec(p.position());
                check(helper, rel.y >= 1.0 - 1.0E-6 && rel.y < 4.0, "out on the floor, not under it: " + rel);
                check(helper, helper.getLevel().noCollision(p), "not inside a block at " + rel);
                check(helper, p.distanceTo(heli) < 8.0, "next to the helicopter: " + p.distanceTo(heli));
            }
            GameTestSupport.cleanUp(heli);
            helper.succeed();
        });
    }

    private static void destroyed(GameTestHelper helper) {
        HeliEntity heli = spawn(helper, AH64, new Vec3(12.5, 1.0, 12.5), 0.0F);
        Player player = rider(helper);
        helper.runAfterDelay(5, () -> {
            heli.setDamageTaken(heli.getMaxHP());
            heli.destroyAircraft();
            check(helper, heli.isDestroyed(), "destroyed");
            heli.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO);
            check(helper, player.getVehicle() == null, "a destroyed helicopter cannot be boarded");
            GameTestSupport.cleanUp(heli);
            helper.succeed();
        });
    }

    private static void landingDoesNotHurt(GameTestHelper helper) {
        HeliEntity heli = spawn(helper, AH64, new Vec3(12.5, 12.0, 12.5), 0.0F);
        Player pilot = rider(helper);
        heli.interact(pilot, InteractionHand.MAIN_HAND, Vec3.ZERO);
        check(helper, heli.getPilot() == pilot, "the pilot boarded in the air");
        float health = pilot.getHealth();
        helper.succeedWhen(() -> {
            check(helper, heli.onGround(), "landed");
            check(helper, pilot.getHealth() == health, "the pilot took no fall damage: " + pilot.getHealth());
            check(helper, pilot.getVehicle() == heli, "still aboard");
            GameTestSupport.cleanUp(heli);
        });
    }
}
