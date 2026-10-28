package io.github.mcheliceport.gametest;

import static io.github.mcheliceport.gametest.GameTestSupport.check;

import io.github.mcheliceport.config.ServerConfig;
import io.github.mcheliceport.content.ContentLocation;
import io.github.mcheliceport.content.ContentType;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.content.aircraft.CrewRole;
import io.github.mcheliceport.content.aircraft.HeliInfo;
import io.github.mcheliceport.content.aircraft.SeatInfo;
import io.github.mcheliceport.damage.ComponentHealth;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.aircraft.CrewRoles;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.groundcrew.GroundCrew;
import io.github.mcheliceport.item.AircraftItem;
import io.github.mcheliceport.registry.ModBlocks;
import io.github.mcheliceport.registry.ModEntityTypes;
import io.github.mcheliceport.registry.ModItems;
import io.github.mcheliceport.weapon.WeaponSet;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * M10c ground crew: a repair pad repairs, refuels and rearms a vehicle standing still on it from a
 * supply vehicle nearby, and a stocked ammo cart rearms the emptiest weapon, faster with its loader,
 * runs dry and restocks on a pad. High above the structure, as stock supply ranges are long.
 */
final class GroundCrewGameTests {

    private static final int Y = 160;

    private GroundCrewGameTests() {
    }

    static void register() {
        ModGameTests.test("ground_crew_pad_services", 100, GroundCrewGameTests::padServices);
        ModGameTests.test("ground_crew_ammo_cart_stock", 30, GroundCrewGameTests::cartStock);
    }

