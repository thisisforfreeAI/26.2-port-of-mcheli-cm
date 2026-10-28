package io.github.mcheliceport.gametest;

import static io.github.mcheliceport.gametest.GameTestSupport.AH64;
import static io.github.mcheliceport.gametest.GameTestSupport.check;

import io.github.mcheliceport.content.ContentType;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.content.aircraft.CrewRole;
import io.github.mcheliceport.content.aircraft.SeatInfo;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.aircraft.CrewRoles;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.entity.ship.ShipEntity;
import io.github.mcheliceport.item.AircraftItem;
import io.github.mcheliceport.net.ModNetwork;
import io.github.mcheliceport.net.WeaponControlPayload;
import io.github.mcheliceport.registry.ModItems;
import io.github.mcheliceport.weapon.WeaponSet;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * M10b crew roles on stock vehicles whose seat roles a test sets: a loader speeds reloads and
 * serves the emptiest gun, a loader seat cannot fire, a commander marks a target for the crew.
 */
final class CrewGameTests {

    private CrewGameTests() {
    }

    static void register() {
        ModGameTests.test("crew_loader_reloads", 20, CrewGameTests::loaderReloads);
        ModGameTests.test("crew_loader_cannot_fire", 60, CrewGameTests::loaderCannotFire);
        ModGameTests.test("crew_commander_marks", 20, CrewGameTests::commanderMarks);
    }

    /** Runs {@code body} with seat {@code sid} of {@code info} in {@code role}, then restores it. */
    private static void withRole(AircraftInfo info, int sid, CrewRole role, Runnable body) {
        SeatInfo seat = info.seatList.get(sid);
        CrewRole before = seat.role;
        boolean required = info.loaderRequired;
        seat.role = role;
        try {
            body.run();
        } finally {
            seat.role = before;
            info.loaderRequired = required;
        }
    }

    private static Player board(GameTestHelper h, AircraftEntity ac, int seatIndex) {
        if (ac.getSeats().length > seatIndex && ac.getSeats()[seatIndex] == null) {
            ac.tick();
        }
        Player player = GameTestSupport.player(h, GameType.SURVIVAL, new Vec3(10, 2, 10));
        check(h, player.startRiding(ac.getSeats()[seatIndex], true, true), "boarded seat " + (seatIndex + 1));
        return player;
    }

    /** The first gun with a magazine and a reload time. */
    private static int magazineGun(AircraftEntity ac) {
        for (int i = 0; i < ac.getWeaponNum(); i++) {
            WeaponSet set = ac.getWeapon(i);
            if (set.getCurrentWeapon() != null && set.getCurrentWeapon().getInfo() != null && set.getMagSize() > 1
                    && set.getCurrentWeapon().getReloadCount() > 0) {
                return i;
            }
        }
        return -1;
    }

    private static void loaderReloads(GameTestHelper h) {
        HeliEntity heli = GameTestSupport.spawn(h, AH64, new Vec3(12, 2, 12), 0.0F);
        heli.tick();
        AircraftInfo info = heli.getAcInfo();
        check(h, CrewRoles.reloadFactor(heli) == 1.0F, "a stock AH-64 reloads at its own rate");
        withRole(info, 1, CrewRole.LOADER, () -> {
            check(h, CrewRoles.reloadFactor(heli) == 1.0F, "an empty loader seat changes nothing by default");
            info.loaderRequired = true;
            check(h, CrewRoles.reloadFactor(heli) == CrewRoles.NO_LOADER_FACTOR, "a required loader missing slows reloads");
            Player loader = board(h, heli, 0);
            check(h, CrewRoles.roleOf(heli, loader) == CrewRole.LOADER, "the second seat is the loader");
            check(h, CrewRoles.reloadFactor(heli) == CrewRoles.LOADER_FACTOR, "a manned loader speeds reloads");

            int gun = magazineGun(heli);
            check(h, gun >= 0, "the AH-64 has a gun with a magazine");
            WeaponSet set = heli.getWeapon(gun);
            int base = set.getCurrentWeapon().getReloadCount();
            set.cooldown = 0;
            set.reloadCooldown = 0;
            set.setAmmo(0);
            set.setReserveAmmo(set.getMaxAmmo());
            set.reload();
            int expected = Math.max(1, Math.round(base * CrewRoles.LOADER_FACTOR) - 20);
            check(h, set.reloadCooldown == expected, "the reload takes " + expected + " ticks, not " + set.reloadCooldown);

            // Every gun full but this one at half: the loader's key serves it.
            for (int i = 0; i < heli.getWeaponNum(); i++) {
                WeaponSet s = heli.getWeapon(i);
                s.cooldown = 0;
                s.reloadCooldown = 0;
                if (s.getCurrentWeapon() != null && s.getCurrentWeapon().getInfo() != null && s.getMagSize() > 0) {
                    s.setAmmo(s.getMagSize());
                }
            }
            set.setAmmo(set.getMagSize() / 2);
            set.setReserveAmmo(set.getMaxAmmo());
            check(h, CrewRoles.loaderTarget(heli.weapons()) == gun, "the half-empty gun is the loader's target");
            heli.weapons().manualReloadForPlayer(loader);
            check(h, set.reloadCooldown > 0, "the loader's reload key reloads it");
            loader.stopRiding();
        });
        GameTestSupport.cleanUp(heli);
        h.succeed();
    }

