package io.github.mcheliceport.damage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mcheliceport.content.ContentRegistries;
import io.github.mcheliceport.content.ContentTestSupport;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** M9 component damage on plain values and the stock content. */
class ComponentDamageTest {

    private static ContentRegistries.Snapshot stock;

    @BeforeAll
    static void load() {
        stock = ContentTestSupport.load(ContentTestSupport.stockPack());
    }

    private static long count(List<DamageZone> zones, ComponentKind kind) {
        return zones.stream().filter(z -> z.kind() == kind).count();
    }

    @Test
    void packingKeepsSlotsApart() {
        long p = ComponentHealth.FULL;
        for (int i = 0; i < ComponentHealth.SLOTS; i++) {
            assertEquals(255, ComponentHealth.level(p, i));
        }
        p = ComponentHealth.withLevel(p, 3, 10);
        p = ComponentHealth.withLevel(p, 7, 0);
        assertEquals(10, ComponentHealth.level(p, 3));
        assertEquals(0, ComponentHealth.level(p, 7));
        assertEquals(255, ComponentHealth.level(p, 2));
        assertEquals(255, ComponentHealth.level(p, 6));
        assertEquals(0, ComponentHealth.level(ComponentHealth.withLevel(p, 1, -40), 1));
    }

    @Test
    void damageAddsUpAndRepairHealsTheWorstFirst() {
        long p = ComponentHealth.damage(ComponentHealth.FULL, 0, 0.6F);
        assertEquals(ComponentState.DAMAGED, ComponentState.of(ComponentHealth.health(p, 0)));
        p = ComponentHealth.damage(p, 0, 0.6F);
        assertEquals(ComponentState.DISABLED, ComponentState.of(ComponentHealth.health(p, 0)));
        assertEquals(254, ComponentHealth.level(ComponentHealth.damage(ComponentHealth.FULL, 1, 0.0001F), 1),
                "a scratch still costs a level");
        assertEquals(ComponentHealth.FULL, ComponentHealth.damage(ComponentHealth.FULL, 1, 0.0F));
        p = ComponentHealth.withLevel(p, 1, 200);
        long r = ComponentHealth.repair(p, 3, 0.2F);
        assertEquals(51, ComponentHealth.level(r, 0), "the disabled engine heals first");
        assertEquals(200, ComponentHealth.level(r, 1));
        long all = ComponentHealth.repair(p, 3, 10.0F);
        assertEquals(255, ComponentHealth.level(all, 0));
        assertEquals(255, ComponentHealth.level(all, 1));
    }

    @Test
    void statesSplitAtHalfAndZero() {
        assertEquals(ComponentState.OK, ComponentState.of(1.0F));
        assertEquals(ComponentState.OK, ComponentState.of(0.51F));
        assertEquals(ComponentState.DAMAGED, ComponentState.of(0.5F));
        assertEquals(ComponentState.DAMAGED, ComponentState.of(0.01F));
        assertEquals(ComponentState.DISABLED, ComponentState.of(0.0F));
    }

    @Test
    void rayEntersTheNearestZone() {
        DamageZone near = new DamageZone(ComponentKind.ENGINE, 0, new Vec3(0, 0, 5), new Vec3(1, 1, 1), "");
        DamageZone far = new DamageZone(ComponentKind.FUEL_TANK, 0, new Vec3(0, 0, 10), new Vec3(1, 1, 1), "");
        Vec3 from = new Vec3(0, 0, 0);
        Vec3 dir = new Vec3(0, 0, 1);
        assertEquals(4.0, near.entry(from, dir), 1e-9);
        assertTrue(Double.isNaN(near.entry(from, new Vec3(0, 0, -1))));
        assertTrue(Double.isNaN(near.entry(new Vec3(3, 0, 0), dir)));
        assertEquals(0.0, near.entry(new Vec3(0, 0, 5), dir), 1e-9);
        assertEquals(1, ComponentDamage.pick(List.of(far, near), from, dir, 50.0), "the nearer zone wins");
        assertEquals(-1, ComponentDamage.pick(List.of(far, near), from, dir, 2.0), "beyond penetration");
        assertEquals(0.0, near.distanceTo(new Vec3(0.5, 0, 5)), 1e-9);
        assertEquals(2.0, near.distanceTo(new Vec3(0, 3, 5)), 1e-9);
    }

    @Test
    void ah64HasTwoEnginesBothRotorsAndATank() {
        List<DamageZone> zones = DamageZones.of(stock.heli().get("ah-64"));
        assertEquals(2, count(zones, ComponentKind.ENGINE));
        assertEquals(1, count(zones, ComponentKind.MAIN_ROTOR));
        assertEquals(1, count(zones, ComponentKind.TAIL_ROTOR));
        assertEquals(1, count(zones, ComponentKind.FUEL_TANK));
        DamageZone tail = zones.stream().filter(z -> z.kind() == ComponentKind.TAIL_ROTOR).findFirst().orElseThrow();
        assertEquals("blade1", tail.partName());
        DamageZone engine = zones.getFirst();
        assertTrue(engine.center().z > -5.1, "engine sits forward of its exhaust: " + engine.center());
    }

    @Test
    void everyStockVehicleHasAnEngineAndFitsTheSlots() {
        List<AircraftInfo> all = new ArrayList<>();
        all.addAll(stock.heli().values());
        all.addAll(stock.plane().values());
        all.addAll(stock.ship().values());
        all.addAll(stock.tank().values());
        all.addAll(stock.vehicle().values());
        assertFalse(all.isEmpty());
        for (AircraftInfo info : all) {
            List<DamageZone> zones = DamageZones.of(info);
            String name = info.toString();
            assertTrue(zones.size() <= ComponentHealth.SLOTS, name);
            assertTrue(count(zones, ComponentKind.ENGINE) >= 1, name);
            for (DamageZone z : zones) {
                assertTrue(z.halfSize().x > 0 && z.halfSize().y > 0 && z.halfSize().z > 0, name + " " + z);
                assertTrue(Double.isFinite(z.center().lengthSqr()), name + " " + z);
            }
        }
        for (var heli : stock.heli().values()) {
            assertTrue(count(DamageZones.of(heli), ComponentKind.MAIN_ROTOR) >= 1, heli.toString());
        }
    }

    @Test
    void damageShareOfAComponentsHealth() {
        // An AH-64-sized 100 HP vehicle: a component holds 35 HP, a 10-damage engine hit takes 10/35.
        assertEquals(10.0F / 35.0F, ComponentDamage.fraction(10.0F, 1.0F, 100), 1e-6);
        assertEquals(12.0F / 35.0F, ComponentDamage.fraction(10.0F, 1.2F, 100), 1e-6);
    }
}
