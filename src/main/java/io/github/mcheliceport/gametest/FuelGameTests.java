package io.github.mcheliceport.gametest;

import static io.github.mcheliceport.gametest.GameTestSupport.AH64;
import static io.github.mcheliceport.gametest.GameTestSupport.check;
import static io.github.mcheliceport.gametest.GameTestSupport.control;
import static io.github.mcheliceport.gametest.GameTestSupport.player;
import static io.github.mcheliceport.gametest.GameTestSupport.spawn;

import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.net.HeliControlPayload;
import io.github.mcheliceport.registry.ModItems;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** Exit criterion E8: fuel use and refuelling from a fuel can in the aircraft menu's slot. */
final class FuelGameTests {

    private static final int MEASURE_FROM = 60;
    private static final int MEASURE_TICKS = 400;

    private FuelGameTests() {
    }

    static void register() {
        ModGameTests.test("fuel_consumption", MEASURE_FROM + MEASURE_TICKS + 40, FuelGameTests::consumption);
        ModGameTests.test("fuel_empty_cannot_climb", 160, FuelGameTests::emptyCannotClimb);
        ModGameTests.test("fuel_refuel_from_can", 100, FuelGameTests::refuelFromCan);
    }

    private static Player board(GameTestHelper helper, HeliEntity heli) {
        Player pilot = player(helper, GameType.SURVIVAL, new Vec3(12.5, 1.0, 9.5));
        heli.interact(pilot, InteractionHand.MAIN_HAND, Vec3.ZERO);
        check(helper, heli.getPilot() == pilot, "the pilot boarded");
        return pilot;
    }

    /** Hovering on the ground keeps the throttle at 1 without lift, so the burn rate is steady. */
    private static void consumption(GameTestHelper helper) {
        HeliEntity heli = spawn(helper, AH64, new Vec3(12.5, 1.0, 12.5), 0.0F);
        heli.setFuel(heli.getMaxFuel());
        Player pilot = board(helper, heli);
        GameTestSupport.mode(heli, pilot, HeliControlPayload.ModeSwitch.HOVERING_ON);
        check(helper, heli.isHoveringMode(), "hovering mode on");
        AircraftInfo info = heli.getAcInfo();
        int[] fuelAt = new int[1];
        helper.runAfterDelay(MEASURE_FROM, () -> {
            check(helper, heli.getThrottle() == 1.0, "full throttle: " + heli.getThrottle());
            fuelAt[0] = heli.getFuel();
        });
        helper.runAfterDelay(MEASURE_FROM + MEASURE_TICKS, () -> {
            double expected = MEASURE_TICKS / 20.0 * info.fuelConsumption * heli.getFuelConsumptionFactor();
            int used = fuelAt[0] - heli.getFuel();
            check(helper, Math.abs(used - expected) <= 1.5, "fuel used at upstream's rate: " + used + " for "
                    + expected);
            GameTestSupport.cleanUp(heli);
            helper.succeed();
        });
    }

    private static void emptyCannotClimb(GameTestHelper helper) {
        HeliEntity heli = spawn(helper, AH64, new Vec3(12.5, 1.0, 12.5), 0.0F);
        heli.setFuel(1);
        Player pilot = board(helper, heli);
        double startY = heli.getY();
        check(helper, !heli.canUseFuel(true), "1 fuel cannot run the engine");
        helper.onEachTick(() -> control(heli, pilot, true, false, false, false));
        helper.runAfterDelay(120, () -> {
            check(helper, heli.getY() - startY < 0.01, "it stayed on the ground: " + (heli.getY() - startY));
            check(helper, heli.getCurrentThrottle() == 0.0, "the throttle stayed at 0: " + heli.getCurrentThrottle());
            GameTestSupport.cleanUp(heli);
            helper.succeed();
        });
    }

    private static void refuelFromCan(GameTestHelper helper) {
        HeliEntity heli = spawn(helper, AH64, new Vec3(12.5, 1.0, 12.5), 0.0F);
        check(helper, heli.getFuel() == 0, "a placed helicopter starts empty");
        ItemStack can = new ItemStack(ModItems.FUEL.get());
        int canFuel = can.getMaxDamage();
        heli.getInventory().setItem(AircraftEntity.SLOT_FUEL_IN, can);
        helper.succeedWhen(() -> {
            int expected = Math.min(canFuel, heli.getMaxFuel());
            check(helper, heli.getFuel() == expected, "the can's fuel went into the tank: " + heli.getFuel());
            check(helper, heli.getInventory().getItem(AircraftEntity.SLOT_FUEL_IN).isEmpty(), "the can left the slot");
            ItemStack empty = heli.getInventory().getItem(AircraftEntity.SLOT_FUEL_OUT);
            check(helper, empty.is(ModItems.FUEL.get()) && empty.getDamageValue() == empty.getMaxDamage(),
                    "an empty can waits in the output slot");
            GameTestSupport.cleanUp(heli);
        });
    }
}