    private static ShipEntity ship(GameTestHelper h) {
        Vec3 p = h.absoluteVec(new Vec3(12.5, 20, 12.5));
        AircraftItem item = (AircraftItem) ModItems.contentItems().stream()
                .filter(i -> i.type() == ContentType.SHIP && i.contentName().equals("mark5"))
                .findFirst().orElseThrow().item().get();
        ShipEntity ship = (ShipEntity) item.createAircraft(h.getLevel(), p.x, p.y, p.z);
        ship.initRotationYaw(0);
        h.getLevel().addFreshEntity(ship);
        return ship;
    }

    private static void fire(ShipEntity ship, Player user) {
        ModNetwork.applyWeaponControl(ship, user, WeaponControlPayload.none(ship.getId())
                .withUseWeapon(0, 0, ship.getX(), ship.getY(), ship.getZ(), user.getYRot(), user.getXRot()));
    }

    /** The Mark V's second crew seat works weapon 0; as a loader it fires nothing, as a gunner it does. */
    private static void loaderCannotFire(GameTestHelper h) {
        ShipEntity ship = ship(h);
        Player pilot = GameTestSupport.player(h, GameType.SURVIVAL, new Vec3(12, 10, 10));
        check(h, pilot.startRiding(ship, true, true), "pilot boarded");
        h.runAfterDelay(2, () -> {
            Player crew = GameTestSupport.player(h, GameType.SURVIVAL, new Vec3(10, 20, 10));
            check(h, crew.startRiding(ship.getSeats()[1], true, true), "gunner seat boarded");
            WeaponSet ws = ship.getWeapon(0);
            ws.setAmmo(ws.getMagSize());
            ModNetwork.applyWeaponControl(ship, crew, WeaponControlPayload.none(ship.getId()).withSwitchWeapon(0));
            check(h, CrewRoles.roleOf(ship, crew) == CrewRole.GUNNER, "a seat with a weapon defaults to gunner");
            h.runAfterDelay(25, () -> {
                crew.setYRot(ship.getYaw() + ship.getAcInfo().getWeaponById(0).defaultYaw);
                withRole(ship.getAcInfo(), 2, CrewRole.LOADER, () -> {
                    ws.cooldown = 0;
                    int ammo = ws.getAmmo();
                    fire(ship, crew);
                    check(h, ws.getAmmo() == ammo, "a loader fires nothing");
                });
                ws.cooldown = 0;
                int ammo = ws.getAmmo();
                fire(ship, crew);
                check(h, ws.getAmmo() == ammo - 1, "back as a gunner the seat fires");
                ship.getPassengers().forEach(Entity::stopRiding);
                GameTestSupport.cleanUp(ship);
                h.succeed();
            });
        });
    }

    private static void commanderMarks(GameTestHelper h) {
        HeliEntity heli = GameTestSupport.spawn(h, AH64, new Vec3(12, 2, 12), 0.0F);
        heli.tick();
        withRole(heli.getAcInfo(), 1, CrewRole.COMMANDER, () -> {
            Player commander = board(h, heli, 0);
            check(h, CrewRoles.roleOf(heli, commander) == CrewRole.COMMANDER, "the second seat is the commander");
            // A husk 9 blocks along the commander's view, clear of the helicopter.
            Husk husk = EntityTypes.HUSK.create(h.getLevel(), EntitySpawnReason.COMMAND);
            husk.setNoAi(true);
            Vec3 at = commander.getEyePosition().add(commander.getViewVector(1.0F).scale(9.0))
                    .subtract(0, husk.getBbHeight() / 2, 0);
            husk.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
            h.getLevel().addFreshEntity(husk);
            Entity mark = CrewRoles.markTarget(heli, commander);
            check(h, mark == husk, "the commander marks the husk in view: " + mark);
            heli.setCrewMark(mark);
            check(h, heli.getCrewMark() == husk, "the crew sees the mark");
            // A living entity looks where its head points.
            commander.setYRot(commander.getYHeadRot() + 90.0F);
            commander.setYHeadRot(commander.getYRot());
            commander.yHeadRotO = commander.getYRot();
            check(h, CrewRoles.markTarget(heli, commander) == null, "looking away marks nothing");
            husk.discard();
            heli.tick();
            check(h, heli.getCrewMarkId() < 0, "the mark ends with its target");
            commander.stopRiding();
        });
        GameTestSupport.cleanUp(heli);
        h.succeed();
    }
}
