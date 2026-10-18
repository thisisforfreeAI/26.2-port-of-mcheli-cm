package io.github.mcheliceport.gametest;

import static io.github.mcheliceport.gametest.GameTestSupport.AH64;
import static io.github.mcheliceport.gametest.GameTestSupport.check;
import static io.github.mcheliceport.gametest.GameTestSupport.spawn;

import io.github.mcheliceport.content.ContentLocation;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.content.aircraft.HeliInfo;
import io.github.mcheliceport.content.aircraft.SeatInfo;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.registry.ModEntityTypes;
import io.github.mcheliceport.registry.ModFluids;
import io.github.mcheliceport.registry.ModItems;
import java.util.Map;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Upstream's {@code mch_fuel} fluid: refuelling from fluid containers in the fuel slot and the
 * vehicle's tank as a fluid handler ({@code MCH_EntityAircraft#updateFuel}, {@code fill},
 * {@code drain}).
 */
final class FuelFluidGameTests {

    private FuelFluidGameTests() {
    }

    static void register() {
        ModGameTests.test("fuel_bucket_refuels", 100, FuelFluidGameTests::bucket);
        ModGameTests.test("fuel_container_needs_output_room", 60, FuelFluidGameTests::outputFull);
        ModGameTests.test("fuel_tank_fluid_handler", 20, FuelFluidGameTests::tank);
    }

    private static void bucket(GameTestHelper h) {
        HeliEntity heli = spawn(h, AH64, new Vec3(12.5, 1.0, 12.5), 0.0F);
        heli.getInventory().setItem(AircraftEntity.SLOT_FUEL_IN, new ItemStack(ModItems.FUEL_BUCKET.get(), 1));
        h.succeedWhen(() -> {
            check(h, heli.getFuel() == 1000 && heli.getFuelType().equals("mch_fuel"),
                    "a bucket gives 1000 fuel: " + heli.getFuel());
            check(h, heli.getInventory().getItem(AircraftEntity.SLOT_FUEL_IN).isEmpty(), "the bucket left the slot");
            check(h, heli.getInventory().getItem(AircraftEntity.SLOT_FUEL_OUT).is(Items.BUCKET),
                    "an empty bucket waits in the output slot");
            GameTestSupport.cleanUp(heli);
        });
    }

    /**
     * Nothing moves while the output slot cannot take the emptied container, and a container of
     * something the vehicle does not burn stays where it is.
     */
    private static void outputFull(GameTestHelper h) {
        HeliEntity heli = spawn(h, AH64, new Vec3(12.5, 1.0, 12.5), 0.0F);
        HeliEntity water = spawn(h, AH64, new Vec3(6.5, 1.0, 6.5), 0.0F);
        heli.getInventory().setItem(AircraftEntity.SLOT_FUEL_IN, new ItemStack(ModItems.FUEL_BUCKET.get(), 1));
        heli.getInventory().setItem(AircraftEntity.SLOT_FUEL_OUT, new ItemStack(Items.STONE));
        water.getInventory().setItem(AircraftEntity.SLOT_FUEL_IN, new ItemStack(Items.WATER_BUCKET));
        h.runAfterDelay(40, () -> {
            check(h, heli.getFuel() == 0, "no fuel without room for the bucket: " + heli.getFuel());
            check(h, heli.getInventory().getItem(AircraftEntity.SLOT_FUEL_IN).is(ModItems.FUEL_BUCKET.get()),
                    "the fuel bucket stays in the slot");
            check(h, water.getFuel() == 0 && water.getInventory().getItem(AircraftEntity.SLOT_FUEL_IN)
                    .is(Items.WATER_BUCKET), "water is not fuel");
            GameTestSupport.cleanUp(heli);
            GameTestSupport.cleanUp(water);
            h.succeed();
        });
    }

    private static HeliEntity fixture(GameTestHelper h, boolean electric, double x) {
        var definition = new HeliInfo(ContentLocation.builtin("fluid_fixture"), "fluid_fixture.yml", "yaml");
        definition.maxFuel = 2000;
        definition.maxHp = 1000;
        definition.fuelConsumption = 0;
        definition.electric = electric;
        definition.setFluidType(Map.of("mch_fuel", 1f, "lava", 2f));
        definition.seatList.add(new SeatInfo(Vec3.ZERO, false));
        var ac = new HeliEntity(ModEntityTypes.HELICOPTER.get(), h.getLevel()) {
            @Override protected AircraftInfo lookupInfo(String name) { return definition; }
            @Override protected void onUpdateAircraft() { }
        };
        ac.setPos(h.absoluteVec(new Vec3(x, 1, 12)));
        ac.setAircraftTypeName("fluid_fixture");
        h.getLevel().addFreshEntity(ac);
        return ac;
    }

    private static int insert(ResourceHandler<FluidResource> tank, FluidResource fluid, int amount, boolean commit) {
        try (Transaction tx = Transaction.openRoot()) {
            int moved = tank.insert(fluid, amount, tx);
            if (commit) {
                tx.commit();
            }
            return moved;
        }
    }

    private static int extract(ResourceHandler<FluidResource> tank, FluidResource fluid, int amount) {
        try (Transaction tx = Transaction.openRoot()) {
            int moved = tank.extract(fluid, amount, tx);
            tx.commit();
            return moved;
        }
    }

    private static void tank(GameTestHelper h) {
        HeliEntity heli = fixture(h, false, 9);
        HeliEntity electric = fixture(h, true, 15);
        var tank = heli.getCapability(Capabilities.Fluid.ENTITY, null);
        check(h, tank != null, "a fuelled vehicle has a fluid tank");
        check(h, electric.getCapability(Capabilities.Fluid.ENTITY, null) == null, "an electric vehicle has none");

        FluidResource fuel = FluidResource.of(ModFluids.FUEL.get());
        FluidResource lava = FluidResource.of(Fluids.LAVA);
        check(h, insert(tank, FluidResource.of(Fluids.WATER), 500, true) == 0, "water is not accepted");
        check(h, insert(tank, fuel, 500, false) == 500 && heli.getFuel() == 0, "an aborted fill changes nothing");
        check(h, insert(tank, fuel, 500, true) == 500 && heli.getFuel() == 500
                && heli.getFuelType().equals("mch_fuel"), "fuel fills the tank: " + heli.getFuel());
        check(h, insert(tank, lava, 500, true) == 0, "fuels do not mix");
        check(h, insert(tank, fuel, 5000, true) == 1500 && heli.getFuel() == 2000, "the tank does not overfill");
        check(h, tank.getResource(0).equals(fuel) && tank.getAmountAsInt(0) == 2000, "the tank shows its fuel");
        check(h, extract(tank, lava, 100) == 0, "only the fuel it holds drains");
        check(h, extract(tank, fuel, 2500) == 2000 && heli.getFuel() == 0, "the fuel drains");
        check(h, insert(tank, lava, 300, true) == 300 && heli.getFuelType().equals("lava"),
                "an empty tank takes another accepted fuel");
        GameTestSupport.cleanUp(heli);
        GameTestSupport.cleanUp(electric);
        h.succeed();
    }
}
