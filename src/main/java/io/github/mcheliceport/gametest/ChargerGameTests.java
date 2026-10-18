package io.github.mcheliceport.gametest;

import static io.github.mcheliceport.gametest.GameTestSupport.check;

import io.github.mcheliceport.block.ChargerBlockEntity;
import io.github.mcheliceport.compat.energy.EnergyCompat;
import io.github.mcheliceport.content.ContentLocation;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.content.aircraft.HeliInfo;
import io.github.mcheliceport.content.aircraft.SeatInfo;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.menu.AircraftMenu;
import io.github.mcheliceport.registry.ModBlocks;
import io.github.mcheliceport.registry.ModEntityTypes;
import io.github.mcheliceport.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.ItemAccessEnergyHandler;

/**
 * The aircraft charger and batteries in electric vehicles (upstream {@code MCH_TileEntityCharger}
 * and {@code updateElectricBattery}). The vehicles are stationary fixtures, and the charger and
 * vehicles are stepped by hand within one game tick so the counts are exact.
 */
final class ChargerGameTests {

    /** Energy of a test battery; see {@link #registerTestBattery}. */
    private static final int BATTERY_CAPACITY = 10000;

    private ChargerGameTests() {
    }

    static void register() {
        ModGameTests.test("charger_feeds_electric_vehicles", 20, ChargerGameTests::feeds);
        ModGameTests.test("charger_and_batteries", 20, ChargerGameTests::batteries);
    }

    /**
     * The port registers no battery item of its own, as upstream, so with GameTests enabled (only
     * in development runs) a copper ingot becomes one, keeping its energy in the repair cost.
     */
    static void registerTestBattery(IEventBus modBus) {
        modBus.addListener((RegisterCapabilitiesEvent event) -> event.registerItem(Capabilities.Energy.ITEM,
                (stack, access) -> new ItemAccessEnergyHandler(access, DataComponents.REPAIR_COST, BATTERY_CAPACITY),
                Items.COPPER_INGOT));
    }

    private static HeliInfo definition(boolean electric, boolean battery) {
        var info = new HeliInfo(ContentLocation.builtin("charger_fixture"), "charger_fixture.yml", "yaml");
        info.maxFuel = 1000;
        info.maxHp = 1000;
        info.fuelConsumption = 0;
        info.electric = electric;
        info.allowBattery = battery;
        info.seatList.add(new SeatInfo(Vec3.ZERO, false));
        return info;
    }

    private static HeliEntity fixture(GameTestHelper h, HeliInfo definition, double x) {
        var ac = new HeliEntity(ModEntityTypes.HELICOPTER.get(), h.getLevel()) {
            @Override protected AircraftInfo lookupInfo(String name) { return definition; }
            @Override protected void onUpdateAircraft() { }
        };
        ac.setPos(h.absoluteVec(new Vec3(x, 1, 12)));
        ac.setAircraftTypeName("charger_fixture");
        h.getLevel().addFreshEntity(ac);
        return ac;
    }

    private static ChargerBlockEntity charger(GameTestHelper h, BlockPos pos) {
        h.setBlock(pos, ModBlocks.CHARGER.get());
        return (ChargerBlockEntity) h.getBlockEntity(pos, ChargerBlockEntity.class);
    }

    /** One of the charger's updates: ten ticks. */
    private static void update(GameTestHelper h, ChargerBlockEntity charger) {
        for (int i = 0; i < 10; i++) {
            ChargerBlockEntity.serverTick(h.getLevel(), charger.getBlockPos(), charger.getBlockState(), charger);
        }
    }

    private static void done(GameTestHelper h, AircraftEntity... aircraft) {
        for (var ac : aircraft) {
            GameTestSupport.cleanUp(ac);
        }
        h.succeed();
    }

