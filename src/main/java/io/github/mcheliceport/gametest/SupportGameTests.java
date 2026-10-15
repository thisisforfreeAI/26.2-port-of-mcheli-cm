package io.github.mcheliceport.gametest;

import static io.github.mcheliceport.gametest.GameTestSupport.check;

import io.github.mcheliceport.config.ServerConfig;
import io.github.mcheliceport.content.ContentLocation;
import io.github.mcheliceport.content.ContentType;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.content.aircraft.HeliInfo;
import io.github.mcheliceport.content.aircraft.SeatInfo;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.item.AircraftItem;
import io.github.mcheliceport.registry.ModEntityTypes;
import io.github.mcheliceport.registry.ModItems;
import java.util.Map;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Real server ticks, with stationary addon fixtures for precise range and cadence checks. */
final class SupportGameTests {
    private SupportGameTests() { }

    static void register() {
        ModGameTests.test("support_stock_fuel_and_ammo", 30, SupportGameTests::stock);
        ModGameTests.test("support_fuel_cadence_capacity_finite", 30, SupportGameTests::finite);
        ModGameTests.test("support_fuel_types_and_power", 30, SupportGameTests::types);
        ModGameTests.test("support_fuel_range_and_landing", 30, SupportGameTests::range);
        ModGameTests.test("support_fuel_consumption_grace", 30, SupportGameTests::grace);
        ModGameTests.test("support_repair_cadence_and_wrecks", 30, SupportGameTests::repair);
        ModGameTests.test("support_medical_crew", 30, SupportGameTests::medical);
    }

    private static HeliInfo definition() {
        var info = new HeliInfo(ContentLocation.builtin("support_fixture"), "support_fixture.yml", "yaml");
        info.maxFuel = 1000; info.maxHp = 1000; info.fuelConsumption = 5;
        info.seatList.add(new SeatInfo(Vec3.ZERO, false));
        info.seatList.add(new SeatInfo(Vec3.ZERO, false));
        return info;
    }

    private static HeliEntity fixture(GameTestHelper h, HeliInfo definition, double x, double y) {
        var ac = new HeliEntity(ModEntityTypes.HELICOPTER.get(), h.getLevel()) {
            @Override protected AircraftInfo lookupInfo(String name) { return definition; }
            @Override protected void onUpdateAircraft() { }
        };
        ac.setPos(h.absoluteVec(new Vec3(x, y, 12)));
        ac.setAircraftTypeName("support_fixture");
        ac.setFuelType("mch_fuel");
        h.getLevel().addFreshEntity(ac);
        return ac;
    }

    private static AircraftEntity stock(GameTestHelper h, String name, ContentType type, double x) {
        var item = (AircraftItem) ModItems.contentItems().stream()
                .filter(c -> c.type() == type && c.contentName().equals(name)).findFirst().orElseThrow().item().get();
        // Stock supply radii exceed the spacing between concurrent GameTest structures.
        var p = h.absoluteVec(new Vec3(x, 100, 12));
        var ac = item.createAircraft(h.getLevel(), p.x, p.y, p.z);
        h.getLevel().addFreshEntity(ac);
        return ac;
    }

    private static void ticks(AircraftEntity ac, int count) {
        for (int i = 0; i < count; i++) { ac.tick(); }
    }

    private static void done(GameTestHelper h, AircraftEntity... entities) {
        for (var ac : entities) { GameTestSupport.cleanUp(ac); }
        h.succeed();
    }

    private static void stock(GameTestHelper h) {
        var truck = stock(h, "fuel_truck", ContentType.PLANE, 6);
        h.setBlock(15, 99, 12, Blocks.STONE);
        var heli = GameTestSupport.spawn(h, "ah-64", new Vec3(15, 100, 12), 0);
        var box = stock(h, "ammo_box", ContentType.VEHICLE, 21);
        check(h, truck.getAcInfo().fuelSupplyInfinite && truck.getMaxFuel() == 0, "stock truck is an infinite source without its own tank");
        for (boolean serverAuthority : new boolean[] {false, true}) {
            GameTestSupport.withConfig(ServerConfig.SERVER_AUTHORITATIVE_MOVEMENT, serverAuthority, () -> {
                var pilot = GameTestSupport.player(h, GameType.SURVIVAL, new Vec3(14, 100, 12));
                check(h, pilot.startRiding(heli, true, true), "pilot boards in either authority mode");
                heli.setFuel(0);
                int before = heli.weapons().getWeapon(0).getRestAllAmmoNum() + heli.weapons().getWeapon(0).getAmmo();
                ticks(truck, 10); ticks(box, 40);
                check(h, heli.getFuel() == 30, "stock truck transfers 30 fuel per ten ticks");
                int after = heli.weapons().getWeapon(0).getRestAllAmmoNum() + heli.weapons().getWeapon(0).getAmmo();
                check(h, after > before, "stock ammo box supplies ammunition alongside fuel");
                pilot.stopRiding();
            });
        }
        h.setBlock(15, 99, 12, Blocks.AIR);
        done(h, truck, heli, box);
    }

    private static void finite(GameTestHelper h) {
        var info = definition(); info.fuelSupplyRange = 5;
        var source = fixture(h, info, 8, 1);
        var target = fixture(h, definition(), 12, 1);
        source.setFuel(17); target.setFuel(990);
        source.tick();
        check(h, target.getFuel() == 1000 && source.getFuel() == 7, "target capacity caps transfer and source pays");
        target.setFuel(0); ticks(source, 9);
        check(h, target.getFuel() == 0, "no transfer between ten-tick intervals");
        source.tick();
        check(h, target.getFuel() == 30 && source.getFuel() == 0, "upstream transfers a full increment before clamping a nearly empty source");
        ticks(source, 10);
        check(h, target.getFuel() == 30, "empty finite source stops");
        done(h, source, target);
    }

