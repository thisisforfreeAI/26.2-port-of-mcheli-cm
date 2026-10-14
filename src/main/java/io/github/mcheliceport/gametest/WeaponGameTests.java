package io.github.mcheliceport.gametest;

import static io.github.mcheliceport.gametest.GameTestSupport.AH64;
import static io.github.mcheliceport.gametest.GameTestSupport.check;
import static io.github.mcheliceport.gametest.GameTestSupport.player;
import static io.github.mcheliceport.gametest.GameTestSupport.spawn;

import io.github.mcheliceport.content.ContentRegistries;
import io.github.mcheliceport.content.weapon.WeaponInfo;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.flare.FlareEntity;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.entity.weapon.A10Entity;
import io.github.mcheliceport.entity.weapon.BaseBulletEntity;
import io.github.mcheliceport.entity.weapon.DispensedItemEntity;
import io.github.mcheliceport.entity.weapon.MarkerRocketEntity;
import io.github.mcheliceport.entity.weapon.TorpedoEntity;
import io.github.mcheliceport.net.ModNetwork;
import io.github.mcheliceport.net.WeaponControlPayload;
import io.github.mcheliceport.weapon.WeaponBase;
import io.github.mcheliceport.weapon.WeaponCreator;
import io.github.mcheliceport.weapon.WeaponParam;
import io.github.mcheliceport.weapon.WeaponSet;
import io.github.mcheliceport.weapon.WeaponTypes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Exit criteria E5 and E6 (docs/M5_PLAN.md section 8.3): every weapon type fires and its projectile
 * hits, flares decoy a heat seeker, ammunition is used, reloaded and saved, infinite ammunition
 * stays full, and a destroyed helicopter's explosion hurts mobs around it.
 */
final class WeaponGameTests {

    /** What a type's test waits for. */
    private enum Expect { DAMAGE, SPAWN, FIRE_ONLY }

    /** One stock weapon per type; buckshot, railgun and laser have none, so they borrow the M230's definition. */
    private record Case(String type, String weapon, Expect expect, boolean fromAbove) {
    }

    private static final List<Case> CASES = List.of(
            new Case("machinegun1", "20mmgunpod", Expect.DAMAGE, false),
            new Case("machinegun2", "m230", Expect.DAMAGE, false),
            new Case("buckshot", "m230", Expect.DAMAGE, false),
            new Case("rocket", "hydra70", Expect.DAMAGE, false),
            new Case("aamissile", "aim-9x", Expect.DAMAGE, false),
            new Case("atmissile", "9k114", Expect.DAMAGE, false),
            new Case("asmissile", "agm-65", Expect.DAMAGE, false),
            new Case("tvmissile", "9m114", Expect.DAMAGE, false),
            new Case("bomb", "250kg", Expect.DAMAGE, true),
            new Case("railgun", "m230", Expect.DAMAGE, false),
            new Case("laser", "m230", Expect.DAMAGE, false),
            new Case("torpedo", "mk46", Expect.SPAWN, false),
            new Case("cas", "a10gau8", Expect.SPAWN, false),
            new Case("mkrocket", "hydra70_m264rp", Expect.SPAWN, false),
            new Case("dispenser", "dispenser_fire", Expect.SPAWN, false),
            new Case("dummy", "none", Expect.FIRE_ONLY, false),
            new Case("smoke", "smoke_white", Expect.FIRE_ONLY, false),
            new Case("targetingpod", "targeting_pod_mob", Expect.FIRE_ONLY, false));

    private static final Vec3 SHOOTER = new Vec3(12.5, 1.0, 2.5);
    private static final Vec3 TARGET = new Vec3(12.5, 1.0, 16.5);

    private WeaponGameTests() {
    }

