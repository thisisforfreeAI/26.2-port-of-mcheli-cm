package io.github.mcheliceport.weapon;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mcheliceport.content.ContentRegistries;
import io.github.mcheliceport.content.ContentTestSupport;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.content.weapon.WeaponInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * §8.2: every stock weapon's type has a factory, and every vehicle's weapon sets resolve. Creating
 * each weapon needs a level, so {@code WeaponGameTests} does that.
 */
class AllWeaponsTest {

    private static ContentRegistries.Snapshot content;

    @BeforeAll
    static void load() {
        content = ContentTestSupport.load(ContentTestSupport.stockPack());
    }

    @Test
    void everyStockWeaponHasItsType() {
        List<String> failures = new ArrayList<>();
        int typed = 0;
        for (Map.Entry<String, WeaponInfo> e : content.weapon().entries()) {
            WeaponInfo info = e.getValue();
            if (info.type.isEmpty()) {
                continue;
            }
            WeaponTypes.Factory factory = WeaponTypes.get(info.type);
            if (factory == null) {
                failures.add(e.getKey() + ": unknown type " + info.type);
                continue;
            }
            typed++;
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
        assertTrue(typed > 100, "only " + typed + " weapons");
    }

    @Test
    void everyVehicleWeaponSetResolves() {
        List<String> failures = new ArrayList<>();
        List<AircraftInfo> vehicles = new ArrayList<>();
        vehicles.addAll(content.heli().values());
        vehicles.addAll(content.plane().values());
        vehicles.addAll(content.ship().values());
        vehicles.addAll(content.tank().values());
        vehicles.addAll(content.vehicle().values());
        for (AircraftInfo info : vehicles) {
            for (AircraftInfo.WeaponSet ws : info.weaponSetList) {
                WeaponInfo wi = content.weapon().get(ws.type);
                if (wi == null) {
                    wi = content.weapon().get(ws.type.toLowerCase(Locale.ROOT));
                }
                if (wi == null || WeaponTypes.get(wi.type) == null) {
                    failures.add(info.name + ": " + ws.type);
                }
            }
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }
}
