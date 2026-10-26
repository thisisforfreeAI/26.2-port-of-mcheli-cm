package io.github.mcheliceport.gametest;

import static io.github.mcheliceport.gametest.GameTestSupport.check;

import io.github.mcheliceport.combat.AntiCheat;
import io.github.mcheliceport.content.ContentType;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.item.AircraftItem;
import io.github.mcheliceport.net.ModNetwork;
import io.github.mcheliceport.net.WeaponControlPayload;
import io.github.mcheliceport.registry.ModItems;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** M9 anti-cheat: the server screens a player's fire packets and BVR locks. */
final class AntiCheatGameTests {
    private AntiCheatGameTests() { }

    static void register() {
        ModGameTests.test("anti_cheat_screens_fire_packets", 30, AntiCheatGameTests::fire);
        ModGameTests.test("anti_cheat_screens_bvr_locks", 30, AntiCheatGameTests::bvr);
    }

    private static AircraftEntity heli(GameTestHelper h, Vec3 at) {
        var item = (AircraftItem) ModItems.contentItems().stream()
                .filter(c -> c.type() == ContentType.HELICOPTER && c.contentName().equals("ah-64"))
                .findFirst().orElseThrow().item().get();
        Vec3 p = h.absoluteVec(at);
        AircraftEntity heli = item.createAircraft(h.getLevel(), p.x, p.y, p.z);
        h.getLevel().addFreshEntity(heli);
        heli.tick();
        return heli;
    }

    @SuppressWarnings("removal")
    private static void fire(GameTestHelper h) {
        AircraftEntity heli = heli(h, new Vec3(12, 2, 12));
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        WeaponControlPayload honest = WeaponControlPayload.none(heli.getId())
                .withUseWeapon(0, 0, heli.getX(), heli.getY(), heli.getZ(), 30.0F, 5.0F);
        check(h, ModNetwork.screenWeaponControl(heli, player, honest) == honest, "an honest shot passes untouched");

        WeaponControlPayload far = WeaponControlPayload.none(heli.getId())
                .withUseWeapon(0, 0, heli.getX() + 30.0, heli.getY(), heli.getZ(), 30.0F, 5.0F);
        WeaponControlPayload screened = ModNetwork.screenWeaponControl(heli, player, far);
        check(h, screened.useWeapon() && screened.x() == heli.getX() && screened.z() == heli.getZ(),
                "a shot from 30 blocks away fires from the vehicle instead");

        WeaponControlPayload nan = WeaponControlPayload.none(heli.getId())
                .withUseWeapon(0, 0, heli.getX(), heli.getY(), heli.getZ(), Float.NaN, 0.0F).withFlare(1);
        WeaponControlPayload dropped = ModNetwork.screenWeaponControl(heli, player, nan);
        check(h, !dropped.useWeapon() && dropped.flareType() == 1, "an impossible aim drops the shot but keeps the flare");

        // The bucket allowed 4 this tick and the three above spent 3.
        check(h, ModNetwork.screenWeaponControl(heli, player, honest).useWeapon(), "fourth packet of the tick passes");
        check(h, !ModNetwork.screenWeaponControl(heli, player, honest).useWeapon(), "fifth packet of the tick is dropped");

        var record = AntiCheat.recordOf(player.getUUID());
        check(h, record != null && record.counts().get(AntiCheat.Check.FIRE_ORIGIN) == 1
                && record.counts().get(AntiCheat.Check.AIM) == 1
                && record.counts().get(AntiCheat.Check.PACKET_RATE) == 1, "each failure is recorded: "
                + (record != null ? record.counts() : null));
        check(h, record.score(h.getLevel().getGameTime()) < 20.0, "a few failures stay below the threshold");
        GameTestSupport.cleanUp(heli);
        h.succeed();
    }

    @SuppressWarnings("removal")
    private static void bvr(GameTestHelper h) {
        AircraftEntity heli = heli(h, new Vec3(12, 2, 12));
        AircraftEntity other = heli(h, new Vec3(4, 2, 4));
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        check(h, !ModNetwork.bvrTargetValid(heli, player, heli), "no lock on itself");
        check(h, !ModNetwork.bvrTargetValid(heli, player, player), "no lock on the shooter");
        check(h, ModNetwork.bvrTargetValid(heli, player, other), "a nearby vehicle is a valid target");
        Vec3 o = other.position();
        other.setPos(o.x + 2100.0, o.y, o.z);
        check(h, !ModNetwork.bvrTargetValid(heli, player, other), "no lock past the radar feed's range");
        other.setPos(o);
        GameTestSupport.cleanUp(heli);
        GameTestSupport.cleanUp(other);
        h.succeed();
    }
}
