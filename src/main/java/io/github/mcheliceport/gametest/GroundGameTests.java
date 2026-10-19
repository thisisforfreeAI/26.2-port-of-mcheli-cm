package io.github.mcheliceport.gametest;

import static io.github.mcheliceport.gametest.GameTestSupport.check;

import io.github.mcheliceport.config.CommonConfig;
import io.github.mcheliceport.config.ServerConfig;
import io.github.mcheliceport.content.ContentLocation;
import io.github.mcheliceport.content.ContentType;
import io.github.mcheliceport.content.aircraft.BoundingBoxInfo;
import io.github.mcheliceport.content.aircraft.TankInfo;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.ground.GroundVehicleEntity;
import io.github.mcheliceport.entity.tank.TankEntity;
import io.github.mcheliceport.entity.tank.TankTerrain;
import io.github.mcheliceport.entity.vehicle.VehicleEntity;
import io.github.mcheliceport.entity.weapon.BaseBulletEntity;
import io.github.mcheliceport.entity.weapon.BulletEntity;
import io.github.mcheliceport.item.AircraftItem;
import io.github.mcheliceport.net.AircraftMotionPayload;
import io.github.mcheliceport.net.GroundControlPayload;
import io.github.mcheliceport.net.HeliControlPayload;
import io.github.mcheliceport.net.ModNetwork;
import io.github.mcheliceport.net.PlayerControlSwitches;
import io.github.mcheliceport.net.WeaponControlPayload;
import io.github.mcheliceport.registry.ModEntityTypes;
import io.github.mcheliceport.registry.ModItems;
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