    static void register() {
        ModGameTests.test("weapon_every_stock_weapon_creates", 20, WeaponGameTests::everyStockWeaponCreates);
        for (Case c : CASES) {
            ModGameTests.test("weapon_fires_" + c.type(), 240, helper -> fires(helper, c));
        }
        ModGameTests.test("weapon_heat_seeker_hits_without_flares", 200, helper -> heatSeeker(helper, false));
        ModGameTests.test("weapon_flares_decoy_heat_seeker", 200, helper -> heatSeeker(helper, true));
        ModGameTests.test("weapon_ammo_used_reloaded_and_saved", 200, WeaponGameTests::ammo);
        ModGameTests.test("weapon_infinity_ammo_stays_full", 200, WeaponGameTests::infinityAmmo);
        ModGameTests.test("weapon_destroyed_helicopter_explodes", 60, WeaponGameTests::destroyedExplosion);
    }

    /** §8.2's other half: every stock weapon definition makes a weapon of its type in a level. */
    private static void everyStockWeaponCreates(GameTestHelper helper) {
        List<String> failures = new ArrayList<>();
        for (Map.Entry<String, WeaponInfo> e : ContentRegistries.weapon().entries()) {
            WeaponInfo info = e.getValue();
            if (info.type.isEmpty()) {
                continue;
            }
            WeaponBase w = WeaponCreator.createWeapon(helper.getLevel(), e.getKey(), Vec3.ZERO, 0.0F, 0.0F, null,
                    false);
            if (w == null || w.getInfo() != info) {
                failures.add(e.getKey());
            }
        }
        check(helper, failures.isEmpty(), "weapons not created: " + failures);
        helper.succeed();
    }

    private static Husk target(GameTestHelper helper, Vec3 relative) {
        Husk husk = helper.spawnWithNoFreeWill(EntityTypes.HUSK, relative);
        husk.setPersistenceRequired();
        return husk;
    }

    private static boolean hurt(Husk husk) {
        return !husk.isAlive() || husk.getHealth() < husk.getMaxHealth();
    }

    /** Turns the shooter to look at a point, as a rider's look would aim the weapon. */
    private static void lookAt(Player shooter, Vec3 eye, Vec3 point) {
        Vec3 d = point.subtract(eye);
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
        shooter.setYRot(yaw);
        shooter.setXRot(pitch);
        shooter.yRotO = yaw;
        shooter.xRotO = pitch;
    }

    private static WeaponSet weaponOf(GameTestHelper helper, Case c) {
        WeaponInfo info = ContentRegistries.weapon().get(c.weapon());
        check(helper, info != null, "no stock weapon " + c.weapon());
        WeaponBase w = WeaponTypes.get(c.type()).create(helper.getLevel(), Vec3.ZERO, 0.0F, 0.0F, c.weapon(), info);
        // WeaponCreator's server-side values.
        w.displayName = info.displayName;
        w.power = info.power;
        w.acceleration = info.acceleration;
        w.explosionPower = info.explosion;
        w.explosionPowerInWater = info.explosionInWater;
        w.interval = info.delay;
        w.numMode = info.modeNum;
        w.piercing = info.piercing;
        w.heatCount = info.heatCount;
        w.setLockCountMax(info.lockTime);
        w.delayedInterval = WeaponCreator.adjustInterval(info.delay);
        w.modifyCommonParameters();
        WeaponSet ws = new WeaponSet(w);
        ws.setAmmo(ws.getMagSize());
        ws.setReserveAmmo(info.maxAmmo);
        return ws;
    }

