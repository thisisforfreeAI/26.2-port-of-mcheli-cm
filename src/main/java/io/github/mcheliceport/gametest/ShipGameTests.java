package io.github.mcheliceport.gametest;

import static io.github.mcheliceport.gametest.GameTestSupport.check;

import io.github.mcheliceport.config.CommonConfig;
import io.github.mcheliceport.config.ServerConfig;
import io.github.mcheliceport.content.ContentLocation;
import io.github.mcheliceport.content.ContentType;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.content.aircraft.SeatInfo;
import io.github.mcheliceport.content.aircraft.ShipInfo;
import io.github.mcheliceport.entity.ship.ShipEntity;
import io.github.mcheliceport.entity.weapon.BaseBulletEntity;
import io.github.mcheliceport.entity.weapon.TorpedoEntity;
import io.github.mcheliceport.item.AircraftItem;
import io.github.mcheliceport.net.AircraftMotionPayload;
import io.github.mcheliceport.net.HeliControlPayload;
import io.github.mcheliceport.net.ModNetwork;
import io.github.mcheliceport.net.PlaneControlPayload.PartSwitch;
import io.github.mcheliceport.net.PlayerControlSwitches;
import io.github.mcheliceport.net.ShipControlPayload;
import io.github.mcheliceport.net.WeaponControlPayload;
import io.github.mcheliceport.registry.ModEntityTypes;
import io.github.mcheliceport.registry.ModItems;
import io.github.mcheliceport.weapon.WeaponSet;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/** Ship placement, real water physics, crew weapons, persistence and authorization. */
final class ShipGameTests {
    private ShipGameTests() { }

    static void register() {
        ModGameTests.test("ships_stock_place_mount_save", 30, ShipGameTests::stockShips);
        ModGameTests.test("ship_float_steer_stop", 30, ShipGameTests::floatSteerStop);
        ModGameTests.test("ship_ground_restrictions", 30, ShipGameTests::groundRestrictions);
        ModGameTests.test("ship_authority_and_controls", 30, ShipGameTests::authority);
        ModGameTests.test("ship_crew_weapon", 60, ShipGameTests::crewWeapon);
        ModGameTests.test("ship_addon_torpedo", 60, ShipGameTests::torpedo);
        ModGameTests.test("ship_item_round_trip", 30, ShipGameTests::itemRoundTrip);
    }

    private static AircraftItem item(String name) {
        return (AircraftItem) ModItems.contentItems().stream()
                .filter(i -> i.type() == ContentType.SHIP && i.contentName().equals(name))
                .findFirst().orElseThrow().item().get();
    }

    private static ShipEntity spawn(GameTestHelper helper, String name, Vec3 relative) {
        Vec3 p = helper.absoluteVec(relative);
        ShipEntity ship = (ShipEntity) item(name).createAircraft(helper.getLevel(), p.x, p.y, p.z);
        check(helper, ship != null, "created " + name);
        ship.initRotationYaw(0);
        helper.getLevel().addFreshEntity(ship);
        return ship;
    }

    private static Player pilot(GameTestHelper helper, ShipEntity ship) {
        Player player = GameTestSupport.player(helper, GameType.SURVIVAL, new Vec3(12, 10, 10));
        check(helper, player.startRiding(ship, true, true), "pilot boarded");
        ship.setFuel(ship.getMaxFuel());
        return player;
    }

    private static ShipControlPayload controls(ShipEntity ship, boolean up, boolean down, boolean left) {
        return new ShipControlPayload(HeliControlPayload.switchesOnly(ship.getId(), PlayerControlSwitches.NONE
                .with(PlayerControlSwitches.THROTTLE_UP, up).with(PlayerControlSwitches.THROTTLE_DOWN, down)
                .with(PlayerControlSwitches.MOVE_LEFT, left)), PartSwitch.NONE, PartSwitch.NONE);
    }

    private static ShipEntity roundTrip(GameTestHelper helper, ShipEntity ship) {
        TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
        check(helper, ship.save(out), "ship saved");
        Entity loaded = EntityType.loadEntityRecursive(TagValueInput.create(ProblemReporter.DISCARDING,
                helper.getLevel().registryAccess(), out.buildResult()), helper.getLevel(), EntitySpawnReason.LOAD, e -> e);
        check(helper, loaded instanceof ShipEntity, "loaded functioning ship entity");
        return (ShipEntity) loaded;
    }