final class GroundGameTests {
    private GroundGameTests() { }
    static void register() {
        ModGameTests.test("ground_stock_place_mount_save", 30, GroundGameTests::stock);
        ModGameTests.test("tank_drive_brake_reverse_turn", 30, GroundGameTests::drive);
        ModGameTests.test("tank_terrain_contacts", 30, GroundGameTests::terrain);
        ModGameTests.test("tank_terrain_tilt_limit", 30, GroundGameTests::tiltLimit);
        ModGameTests.test("ground_authority_and_controls", 30, GroundGameTests::authority);
        ModGameTests.test("vehicle_stationary_gun", 40, GroundGameTests::stationary);
        ModGameTests.test("tank_turret_weapon", 40, GroundGameTests::tankWeapon);
        ModGameTests.test("ground_era_isolation_persistence", 30, GroundGameTests::era);
    }
    private static GroundVehicleEntity spawn(GameTestHelper h, String name, ContentType type) {
        var item = (AircraftItem) ModItems.contentItems().stream().filter(c -> c.type() == type && c.contentName().equals(name))
                .findFirst().orElseThrow().item().get();
        Vec3 p = h.absoluteVec(new Vec3(12, 1, 12));
        var entity = (GroundVehicleEntity) item.createAircraft(h.getLevel(), p.x, p.y, p.z);
        h.getLevel().addFreshEntity(entity); entity.setFuel(entity.getMaxFuel());
        return entity;
    }
    private static Player pilot(GameTestHelper h, AircraftEntity entity) {
        var p = GameTestSupport.player(h, GameType.SURVIVAL, new Vec3(10, 2, 10));
        check(h, p.startRiding(entity, true, true), "pilot boards"); return p;
    }
    private static GroundControlPayload controls(GroundVehicleEntity entity, boolean up, boolean down, boolean left, boolean brake) {
        return new GroundControlPayload(HeliControlPayload.switchesOnly(entity.getId(), PlayerControlSwitches.NONE
                .with(PlayerControlSwitches.THROTTLE_UP, up).with(PlayerControlSwitches.THROTTLE_DOWN, down)
                .with(PlayerControlSwitches.MOVE_LEFT, left)), brake);
    }
    private static void ticks(GroundVehicleEntity entity, int count) {
        for (int i = 0; i < count; i++) { entity.level().getChunk(entity.blockPosition()); entity.tick(); }
    }
    private static GroundVehicleEntity copy(GameTestHelper h, GroundVehicleEntity entity) {
        var out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, h.getLevel().registryAccess());
        check(h, entity.save(out), "saved");
        return (GroundVehicleEntity) EntityType.loadEntityRecursive(TagValueInput.create(ProblemReporter.DISCARDING,
                h.getLevel().registryAccess(), out.buildResult()), h.getLevel(), EntitySpawnReason.LOAD, e -> e);
    }
    private static void stock(GameTestHelper h) {
        int tanks = 0, vehicles = 0;
        for (var content : ModItems.contentItems()) {
            if (content.type() != ContentType.TANK && content.type() != ContentType.VEHICLE) { continue; }
            var item = (AircraftItem) content.item().get();
            if (!ContentLocation.BUILTIN_DOMAIN.equals(item.getAircraftInfo().getLocation().addonDomain())) { continue; }
            Vec3 p = h.absoluteVec(new Vec3(12, 1, 12));
            var entity = (GroundVehicleEntity) item.onTileClick(h.getLevel(), 0, (int) p.x, (int) p.y - 1, (int) p.z);
            check(h, entity != null && item.isPlaceable(), content.contentName() + " placed");
            h.getLevel().addFreshEntity(entity); entity.tick();
            pilot(h, entity);
            if (entity.getSeatNum() > 0) {
                var crew = GameTestSupport.player(h, GameType.SURVIVAL, new Vec3(11, 1, 11));
                check(h, crew.startRiding(entity.getSeats()[0], true, true), "crew boards");
            }
            entity.setFuel(123); entity.setDamageTaken(7);
            var roundTrip = copy(h, entity);
            check(h, roundTrip.getContentType() == content.type() && roundTrip.getAircraftTypeName().equals(content.contentName())
                    && roundTrip.getFuel() == Math.min(123, entity.getMaxFuel()) && roundTrip.getDamageTaken() == 7, "content, fuel and damage reload");
            ItemStack stack = new ItemStack(item); entity.writeDataToItem(stack);
            var placedAgain = item.createAircraft(h.getLevel(), p.x, p.y, p.z); placedAgain.readDataFromItem(stack);
            check(h, placedAgain.getDamageTaken() == 7, "item round trip");
            GameTestSupport.cleanUp(placedAgain); GameTestSupport.cleanUp(roundTrip); GameTestSupport.cleanUp(entity);
            if (content.type() == ContentType.TANK) { tanks++; } else { vehicles++; }
        }
        check(h, tanks == 12 && vehicles == 18, "all 12 tanks and 18 vehicles checked"); h.succeed();
    }
    private static void drive(GameTestHelper h) {
        for (String name : new String[] {"m1a2", "bnr32"}) {
            var tank = (TankEntity) spawn(h, name, ContentType.TANK);
            // Drive beyond the small test arena and its enclosing barrier walls.
            tank.setPos(tank.position().add(1000, 0, 0));
            var p = pilot(h, tank);
            ticks(tank, 10); Vec3 start = tank.position();
            ModNetwork.applyGroundControl(tank, p, controls(tank, true, false, false, false));
            ticks(tank, 70);
            check(h, tank.position().subtract(start).horizontalDistance() > 3, name + " drives");
            ModNetwork.applyGroundControl(tank, p, controls(tank, true, false, true, false)); ticks(tank, 20);
            check(h, Math.abs(tank.getYaw()) > 3, name + " steers: " + tank.getYaw());
            // Wheeled vehicles retain upstream's .985/.992 rolling grip and coast longer than tracks.
            ModNetwork.applyGroundControl(tank, p, controls(tank, false, false, false, true)); ticks(tank, 240);
            check(h, tank.getCurrentThrottle() == 0 && tank.getDeltaMovement().horizontalDistance() < 0.02,
                    name + " Space brakes to rest: throttle=" + tank.getCurrentThrottle() + " motion=" + tank.getDeltaMovement()
                    + " ground=" + tank.onGround() + " pos=" + tank.position());
            ModNetwork.applyGroundControl(tank, p, controls(tank, false, true, false, false)); ticks(tank, 60);
            double yaw = Math.toRadians(tank.getYaw());
            check(h, tank.throttleBack() > 0 && -tank.motionX() * Math.sin(yaw) + tank.motionZ() * Math.cos(yaw) < -0.01,
                    name + " reverses");
            GameTestSupport.cleanUp(tank);
        }
        h.succeed();
    }
    private static void terrain(GameTestHelper h) {
        var tank = (TankEntity) spawn(h, "m1a2", ContentType.TANK);
        pilot(h, tank);
        for (int x = 8; x <= 16; x++) { for (int z = 13; z <= 18; z++) { h.setBlock(x, 1, z, Blocks.SMOOTH_STONE_SLAB); } }
        ticks(tank, 100);
        check(h, tank.terrain().wheelCount() == tank.getTankInfo().wheels.size() * 2, "all mirrored wheel contacts exist");
        check(h, Float.isFinite(tank.getPitch()) && Float.isFinite(tank.getRoll()) && Math.abs(tank.getPitch()) > 0.1,
                "wheel contacts tilt on uneven ground: " + tank.getPitch() + "," + tank.getRoll());
        GameTestSupport.cleanUp(tank); h.succeed();
    }
    /**
     * A tank whose front wheels hang over a 6-block drop, as over a shell crater, tips no further
     * than TankTerrain's limit instead of falling in nose first.
     */
    private static void tiltLimit(GameTestHelper h) {
        for (int x = 4; x <= 20; x++) { for (int y = 1; y <= 6; y++) { for (int z = 2; z <= 22; z++) { h.setBlock(x, y, z, Blocks.STONE); } } }
        var tank = (TankEntity) spawn(h, "m1a2", ContentType.TANK);
        Vec3 p = h.absoluteVec(new Vec3(12.5, 7, 12.0));
        tank.snapTo(p.x, p.y, p.z, 0.0F, 0.0F);
        tank.setYaw(0.0F);
        float max = 0;
        for (int i = 0; i < 140; i++) {
            if (i == 20) {
                // A shell crater under the front half of the tank.
                for (int x = 8; x <= 17; x++) { for (int y = 1; y <= 6; y++) { for (int z = 12; z <= 21; z++) {
                    if ((x - 12.5) * (x - 12.5) + (y - 6.5) * (y - 6.5) * 0.5 + (z - 16.5) * (z - 16.5) < 16) { h.setBlock(x, y, z, Blocks.AIR); }
                } } }
            }
            tank.tick();
            if (i >= 20) { max = Math.max(max, Math.max(Math.abs(tank.getPitch()), Math.abs(tank.getRoll()))); }
        }
        check(h, max <= TankTerrain.MAX_TILT + 0.5F, "the tank tipped " + max + " degrees into the crater");
        GameTestSupport.cleanUp(tank); h.succeed();
    }

    private static void authority(GameTestHelper h) {
        var tank = (TankEntity) spawn(h, "m1a2", ContentType.TANK); tank.tick();
        Player p = pilot(h, tank);
        Player stranger = GameTestSupport.player(h, GameType.SURVIVAL, new Vec3(3, 1, 3));
        ModNetwork.applyGroundControl(tank, stranger, controls(tank, true, false, false, true));
        check(h, !tank.throttleUp && !tank.getBrake(), "outsider refused");
        var bad = new GroundControlPayload(HeliControlPayload.switchesOnly(tank.getId() + 1,
                PlayerControlSwitches.NONE.with(PlayerControlSwitches.THROTTLE_UP, true)), true);
        ModNetwork.applyGroundControl(tank, p, bad); check(h, !tank.throttleUp && !tank.getBrake(), "wrong entity refused");
        ModNetwork.applyGroundControl(tank, p, controls(tank, true, false, false, false)); check(h, tank.throttleUp, "pilot controls accepted");
        p.stopRiding();
        ServerPlayer networkPilot = h.makeMockServerPlayerInLevel();
        networkPilot.startRiding(tank, true, true);
        for (boolean authoritative : new boolean[] {false, true}) {
            GameTestSupport.withConfig(ServerConfig.SERVER_AUTHORITATIVE_MOVEMENT, authoritative, () -> {
                check(h, tank.followsPilot() != authoritative, "movement authority");
                tank.setDeltaMovement(Vec3.ZERO);
                ModNetwork.applyPilotMotion(tank, new AircraftMotionPayload(tank.getId(), 1000, 1000, 1000, 1));
                check(h, authoritative ? tank.getDeltaMovement().equals(Vec3.ZERO)
                        : tank.getDeltaMovement().horizontalDistance() <= tank.getMaxSpeedForChecks() + 1.0E-6, "velocity bounded");
                ModNetwork.applyPilotMotion(tank, new AircraftMotionPayload(tank.getId(), Double.NaN, 0, 0, 0));
                check(h, Double.isFinite(tank.motionX()), "nonfinite motion refused");
            });
        }
        GameTestSupport.cleanUp(tank); h.succeed();
    }
    private static void stationary(GameTestHelper h) {
        var gun = (VehicleEntity) spawn(h, "12.7", ContentType.VEHICLE);
        Player p = pilot(h, gun); ticks(gun, 5);
        Vec3 start = gun.position(); float hull = gun.getYaw();
        ModNetwork.applyGroundControl(gun, p, controls(gun, true, false, true, false)); ticks(gun, 20);
        check(h, gun.position().subtract(start).horizontalDistance() < 0.01 && gun.getYaw() == hull, "stock gun cannot drive or rotate chassis");
        h.runAfterDelay(25, () -> {
            p.setYRot(90); p.setXRot(-10);
            var ws = gun.getWeapon(0); ws.setAmmo(ws.getMagSize()); ws.cooldown = 0;
            ModNetwork.applyWeaponControl(gun, p, WeaponControlPayload.none(gun.getId()).withUseWeapon(0, 0,
                    gun.getX(), gun.getY(), gun.getZ(), 90, -10));
            var shots = h.getLevel().getEntitiesOfClass(BaseBulletEntity.class, gun.getBoundingBox().inflate(20), b -> b.shootingAircraft == gun);
            check(h, !shots.isEmpty() && shots.getFirst().getDeltaMovement().x < -0.1, "gun fires along independent world aim");
            check(h, gun.getYaw() == hull, "firing does not rotate chassis");
            GameTestSupport.cleanUp(gun); h.succeed();
        });
    }
    private static void tankWeapon(GameTestHelper h) {
        var tank = (TankEntity) spawn(h, "m1a2", ContentType.TANK);
        Player p = pilot(h, tank); tank.tick();
        h.runAfterDelay(25, () -> {
            p.setYRot(70); p.setXRot(-10);
            for (int i = 0; i < 200; i++) { tank.weapons().updateWeaponsRotation(); }
            var ws = tank.getWeapon(0); ws.setAmmo(ws.getMagSize()); ws.cooldown = ws.reloadCooldown = 0;
            ModNetwork.applyWeaponControl(tank, p, WeaponControlPayload.none(tank.getId()).withUseWeapon(0, 0,
                    tank.getX(), tank.getY(), tank.getZ(), 70, -10));
            check(h, ws.getAmmo() == ws.getMagSize() - 1, "tank cannon consumes ammo: " + ws.getAmmo()
                    + "/" + ws.getMagSize() + " selected=" + tank.weapons().getCurrentWeaponID(p));
            check(h, !h.getLevel().getEntitiesOfClass(BaseBulletEntity.class, tank.getBoundingBox().inflate(25), b -> b.shootingAircraft == tank).isEmpty(), "tank projectile launches");
            check(h, Math.abs(tank.weapons().getCurrentWeaponShotYaw(p) - 70) < 1, "turret aims independently of hull");
            GameTestSupport.cleanUp(tank); h.succeed();
        });
    }
    private static void era(GameTestHelper h) {
        TankInfo fixture = new TankInfo(ContentLocation.builtin("m1a2"), "era_fixture.yml", "yaml");
        fixture.maxHp = 1000; fixture.armorMinDamage = 0; fixture.armorDamageFactor = 1;
        var box = new BoundingBoxInfo(4, 1, 0, 1, 1, 1); box.isERA = true; box.eraMinDamage = 5;
        fixture.extraBoundingBox.add(box);
        class EraTank extends TankEntity {
            EraTank() { super(ModEntityTypes.TANK.get(), h.getLevel()); }
            @Override protected io.github.mcheliceport.content.aircraft.AircraftInfo lookupInfo(String name) { return fixture; }
            void restore(net.minecraft.world.level.storage.ValueInput input) { this.readAdditionalSaveData(input); }
        }
        class ProbeBullet extends BulletEntity {
            ProbeBullet() { super(ModEntityTypes.BULLET.get(), h.getLevel()); }
            Entity trace(Vec3 from, Vec3 to) {
                Vec3 delta = to.subtract(from);
                this.setPos(from);
                return this.findEntityOnPath(from, to, delta.x, delta.y, delta.z);
            }
        }
        EraTank a = new EraTank();
        a.setAircraftTypeName("m1a2"); a.setPos(h.absoluteVec(new Vec3(12, 1, 12))); h.getLevel().addFreshEntity(a);
        a.tick();
        var tile = a.getHitBoxes()[0];
        Vec3 center = tile.getBoundingBox().getCenter();
        var bullet = new ProbeBullet();
        check(h, bullet.trace(center.add(0, 0, -3), center.add(0, 0, 3)) == tile, "projectile finds tile beyond the main hull box");
        a.hurtFromHitBox(h.getLevel(), a.getHitBoxes()[0], h.getLevel().damageSources().generic(), 3);
        check(h, a.isEraActive(0), "small damage leaves tile intact"); a.timeSinceHit = 0;
        a.hurtFromHitBox(h.getLevel(), a.getHitBoxes()[0], h.getLevel().damageSources().generic(), 20);
        check(h, !a.isEraActive(0) && a.getEraState().equals("0"), "qualifying hit pops tile");
        a.timeSinceHit = 0;
        int damage = a.getDamageTaken();
        check(h, !tile.hurtServer(h.getLevel(), h.getLevel().damageSources().generic(), 20)
                && a.getDamageTaken() == damage, "popped tile cannot receive another hit");
        check(h, bullet.trace(center.add(0, 0, -3), center.add(0, 0, 3)) != tile, "projectiles ignore popped tile");
        check(h, fixture.extraBoundingBox.getFirst() == box && box.isERA, "definition remains shared immutable configuration");
        var saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, h.getLevel().registryAccess()); a.save(saved);
        check(h, saved.buildResult().getString("AcERAState").orElse("").equals("0"), "popped tile saved");
        EraTank restored = new EraTank();
        restored.restore(TagValueInput.create(ProblemReporter.DISCARDING, h.getLevel().registryAccess(), saved.buildResult()));
        check(h, !restored.isEraActive(0), "popped state restored from world data");
        EraTank fresh = new EraTank(); fresh.setAircraftTypeName("m1a2");
        check(h, fresh.isEraActive(0), "another entity using the same definition keeps its tile");
        GameTestSupport.cleanUp(a); GameTestSupport.cleanUp(restored); GameTestSupport.cleanUp(fresh); h.succeed();
    }
}