    private static void feeds(GameTestHelper h) {
        BlockPos pos = new BlockPos(12, 1, 12);
        ChargerBlockEntity charger = charger(h, pos);
        EnergyHandler handler = h.getLevel().getCapability(Capabilities.Energy.BLOCK, h.absolutePos(pos), null);
        check(h, handler != null, "the charger has an energy capability");
        check(h, EnergyCompat.insert(handler, 150000) == 100000 && charger.getEnergy() == 100000,
                "it takes energy up to its capacity: " + charger.getEnergy());
        check(h, EnergyCompat.extract(handler, 500) == 0, "nothing takes energy out of it");

        charger.setEnergy(1000);
        var electric = fixture(h, definition(true, false), 15);
        var fuelled = fixture(h, definition(false, false), 9);
        var far = fixture(h, definition(true, false), 22);
        update(h, charger);
        check(h, electric.getFuel() == 256 && charger.getEnergy() == 744,
                "an electric vehicle in range gets the transfer rate: " + electric.getFuel() + ", " + charger.getEnergy());
        check(h, fuelled.getFuel() == 0, "a fuelled vehicle gets nothing");
        check(h, far.getFuel() == 0, "a vehicle out of range gets nothing");

        electric.setFuel(900);
        update(h, charger);
        check(h, electric.getFuel() == 1000 && charger.getEnergy() == 644,
                "a nearly full store takes what it has room for: " + charger.getEnergy());

        var second = fixture(h, definition(true, false), 13);
        electric.setFuel(0);
        update(h, charger);
        check(h, electric.getFuel() + second.getFuel() == 256 && charger.getEnergy() == 388,
                "vehicles share one budget per update: " + electric.getFuel() + " + " + second.getFuel());

        charger.setEnergy(0);
        electric.setFuel(0);
        second.setFuel(0);
        update(h, charger);
        check(h, electric.getFuel() == 0 && second.getFuel() == 0, "an empty charger gives nothing");

        GameTestSupport.withConfig(io.github.mcheliceport.config.ServerConfig.CHARGER_RANGE, 0.0, () -> {
            charger.setEnergy(500);
            update(h, charger);
            check(h, electric.getFuel() == 0 && charger.getEnergy() == 500, "range 0 turns the charger off");
        });
        done(h, electric, fuelled, far, second);
    }

    private static void batteries(GameTestHelper h) {
        BlockPos pos = new BlockPos(12, 1, 12);
        ChargerBlockEntity charger = charger(h, pos);
        var vehicle = fixture(h, definition(true, true), 15);
        var noBattery = fixture(h, definition(true, false), 9);
        ItemStack battery = new ItemStack(Items.COPPER_INGOT);
        check(h, EnergyCompat.isBattery(battery), "the test battery has an energy capability");

        var player = GameTestSupport.player(h, GameType.SURVIVAL, new Vec3(12, 1, 10));
        check(h, new AircraftMenu(0, player.getInventory(), vehicle).getSlot(AircraftEntity.SLOT_FUEL_IN).mayPlace(battery),
                "a vehicle that takes batteries accepts one in its fuel slot");
        check(h, !new AircraftMenu(0, player.getInventory(), noBattery).getSlot(AircraftEntity.SLOT_FUEL_IN)
                .mayPlace(battery), "one that does not refuses it");
        check(h, new AircraftMenu(0, player.getInventory(), vehicle).getSlot(AircraftEntity.SLOT_FUEL_IN)
                .mayPlace(new ItemStack(ModItems.FUEL.get())), "a fuel can is still accepted");

        noBattery.getInventory().setItem(AircraftEntity.SLOT_FUEL_IN, new ItemStack(Items.COPPER_INGOT));
        noBattery.setFuel(1000);
        vehicle.setFuel(1000);
        charger.setEnergy(1000);
        update(h, charger);
        check(h, energy(noBattery.getInventory().getItem(AircraftEntity.SLOT_FUEL_IN)) == 0
                && charger.getEnergy() == 1000, "a vehicle that takes no battery does not charge one");

        vehicle.getInventory().setItem(AircraftEntity.SLOT_FUEL_IN, battery);
        update(h, charger);
        ItemStack held = vehicle.getInventory().getItem(AircraftEntity.SLOT_FUEL_IN);
        check(h, energy(held) == 256 && charger.getEnergy() == 744,
                "a full vehicle's battery takes the overflow: " + energy(held));

        vehicle.setFuel(100);
        for (int i = 0; i < 10; i++) {
            vehicle.tick();
        }
        check(h, vehicle.getFuel() == 356 && energy(held) == 0,
                "the battery tops the vehicle up: " + vehicle.getFuel() + ", " + energy(held));
        done(h, vehicle, noBattery);
    }

    private static int energy(ItemStack stack) {
        EnergyHandler handler = EnergyCompat.handler(stack);
        return handler != null ? handler.getAmountAsInt() : -1;
    }
}