    private static void types(GameTestHelper h) {
        var info = definition(); info.fuelSupplyRange = 5; info.fuelSupplyInfinite = true; info.fuelSupplyType = "addon_fuel";
        var source = fixture(h, info, 8, 1);
        var accepted = definition(); accepted.setFluidType(Map.of("addon_fuel", 1f, "mch_fuel", 1f));
        var target = fixture(h, accepted, 12, 1);
        target.setFuel(10); source.tick();
        check(h, target.getFuel() == 10, "nonempty incompatible fuel is not mixed");
        target.setFuel(0); ticks(source, 10);
        check(h, target.getFuel() == 30 && target.getFuelType().equals("addon_fuel"), "empty compatible tank adopts supply type");
        target.setFuel(0); accepted.setFluidType(Map.of("mch_fuel", 1f)); ticks(source, 10);
        check(h, target.getFuel() == 0, "fuel whitelist rejects unaccepted types");
        info.fuelSupplyInfinite = false; info.electric = true; source.setFuel(500); source.setFuelType("mch_fuel");
        ticks(source, 10);
        check(h, target.getFuel() == 0, "a finite electric source cannot dispense fluid");
        done(h, source, target);
    }

    private static void range(GameTestHelper h) {
        var info = definition(); info.fuelSupplyRange = 4; info.fuelSupplyInfinite = true;
        var source = fixture(h, info, 8, 1);
        var near = fixture(h, definition(), 12, 1);
        var far = fixture(h, definition(), 22, 1);
        var airborne = fixture(h, definition(), 10, 5);
        source.setOnGround(true); source.tick();
        check(h, near.getFuel() == 30 && far.getFuel() == 0, "expanded bounding box limits range");
        check(h, !airborne.canSupply() && airborne.getFuel() == 0, "grounded source requires recipient near ground");
        source.setOnGround(false); ticks(source, 10);
        check(h, airborne.getFuel() == 30, "airborne source can refuel airborne recipient");
        done(h, source, near, far, airborne);
    }

    private static void grace(GameTestHelper h) {
        var info = definition(); info.fuelSupplyRange = 4; info.fuelSupplyInfinite = true;
        var source = fixture(h, info, 8, 1);
        var target = fixture(h, definition(), 12, 1);
        target.setFuel(1000); target.setThrottle(1);
        source.tick();
        ticks(target, 39);
        check(h, target.getFuel() == 1000, "even a full compatible tank has consumption paused");
        ticks(target, 2);
        check(h, target.getFuel() == 995, "consumption resumes after grace expires");
        done(h, source, target);
    }

    private static void repair(GameTestHelper h) {
        var info = definition(); info.repairOtherVehiclesRange = 4; info.repairOtherVehiclesValue = 10;
        var source = fixture(h, info, 8, 5);
        var target = fixture(h, definition(), 12, 5);
        var far = fixture(h, definition(), 22, 5);
        source.setDamageTaken(100); target.setDamageTaken(15); far.setDamageTaken(100);
        source.tick();
        check(h, target.getDamageTaken() == 5 && source.getDamageTaken() == 100 && far.getDamageTaken() == 100,
                "repair is in range, excludes self and does not require landing");
        ticks(source, 19);
        check(h, target.getDamageTaken() == 5, "repair waits twenty ticks");
        source.tick(); check(h, target.getDamageTaken() == 0, "repair clamps damage to zero");
        target.destroyAircraft(); target.setDamageTaken(1000); source.destroyAircraft(); ticks(source, 20);
        check(h, target.getDamageTaken() == 990 && target.isDestroyed(), "upstream repair reduces wreck damage without reviving it, even from a wrecked source");
        done(h, source, target, far);
    }

    private static void medical(GameTestHelper h) {
        var heli = GameTestSupport.spawn(h, "nh90", new Vec3(12, 1, 12), 0);
        heli.tick();
        var pilot = GameTestSupport.player(h, GameType.SURVIVAL, new Vec3(10, 1, 12));
        var crew = GameTestSupport.player(h, GameType.SURVIVAL, new Vec3(11, 1, 12));
        check(h, crew.startRiding(heli.getSeats()[0], true, true), "crew boards medical helicopter");
        heli.tick(); check(h, !crew.hasEffect(MobEffects.REGENERATION), "medical effect requires a pilot");
        check(h, pilot.startRiding(heli, true, true), "pilot boards medical helicopter");
        heli.tick();
        var effect = crew.getEffect(MobEffects.REGENERATION);
        check(h, effect != null && effect.getDuration() == 250 && effect.getAmplifier() == 0 && effect.isAmbient(), "stock medical helicopter applies upstream crew regeneration");
        check(h, !pilot.hasEffect(MobEffects.REGENERATION), "pilot is not a secondary-seat patient");
        crew.removeEffect(MobEffects.REGENERATION);
        crew.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 600, 0)); heli.tick();
        check(h, crew.getEffect(MobEffects.REGENERATION).getDuration() == 600, "longer existing effect is untouched");
        crew.removeEffect(MobEffects.REGENERATION); heli.destroyAircraft(); heli.tick();
        check(h, !crew.hasEffect(MobEffects.REGENERATION), "destroyed medical helicopter cannot heal crew");
        done(h, heli);
    }
}