    private static void stockShips(GameTestHelper helper) {
        int count = 0;
        for (ModItems.ContentItem content : ModItems.contentItems()) {
            if (content.type() != ContentType.SHIP) { continue; }
            AircraftItem item = (AircraftItem) content.item().get();
            ShipInfo info = (ShipInfo) item.getAircraftInfo();
            if (!ContentLocation.BUILTIN_DOMAIN.equals(info.getLocation().addonDomain())) { continue; }
            Vec3 p = helper.absoluteVec(new Vec3(12.5, 20, 12.5));
            ShipEntity ship = (ShipEntity) item.onTileClick(helper.getLevel(), 90, (int) p.x, (int) p.y - 1, (int) p.z);
            check(helper, ship != null && item.isPlaceable(), info.name + ": item placement");
            helper.getLevel().addFreshEntity(ship);
            ship.tick();
            Player pilot = pilot(helper, ship);
            check(helper, ship.getShipInfo() == info && ship.getSeats().length == ship.getSeatNum(),
                    info.name + ": content and crew seats");
            if (ship.getSeatNum() > 0) {
                Player crew = GameTestSupport.player(helper, GameType.SURVIVAL, new Vec3(10, 20, 10));
                check(helper, crew.startRiding(ship.getSeats()[0], true, true), info.name + ": crew boarded");
                check(helper, ship.getSeatIdByEntity(crew) == 1, "crew identity");
                crew.stopRiding();
            }
            pilot.stopRiding();
            ship.setFuel(321);
            ship.setDamageTaken(17);
            ship.setCurrentThrottle(0.42);
            ship.getInventory().setItem(0, new ItemStack(ModItems.FUEL.get()));
            ShipEntity copy = roundTrip(helper, ship);
            check(helper, copy.getAircraftTypeName().equals(info.name) && copy.getShipInfo() == info,
                    info.name + ": content survives loading");
            check(helper, copy.getFuel() == 321 && copy.getDamageTaken() == 17
                    && Math.abs(copy.getCurrentThrottle() - 0.42) < 1.0E-9, "fuel, damage and throttle saved");
            check(helper, copy.getInventory().getItem(0).is(ModItems.FUEL.get()), "fuel inventory saved");
            GameTestSupport.cleanUp(copy);
            GameTestSupport.cleanUp(ship);
            count++;
        }
        check(helper, count == 5, "five stock ships, got " + count);
        helper.succeed();
    }

    private static void pool(GameTestHelper helper) {
        for (int x = 0; x < 24; x++) {
            for (int z = 0; z < 24; z++) {
                for (int y = 1; y <= 8; y++) { helper.setBlock(x, y, z, Blocks.WATER); }
            }
        }
    }

    /** Synchronous ticks isolate config changes and prevent other tests seeing an altered server mode. */
    private static void floatSteerStop(GameTestHelper helper) {
        pool(helper);
        ShipEntity ship = spawn(helper, "zodiac", new Vec3(12.5, 9, 5.5));
        Player pilot = pilot(helper, ship);
        for (int t = 0; t < 160; t++) { ship.tick(); }
        double surfaceY = ship.getY();
        check(helper, surfaceY > helper.absoluteVec(new Vec3(0, 8, 0)).y && surfaceY < helper.absoluteVec(new Vec3(0, 10, 0)).y,
                "floats at the water surface: " + surfaceY);
        Vec3 start = ship.position();
        ModNetwork.applyShipControl(ship, pilot, controls(ship, true, false, true));
        for (int t = 0; t < 20; t++) {
            ship.setAngles(0, 0, 0.05F);
            ship.tick();
        }
        check(helper, ship.position().subtract(start).horizontalDistance() > 0.5, "accelerates on water");
        check(helper, ship.getYaw() < -5, "left steering turns the hull: " + ship.getYaw());
        ModNetwork.applyShipControl(ship, pilot, controls(ship, false, true, false));
        for (int t = 0; t < 120; t++) { ship.tick(); }
        check(helper, ship.getCurrentThrottle() == 0 && ship.getDeltaMovement().horizontalDistance() < 0.02,
                "brakes and slows to rest");
        check(helper, Math.abs(ship.getY() - surfaceY) < 1.0, "remains afloat while moving and stopping");
        check(helper, ship.getDamageTaken() == 0 && pilot.getHealth() == pilot.getMaxHealth(), "no water/ground damage");
        ship.setDamageTaken(ship.getMaxHP());
        ship.destroyAircraft();
        check(helper, !ship.canFloatWater(), "destroyed hull loses buoyancy");
        GameTestSupport.cleanUp(ship);
        helper.succeed();
    }