    private static void pad(GameTestHelper h, int x, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                h.setBlock(new BlockPos(x + dx, Y - 1, z + dz), Blocks.STONE);
                h.setBlock(new BlockPos(x + dx, Y, z + dz), ModBlocks.REPAIR_PAD.get());
            }
        }
    }

    private static void clear(GameTestHelper h, int x, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                h.setBlock(new BlockPos(x + dx, Y - 1, z + dz), Blocks.AIR);
                h.setBlock(new BlockPos(x + dx, Y, z + dz), Blocks.AIR);
            }
        }
    }

    /** A supply vehicle whose own reach is half a block, so only the pad can pass its fuel and ammo on. */
    private static HeliEntity supplier(GameTestHelper h, Vec3 relative) {
        HeliInfo def = new HeliInfo(ContentLocation.builtin("pad_supplier"), "pad_supplier.yml", "yaml");
        def.maxHp = 1000;
        def.fuelSupplyRange = 0.5F;
        def.fuelSupplyInfinite = true;
        def.fuelSupplyType = "mch_fuel";
        def.ammoSupplyRange = 0.5F;
        def.ammoSupplyStock = 100;
        def.seatList.add(new SeatInfo(Vec3.ZERO, false));
        HeliEntity ac = new HeliEntity(ModEntityTypes.HELICOPTER.get(), h.getLevel()) {
            @Override protected AircraftInfo lookupInfo(String name) { return def; }
            @Override protected void onUpdateAircraft() { }
        };
        ac.setPos(h.absoluteVec(relative));
        ac.setAircraftTypeName("pad_supplier");
        h.getLevel().addFreshEntity(ac);
        ac.tick();
        return ac;
    }

    private static void ticks(AircraftEntity ac, int count) {
        for (int i = 0; i < count; i++) {
            ac.tick();
        }
    }

    private static void empty(AircraftEntity ac) {
        for (int i = 0; i < ac.getWeaponNum(); i++) {
            WeaponSet ws = ac.getWeapon(i);
            if (ws.getInfo() != null) {
                ws.setAmmo(0);
                ws.setReserveAmmo(0);
            }
        }
    }

    private static int rounds(AircraftEntity ac) {
        int n = 0;
        for (int i = 0; i < ac.getWeaponNum(); i++) {
            n += ac.getWeapon(i).getAmmo() + ac.getWeapon(i).getRestAllAmmoNum();
        }
        return n;
    }

    private static void padServices(GameTestHelper h) {
        pad(h, 12, 12);
        HeliEntity heli = GameTestSupport.spawn(h, "ah-64", new Vec3(12.5, Y + 0.07, 12.5), 0.0F);
        HeliEntity supply = supplier(h, new Vec3(17.5, Y + 0.07, 12.5));
        check(h, supply.getAmmoStock() == 100, "the supplier starts with a full stock: " + supply.getAmmoStock());
        heli.tick();
        check(h, GroundCrew.pad(heli) != null, "the helicopter stands over the pad's centre");
        check(h, GroundCrew.stillAndOff(heli), "with its engine off and standing still");
        heli.setDamageTaken(200);
        heli.setComponentHealth(ComponentHealth.withLevel(ComponentHealth.FULL, 0, 100));
        heli.setFuel(0);
        empty(heli);
        supply.setAmmoStock(100);
        int stock = supply.getAmmoStock();

        ticks(heli, 20);
        check(h, heli.getDamageTaken() <= 190, "the pad repairs 10 HP a second: " + heli.getDamageTaken());
        check(h, ComponentHealth.level(heli.getComponentHealth(), 0) > 100, "components first, through the M9 repair");
        check(h, heli.getFuel() > 0, "a fuel supplier near the pad refuels it");
        int given = rounds(heli);
        check(h, given > 0 && supply.getAmmoStock() == stock - given, "an ammo supplier near the pad rearms it from its stock");
        int flags = heli.getServiceFlags();
        check(h, (flags & GroundCrew.REPAIR) != 0 && (flags & GroundCrew.REARM) != 0 && (flags & GroundCrew.REFUEL) != 0,
                "the riders see repair, rearm and refuel: " + flags);

        GameTestSupport.withConfig(ServerConfig.GROUND_CREW, false, () -> {
            int damage = heli.getDamageTaken();
            ticks(heli, 20);
            check(h, heli.getDamageTaken() == damage, "switched off, the pad does nothing");
        });

        heli.setCurrentThrottle(0.5);
        check(h, !GroundCrew.stillAndOff(heli), "with the engine running it is not serviced");
        heli.setCurrentThrottle(0.0);

        clear(h, 12, 12);
        h.setBlock(new BlockPos(12, Y - 1, 12), Blocks.STONE);
        int damage = heli.getDamageTaken();
        ticks(heli, 20);
        check(h, GroundCrew.pad(heli) == null && heli.getDamageTaken() == damage, "off a pad, no repair");
        GameTestSupport.cleanUp(supply);
        // The HUD flags last SHOW_TICKS of game time.
        h.runAfterDelay(GroundCrew.SHOW_TICKS + 5, () -> {
            check(h, heli.getServiceFlags() == 0, "and the HUD line clears: " + heli.getServiceFlags());
            GameTestSupport.cleanUp(heli);
            h.succeed();
        });
    }

    private static AircraftEntity cart(GameTestHelper h, Vec3 relative) {
        AircraftItem item = (AircraftItem) ModItems.contentItems().stream()
                .filter(c -> c.type() == ContentType.VEHICLE && c.contentName().equals("ammo_cart"))
                .findFirst().orElseThrow().item().get();
        Vec3 p = h.absoluteVec(relative);
        AircraftEntity cart = item.createAircraft(h.getLevel(), p.x, p.y, p.z);
        h.getLevel().addFreshEntity(cart);
        cart.tick();
        return cart;
    }

    /** Ticks the cart 20 times and counts the ticks its stock went down. */
    private static int rearms(AircraftEntity cart) {
        int n = 0;
        for (int i = 0; i < 20; i++) {
            int before = cart.getAmmoStock();
            cart.tick();
            if (cart.getAmmoStock() < before) {
                n++;
            }
        }
        return n;
    }

    private static void cartStock(GameTestHelper h) {
        h.setBlock(new BlockPos(6, Y - 1, 12), Blocks.STONE);
        h.setBlock(new BlockPos(10, Y - 1, 12), Blocks.STONE);
        AircraftEntity cart = cart(h, new Vec3(6.5, Y, 12.5));
        // Upstream's ground probe looks at the block under x + 0.5, z + 0.5.
        HeliEntity heli = GameTestSupport.spawn(h, "ah-64", new Vec3(10.0, Y, 12.0), 0.0F);
        heli.tick();
        AircraftInfo info = cart.getAcInfo();
        check(h, info.ammoSupplyStock == 600 && info.ammoSupplyRange == 8.0F, "the ammo cart carries 600 rounds for 8 blocks");
        check(h, cart.getAmmoStock() == 600, "a new cart is full");
        check(h, CrewRoles.role(info, 0) == CrewRole.LOADER, "its seat is a loader's");
        empty(heli);

        int n = rearms(cart);
        check(h, n == 1, "one weapon a second: " + n + " stock " + cart.getAmmoStock() + " canSupply " + heli.canSupply()
                + " in range " + cart.getBoundingBox().inflate(8).intersects(heli.getBoundingBox()));
        int first = -1;
        for (int i = 0; i < heli.getWeaponNum(); i++) {
            if (heli.getWeapon(i).getAmmo() + heli.getWeapon(i).getRestAllAmmoNum() > 0) {
                check(h, first < 0, "only one weapon got rounds");
                first = i;
            }
        }
        check(h, 600 - cart.getAmmoStock() == rounds(heli), "the cart pays for every round");

        Player loader = GameTestSupport.player(h, GameType.SURVIVAL, new Vec3(6, Y, 10));
        check(h, loader.startRiding(cart, true, true), "a loader boards the cart");
        check(h, rearms(cart) == 2, "twice as fast with its loader");
        loader.stopRiding();

        cart.setAmmoStock(0);
        int had = rounds(heli);
        rearms(cart);
        check(h, rounds(heli) == had, "an empty cart rearms nothing");

        GameTestSupport.cleanUp(heli);
        pad(h, 6, 12);
        cart.setPos(h.absoluteVec(new Vec3(6.5, Y + 0.07, 12.5)));
        cart.setDeltaMovement(Vec3.ZERO);
        ticks(cart, 20);
        check(h, cart.getAmmoStock() >= 60 && (cart.getServiceFlags() & GroundCrew.RESTOCK) != 0,
                "on a pad it restocks a tenth a second: " + cart.getAmmoStock());
        GameTestSupport.cleanUp(cart);
        h.succeed();
    }
}