    /** E5: a type's weapon fires at a husk and hurts it, or launches its projectile. */
    private static void fires(GameTestHelper helper, Case c) {
        if (c.type().equals("torpedo")) {
            // A guided torpedo launches only at water under the sight.
            for (int x = 10; x <= 14; x++) {
                for (int z = 3; z <= 22; z++) {
                    helper.setBlock(x, 0, z, Blocks.WATER);
                }
            }
        }
        Husk husk = target(helper, TARGET);
        Vec3 shooterPos = c.fromAbove() ? TARGET.add(0.0, 12.0, 0.0) : SHOOTER;
        Player shooter = player(helper, GameType.SURVIVAL, shooterPos);
        Vec3 eye = shooter.getEyePosition();
        lookAt(shooter, eye, c.fromAbove() ? husk.position() : husk.position().add(0.0, 0.3, 0.0));
        WeaponSet ws = weaponOf(helper, c);
        Runnable release = c.type().equals("cas") ? forceA10Chunks(helper, husk.position()) : () -> { };
        boolean[] firedOnce = new boolean[1];
        helper.onEachTick(() -> {
            ws.update(shooter, true, false);
            if (!ws.canFire() || husk.isRemoved() && c.expect() == Expect.DAMAGE) {
                return;
            }
            WeaponParam prm = new WeaponParam();
            prm.entity = shooter;
            prm.user = shooter;
            prm.setPosAndRot(eye.x, eye.y, eye.z, shooter.getYRot(), shooter.getXRot());
            prm.option1 = husk.getId();
            prm.isInfinity = true;
            if (ws.use(prm)) {
                firedOnce[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            switch (c.expect()) {
                case DAMAGE -> check(helper, hurt(husk), c.weapon() + " (" + c.type() + ") has not hurt the target");
                case SPAWN -> {
                    check(helper, spawned(helper, shooter, c.type()),
                            c.weapon() + " (" + c.type() + ") has not launched its projectile");
                    release.run();
                }
                case FIRE_ONLY -> check(helper, helper.getTick() > 20, "fired for a second without failing");
            }
        });
    }

    /**
     * The A-10 appears 90 blocks from the strike point, on a random side; the test level may not
     * have those chunks loaded, and an entity added to an unloaded chunk is dropped. Keeps the four
     * candidate chunks (and their neighbours) loaded until the A-10 is seen; the returned action
     * releases them.
     */
    private static Runnable forceA10Chunks(GameTestHelper helper, Vec3 target) {
        var level = helper.getLevel();
        List<long[]> forced = new ArrayList<>();
        for (int[] d : new int[][] {{0, -90}, {90, 0}, {0, 90}, {-90, 0}}) {
            int cx = Mth.floor(target.x + d[0]) >> 4;
            int cz = Mth.floor(target.z + d[1]) >> 4;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (level.setChunkForced(cx + dx, cz + dz, true)) {
                        forced.add(new long[] {cx + dx, cz + dz});
                    }
                }
            }
        }
        return () -> forced.forEach(c -> level.setChunkForced((int) c[0], (int) c[1], false));
    }

    private static boolean spawned(GameTestHelper helper, Player shooter, String type) {
        Class<? extends Entity> cls = switch (type) {
            case "torpedo" -> TorpedoEntity.class;
            case "cas" -> A10Entity.class;
            case "mkrocket" -> MarkerRocketEntity.class;
            case "dispenser" -> DispensedItemEntity.class;
            default -> throw new IllegalArgumentException(type);
        };
        AABB box = new AABB(shooter.blockPosition()).inflate(400.0);
        return !helper.getLevel().getEntitiesOfClass(cls, box,
                e -> !(e instanceof BaseBulletEntity b) || b.shootingEntity == shooter).isEmpty();
    }

    /** E6: an AIM-9X fired at a parked AH-64 hits it, unless the AH-64 dispenses flares. */
    private static void heatSeeker(GameTestHelper helper, boolean flares) {
        HeliEntity heli = spawn(helper, AH64, TARGET.add(0.0, 0.0, 2.0), 0.0F);
        Player pilot = player(helper, GameType.SURVIVAL, TARGET.add(0.0, 0.0, 4.0));
        heli.interact(pilot, InteractionHand.MAIN_HAND, Vec3.ZERO);
        Player shooter = player(helper, GameType.SURVIVAL, SHOOTER.add(0.0, 2.0, 0.0));
        Vec3 eye = shooter.getEyePosition();
        Case c = new Case("aamissile", "aim-9x", Expect.DAMAGE, false);
        WeaponSet ws = weaponOf(helper, c);
        helper.runAfterDelay(5, () -> {
            check(helper, heli.getPilot() == pilot, "the pilot boarded");
            check(helper, heli.haveFlare(), "the AH-64 has flares");
            lookAt(shooter, eye, heli.position().add(0.0, 1.5, 0.0));
            WeaponParam prm = new WeaponParam();
            prm.entity = shooter;
            prm.user = shooter;
            prm.setPosAndRot(eye.x, eye.y, eye.z, shooter.getYRot(), shooter.getXRot());
            prm.option1 = heli.getId();
            check(helper, ws.use(prm), "the AIM-9X fired");
            if (flares) {
                check(helper, heli.useFlare(heli.getCurrentFlareType()), "flares out");
            }
        });
        helper.runAfterDelay(150, () -> {
            int damage = heli.getDamageTaken();
            if (flares) {
                check(helper, damage == 0, "the decoyed missile still hit: damage " + damage);
                boolean homingOnHeli = !helper.getLevel().getEntitiesOfClass(BaseBulletEntity.class,
                        heli.getBoundingBox().inflate(64.0), b -> b.targetEntity == heli).isEmpty();
                check(helper, !homingOnHeli, "a missile still homes on the AH-64");
                check(helper, !helper.getLevel().getEntitiesOfClass(FlareEntity.class,
                        heli.getBoundingBox().inflate(64.0)).isEmpty() || damage == 0, "flares were dispensed");
            } else {
                check(helper, damage > 0, "the AIM-9X missed the AH-64");
            }
            pilot.stopRiding();
            GameTestSupport.cleanUp(heli);
            helper.succeed();
        });
    }