    private static void groundRestrictions(GameTestHelper helper) {
        ShipEntity ship = spawn(helper, "zodiac", new Vec3(12.5, 1, 12.5));
        Player pilot = pilot(helper, ship);
        ModNetwork.applyShipControl(ship, pilot, controls(ship, true, false, true));
        Vec3 start = ship.position();
        for (int t = 0; t < 40; t++) {
            ship.setAngles(0, 0, 0.05F);
            ship.tick();
        }
        check(helper, ship.position().subtract(start).horizontalDistance() < 1.0E-6, "stock ship cannot drive on land");
        check(helper, Math.abs(ship.getYaw()) < 1.0E-6, "stock ship cannot turn on land");
        GameTestSupport.cleanUp(ship);
        helper.succeed();
    }

    @SuppressWarnings("removal")
    private static void authority(GameTestHelper helper) {
        ShipEntity ship = spawn(helper, "zodiac", new Vec3(12.5, 20, 12.5));
        ServerPlayer pilot = helper.makeMockServerPlayerInLevel();
        Player stranger = GameTestSupport.player(helper, GameType.SURVIVAL, new Vec3(10, 20, 10));
        try {
            check(helper, pilot.startRiding(ship, true, true), "real player boarded");
            ship.setFuel(ship.getMaxFuel());
            ModNetwork.applyShipControl(ship, stranger, controls(ship, true, false, false));
            check(helper, !ship.throttleUp, "stranger cannot steer");
            ModNetwork.applyShipControl(ship, pilot, new ShipControlPayload(
                    HeliControlPayload.switchesOnly(ship.getId() + 1, PlayerControlSwitches.NONE
                            .with(PlayerControlSwitches.THROTTLE_UP, true)), PartSwitch.ON, PartSwitch.ON));
            check(helper, !ship.throttleUp, "wrong vehicle id rejected");
            ModNetwork.applyShipControl(ship, pilot, controls(ship, true, false, false));
            check(helper, ship.throttleUp && ship.isClientAuthoritative() && ship.followsPilot(), "pilot controls client mode");
            ModNetwork.applyPilotMotion(ship, new AircraftMotionPayload(ship.getId(), 0.1, 0.1, 0.2, 0.8F));
            Vec3 start = ship.position();
            ship.tick();
            check(helper, ship.position().equals(start), "server follows pilot without simulating a second movement");
            ship.move(MoverType.PLAYER, new Vec3(100, 5, 0));
            check(helper, ship.position().subtract(start).horizontalDistance() <= ship.getMaxSpeedForChecks() + 1.0E-6,
                    "impossible pilot movement capped");
            Vec3 velocity = ship.getDeltaMovement();
            ModNetwork.applyPilotMotion(ship, new AircraftMotionPayload(ship.getId(), Double.NaN, 0, 0, 1));
            check(helper, ship.getDeltaMovement().equals(velocity), "non-finite motion refused");
            GameTestSupport.withConfig(ServerConfig.SERVER_AUTHORITATIVE_MOVEMENT, true, () -> {
                check(helper, ship.simulates() && !ship.isClientAuthoritative(), "server authority mode");
                ship.setDeltaMovement(Vec3.ZERO);
                Vec3 before = ship.position();
                ship.tick();
                check(helper, ship.position().distanceTo(before) > 0.001, "server runs real ship physics");
                Vec3 after = ship.getDeltaMovement();
                ModNetwork.applyPilotMotion(ship, new AircraftMotionPayload(ship.getId(), 0.4, 0.4, 0.4, 1));
                check(helper, ship.getDeltaMovement().equals(after), "client cannot overwrite server velocity");
            });
            GameTestSupport.withConfig(CommonConfig.ALL_SHIP_SPEED, 0.5, () ->
                    check(helper, Math.abs(ship.getMaxSpeedForChecks() - ship.getShipInfo().speed * 0.75) < 1.0E-6,
                            "ship speed multiplier applies to movement checks"));
        } finally {
            GameTestSupport.cleanUp(ship);
            helper.getLevel().getServer().getPlayerList().remove(pilot);
        }
        helper.succeed();
    }

    private static void fire(ShipEntity ship, Player user) {
        ModNetwork.applyWeaponControl(ship, user, WeaponControlPayload.none(ship.getId())
                .withUseWeapon(0, 0, ship.getX(), ship.getY(), ship.getZ(), user.getYRot(), user.getXRot()));
    }

