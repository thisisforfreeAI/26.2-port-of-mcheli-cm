package io.github.mcheliceport.entity.aircraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mcheliceport.content.ContentLocation;
import io.github.mcheliceport.content.ContentRegistries;
import io.github.mcheliceport.content.ContentTestSupport;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.content.aircraft.CrewRole;
import io.github.mcheliceport.content.aircraft.HeliInfo;
import io.github.mcheliceport.content.emitter.YamlEmitter;
import io.github.mcheliceport.content.parser.txt.TxtParser;
import io.github.mcheliceport.content.parser.yaml.YamlParser;
import io.github.mcheliceport.net.CrewMarkPayload;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** M10b crew roles on the stock content and on files that set them. */
class CrewRolesTest {

    private static ContentRegistries.Snapshot stock;

    @BeforeAll
    static void load() {
        stock = ContentTestSupport.load(ContentTestSupport.stockPack());
    }

    private static List<AircraftInfo> all() {
        List<AircraftInfo> all = new ArrayList<>(stock.heli().values());
        all.addAll(stock.plane().values());
        all.addAll(stock.ship().values());
        all.addAll(stock.tank().values());
        all.addAll(stock.vehicle().values());
        return all;
    }

    @Test
    void stockSeatsKeepWhoMayFire() {
        assertFalse(all().isEmpty());
        for (AircraftInfo info : all()) {
            if (info.getName().equals("ammo_cart")) {
                // M10c's ammo cart is the one stock vehicle with a crew role: its seat is a loader's.
                assertEquals(CrewRole.LOADER, CrewRoles.role(info, 0));
                continue;
            }
            assertEquals(CrewRole.PILOT, CrewRoles.role(info, 0), info.toString());
            for (AircraftInfo.WeaponSet set : info.weaponSetList) {
                for (AircraftInfo.Weapon w : set.weapons) {
                    assertTrue(CrewRoles.role(info, w.seatID).canFire(), info + " weapon seat " + w.seatID);
                }
            }
            for (int sid = 0; sid < info.seatList.size(); sid++) {
                if (info.seatList.get(sid).gunner) {
                    assertEquals(sid == 0 ? CrewRole.PILOT : CrewRole.GUNNER, CrewRoles.role(info, sid),
                            info + " seat " + sid);
                }
            }
            assertFalse(CrewRoles.hasRole(info, CrewRole.LOADER), info + " has no loader, so its reloads are unchanged");
            assertFalse(CrewRoles.hasRole(info, CrewRole.COMMANDER), info.toString());
        }
        HeliInfo ah64 = stock.heli().get("ah-64");
        assertEquals(CrewRole.GUNNER, CrewRoles.role(ah64, 1));
    }

    @Test
    void rolesAndLoaderRequiredRoundTripThroughYaml() throws Exception {
        ContentLocation location = ContentLocation.builtin("crew_test");
        HeliInfo heli = TxtParser.INSTANCE.parseHelicopter(location, "crew_test.txt", List.of(
                "AddSeat = 0, 1, 0",
                "AddSeat = 0, 1, -1",
                "AddSeat = 0, 1, -2",
                "SeatRole = 2, loader",
                "SeatRole = 3, COMMANDER",
                "SeatRole = 9, GUNNER",
                "LoaderRequired = true"), false);
        assertEquals(CrewRole.LOADER, heli.seatList.get(1).role);
        assertEquals(CrewRole.COMMANDER, heli.seatList.get(2).role);
        assertNull(heli.seatList.get(0).role, "an unset role stays derived");
        assertTrue(heli.loaderRequired);
        String yaml = new YamlEmitter().emitHelicopter(heli);
        HeliInfo restored = YamlParser.INSTANCE.parseHelicopter(location, "crew_test.yml", List.of(yaml.split("\n")),
                false);
        assertEquals(CrewRole.LOADER, restored.seatList.get(1).role);
        assertEquals(CrewRole.COMMANDER, restored.seatList.get(2).role);
        assertTrue(restored.loaderRequired);
        assertTrue(CrewRoles.hasRole(restored, CrewRole.LOADER));
        assertEquals(CrewRole.PASSENGER, CrewRoles.role(restored, 99), "a seat it doesn't have rides along");
    }

    @Test
    void onlyLoadersAndPassengersHoldFire() {
        assertTrue(CrewRole.PILOT.canFire());
        assertTrue(CrewRole.GUNNER.canFire());
        assertTrue(CrewRole.COMMANDER.canFire());
        assertFalse(CrewRole.LOADER.canFire());
        assertFalse(CrewRole.PASSENGER.canFire());
        assertEquals(1.0F, CrewRoles.reloadFactor(null));
        assertEquals(0, CrewRoles.reloadTicks(null, 0));
        assertEquals(100, CrewRoles.reloadTicks(null, 100));
    }

    @Test
    void markPayloadRoundTrips() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        CrewMarkPayload p = new CrewMarkPayload(4242);
        CrewMarkPayload.CODEC.encode(buf, p);
        assertEquals(p, CrewMarkPayload.CODEC.decode(buf));
    }
}