    /** The AH-64's Hydra 70 set and its id, selected for the pilot. */
    private static int selectHydra(GameTestHelper helper, HeliEntity heli, Player pilot) {
        for (int id = 0; id < heli.getWeaponNum(); id++) {
            if (heli.getWeapon(id).isName("hydra70") && heli.weapons().getWeaponSeatID(heli.getWeapon(id).getInfo(),
                    heli.getAcInfo().getWeaponById(id)) == 0) {
                ModNetwork.applyWeaponControl(heli, pilot, WeaponControlPayload.none(heli.getId()).withSwitchWeapon(id));
                check(helper, heli.getCurrentWeaponID(pilot) == id, "the pilot selected the Hydra 70");
                return id;
            }
        }
        throw helper.assertionException(net.minecraft.network.chat.Component.literal("no pilot Hydra 70"));
    }

    private static void fire(HeliEntity heli, Player pilot) {
        ModNetwork.applyWeaponControl(heli, pilot, WeaponControlPayload.none(heli.getId())
                .withUseWeapon(0, 0, heli.getX(), heli.getY(), heli.getZ(), heli.getYRot(), 0.0F));
    }

    private static HeliEntity roundTrip(GameTestHelper helper, HeliEntity heli) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
                helper.getLevel().registryAccess());
        check(helper, heli.save(output), "saved");
        CompoundTag tag = output.buildResult();
        Entity loaded = EntityType.loadEntityRecursive(TagValueInput.create(ProblemReporter.DISCARDING,
                helper.getLevel().registryAccess(), tag), helper.getLevel(), EntitySpawnReason.LOAD, e -> e);
        check(helper, loaded instanceof HeliEntity, "loaded a helicopter");
        return (HeliEntity) loaded;
    }

    /** E6: each shot takes a round, an empty magazine reloads from the reserve, and both survive a save. */
    private static void ammo(GameTestHelper helper) {
        HeliEntity heli = spawn(helper, AH64, new Vec3(12.5, 1.0, 12.5), 180.0F);
        Player pilot = player(helper, GameType.SURVIVAL, new Vec3(12.5, 1.0, 9.5));
        heli.interact(pilot, InteractionHand.MAIN_HAND, Vec3.ZERO);
        int[] id = new int[1];
        int[] mag = new int[1];
        helper.runAfterDelay(5, () -> {
            check(helper, heli.getPilot() == pilot, "the pilot boarded");
            check(helper, !heli.isInfinityAmmo(pilot), "ammunition is finite");
            id[0] = selectHydra(helper, heli, pilot);
            // A new vehicle is empty until supplied (upstream too): fill the reserve and load a magazine.
            WeaponSet ws = heli.getWeapon(id[0]);
            ws.setReserveAmmo(ws.getInfo().maxAmmo);
            ws.reloadMag();
        });
        // The switch cooldown runs out, then the pilot fires three rockets.
        helper.runAfterDelay(30, () -> {
            WeaponSet ws = heli.getWeapon(id[0]);
            mag[0] = ws.getMagSize();
            check(helper, ws.getAmmo() == mag[0], "a full magazine: " + ws.getAmmo() + " of " + mag[0]);
            fire(heli, pilot);
            check(helper, ws.getAmmo() == mag[0] - 1, "one round used: " + ws.getAmmo());
        });
        helper.runAfterDelay(60, () -> {
            WeaponSet ws = heli.getWeapon(id[0]);
            HeliEntity copy = roundTrip(helper, heli);
            WeaponSet saved = copy.getWeapon(id[0]);
            check(helper, saved.getAmmo() == ws.getAmmo() && saved.getRestAllAmmoNum() == ws.getRestAllAmmoNum(),
                    "ammunition saved: " + saved.getAmmo() + "/" + saved.getRestAllAmmoNum() + " for "
                            + ws.getAmmo() + "/" + ws.getRestAllAmmoNum());
            copy.getPassengers().forEach(Entity::discard);
            copy.discard();
            // Empty the magazine with rounds in reserve (the Hydra 70's MaxAmmo is one magazine): the set
            // starts its reload.
            ws.setAmmo(1);
            ws.setReserveAmmo(5);
            ws.cooldown = 0;
            fire(heli, pilot);
            check(helper, ws.getAmmo() == 0 && ws.reloadCooldown > 0, "an empty magazine reloads: ammo "
                    + ws.getAmmo() + ", reload " + ws.reloadCooldown);
        });
        helper.succeedWhen(() -> {
            WeaponSet ws = heli.getWeapon(id[0]);
            check(helper, helper.getTick() > 60 && ws.getAmmo() > 0, "the magazine has not reloaded");
            pilot.stopRiding();
            GameTestSupport.cleanUp(heli);
        });
    }

    /** E6: with infinite ammunition the reserve never runs down. */
    private static void infinityAmmo(GameTestHelper helper) {
        HeliEntity heli = spawn(helper, AH64, new Vec3(12.5, 1.0, 12.5), 180.0F);
        Player pilot = player(helper, GameType.SURVIVAL, new Vec3(12.5, 1.0, 9.5));
        heli.interact(pilot, InteractionHand.MAIN_HAND, Vec3.ZERO);
        int[] id = new int[1];
        int[] reserve = new int[1];
        helper.runAfterDelay(5, () -> {
            heli.setCommonStatus(AircraftEntity.STATUS_INFINITY_AMMO, true);
            check(helper, heli.isInfinityAmmo(pilot), "infinite ammunition on");
            id[0] = selectHydra(helper, heli, pilot);
        });
        helper.runAfterDelay(30, () -> {
            WeaponSet ws = heli.getWeapon(id[0]);
            reserve[0] = ws.getRestAllAmmoNum();
            ws.setAmmo(1);
            fire(heli, pilot);
            check(helper, ws.getAmmo() == 0, "the last round fired");
        });
        helper.succeedWhen(() -> {
            WeaponSet ws = heli.getWeapon(id[0]);
            check(helper, helper.getTick() > 30 && ws.getAmmo() == ws.getMagSize(), "the magazine has not refilled");
            check(helper, ws.getAmmo() + ws.getRestAllAmmoNum() >= Math.min(ws.getMagSize(), reserve[0]),
                    "infinite ammunition ran down: " + ws.getAmmo() + "/" + ws.getRestAllAmmoNum());
            pilot.stopRiding();
            GameTestSupport.cleanUp(heli);
        });
    }

    /** A helicopter destroyed by damage explodes and hurts a mob beside it. */
    private static void destroyedExplosion(GameTestHelper helper) {
        HeliEntity heli = spawn(helper, AH64, new Vec3(12.5, 1.0, 12.5), 0.0F);
        Husk husk = target(helper, new Vec3(14.5, 1.0, 12.5));
        helper.runAfterDelay(5, () -> {
            heli.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), 100000.0F);
            check(helper, heli.isDestroyed(), "the helicopter was destroyed");
        });
        helper.succeedWhen(() -> {
            check(helper, hurt(husk), "the explosion has not hurt the husk");
            GameTestSupport.cleanUp(heli);
        });
    }
}