    private static void crewWeapon(GameTestHelper helper) {
        ShipEntity ship = spawn(helper, "mark5", new Vec3(12.5, 20, 12.5));
        Player pilot = pilot(helper, ship);
        helper.runAfterDelay(2, () -> {
            Player crew = GameTestSupport.player(helper, GameType.SURVIVAL, new Vec3(10, 20, 10));
            check(helper, crew.startRiding(ship.getSeats()[1], true, true), "gunner seat boarded");
            int id = 0;
            WeaponSet ws = ship.getWeapon(id);
            ws.setAmmo(ws.getMagSize());
            ModNetwork.applyWeaponControl(ship, crew, WeaponControlPayload.none(ship.getId()).withSwitchWeapon(id));
            helper.runAfterDelay(25, () -> {
                ws.cooldown = 0;
                int ammo = ws.getAmmo();
                fire(ship, pilot);
                check(helper, ws.getAmmo() == ammo, "pilot cannot fire crew-only weapon");
                crew.setYRot(ship.getYaw() + ship.getAcInfo().getWeaponById(id).defaultYaw);
                fire(ship, crew);
                check(helper, ws.getAmmo() == ammo - 1, "assigned crew consumes ammunition");
                check(helper, !helper.getLevel().getEntitiesOfClass(BaseBulletEntity.class,
                        ship.getBoundingBox().inflate(20), b -> b.shootingAircraft == ship).isEmpty(), "crew projectile launched");
                ship.getPassengers().forEach(Entity::stopRiding);
                GameTestSupport.cleanUp(ship);
                helper.succeed();
            });
        });
    }

    private static void torpedo(GameTestHelper helper) {
        pool(helper);
        ShipInfo fixture = new ShipInfo(ContentLocation.builtin("zodiac"), "ship_torpedo_fixture.yml", "yaml");
        fixture.isFloat = true;
        fixture.seatList.add(new SeatInfo(Vec3.ZERO, false));
        fixture.getOrCreateWeaponSet("mk46").weapons.add(new AircraftInfo.Weapon(0, -1, 2,
                0, -1, 2, 0, 0, true, 0, 0, -180, 180, -90, 90, false));
        try { fixture.validate(); } catch (Exception e) { throw new IllegalStateException(e); }
        ShipEntity ship = new ShipEntity(ModEntityTypes.SHIP.get(), helper.getLevel()) {
            @Override protected AircraftInfo lookupInfo(String name) { return fixture; }
        };
        ship.setAircraftTypeName("zodiac");
        ship.setPos(helper.absoluteVec(new Vec3(12.5, 9, 5.5)));
        helper.getLevel().addFreshEntity(ship);
        Player pilot = pilot(helper, ship);
        helper.runAfterDelay(25, () -> {
            WeaponSet ws = ship.getWeapon(0);
            ws.setAmmo(ws.getMagSize());
            ws.cooldown = 0;
            pilot.setPos(ship.position().add(0, 1, 0));
            pilot.setYRot(0);
            pilot.setXRot(45);
            fire(ship, pilot);
            var torpedoes = helper.getLevel().getEntitiesOfClass(TorpedoEntity.class,
                    ship.getBoundingBox().inflate(20), t -> t.shootingAircraft == ship);
            check(helper, torpedoes.size() == 1 && ws.getAmmo() == ws.getMagSize() - 1,
                    "addon ship launches guided torpedo and consumes a round");
            TorpedoEntity torpedo = torpedoes.getFirst();
            check(helper, torpedo.shootingEntity == pilot && torpedo.accelerationInWater > 0, "owner and water propulsion");
            helper.runAfterDelay(3, () -> {
                check(helper, !torpedo.isRemoved() && torpedo.getDeltaMovement().length() > 0.1, "torpedo travels in water");
                torpedo.discard();
                GameTestSupport.cleanUp(ship);
                helper.succeed();
            });
        });
    }

    private static void itemRoundTrip(GameTestHelper helper) {
        ShipEntity ship = spawn(helper, "zodiac", new Vec3(12.5, 1, 12.5));
        ship.setFuel(456);
        ship.setDamageTaken(23);
        ItemStack stack = new ItemStack(item("zodiac"));
        GameTestSupport.withConfig(CommonConfig.ITEM_FUEL, true, () ->
                GameTestSupport.withConfig(CommonConfig.ITEM_DAMAGE, true, () -> ship.writeDataToItem(stack)));
        ShipEntity copy = spawn(helper, "zodiac", new Vec3(5.5, 1, 5.5));
        GameTestSupport.withConfig(CommonConfig.ITEM_FUEL, true, () ->
                GameTestSupport.withConfig(CommonConfig.ITEM_DAMAGE, true, () -> copy.readDataFromItem(stack)));
        check(helper, copy.getFuel() == 456 && copy.getDamageTaken() == 23, "ship item keeps fuel and damage");
        GameTestSupport.cleanUp(copy);
        GameTestSupport.cleanUp(ship);
        helper.succeed();
    }
}
