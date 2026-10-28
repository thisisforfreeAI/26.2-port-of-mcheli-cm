package io.github.mcheliceport.damage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mcheliceport.content.ContentLocation;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.content.aircraft.HeliInfo;
import io.github.mcheliceport.content.emitter.YamlEmitter;
import io.github.mcheliceport.content.parser.txt.TxtParser;
import io.github.mcheliceport.content.parser.yaml.YamlParser;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** M10a: component zones placed by a vehicle's own file. */
class AddonDamageZonesTest {

    private static final ContentLocation LOCATION = ContentLocation.builtin("zone_test");

    private static HeliInfo txt(String... lines) throws Exception {
        return TxtParser.INSTANCE.parseHelicopter(LOCATION, "zone_test.txt", List.of(lines), false);
    }

    @Test
    void txtZonesRoundTripThroughYaml() throws Exception {
        HeliInfo heli = txt(
                "AddRotor = 4, 10, 0, 3, 0, 0, 1, 0",
                "AddRotor = 2, 10, 0, 2, -8, 1, 0, 0",
                "AddDamageZone = ENGINE, 0, 1.2, -2.5, 0.6, 0.5, 0.8",
                "AddDamageZone = fuel_tank, 0, 0.8, 0, 0.8, 0.4, 1.2, 0.9, 0.5",
                "AddDamageZone = MAIN_ROTOR, blade0",
                "AddDamageZone = TAIL_ROTOR, blade1, 2.0");
        assertEquals(4, heli.damageZones.size());
        String yaml = new YamlEmitter().emitHelicopter(heli);
        assertTrue(yaml.contains("DamageZones"), yaml);
        HeliInfo restored = YamlParser.INSTANCE.parseHelicopter(LOCATION, "zone_test.yml", List.of(yaml.split("\n")),
                false);
        assertEquals(heli.damageZones, restored.damageZones);
    }

    @Test
    void placedZonesReplaceTheDerivedOnes() throws Exception {
        HeliInfo heli = txt(
                "AddRotor = 4, 10, 0, 3, 0, 0, 1, 0",
                "AddRotor = 2, 10, 0, 2, -8, 1, 0, 0",
                "AddDamageZone = ENGINE, 0, 1.2, 2.5, 0.6, 0.5, 0.8",
                "AddDamageZone = ENGINE, 0, 1.2, -2.5, 0.6, 0.5, 0.8, 0.5, 0.7",
                "AddDamageZone = MAIN_ROTOR, blade0",
                "AddDamageZone = MAIN_ROTOR, nosuchpart");
        List<DamageZone> zones = DamageZones.of(heli);
        assertEquals(3, zones.size(), "a rotor naming no rotor is skipped: " + zones);
        assertEquals(ComponentKind.ENGINE, zones.get(0).kind());
        assertEquals(0, zones.get(0).index());
        assertEquals(1, zones.get(1).index(), "indexes count per kind");
        assertEquals(new Vec3(0, 3, 0), zones.get(2).center(), "a rotor by part sits on its hub");
        assertEquals("blade0", zones.get(2).partName());
        assertTrue(zones.stream().noneMatch(z -> z.kind() == ComponentKind.TAIL_ROTOR),
                "no derived tail rotor is added");

        assertEquals(ComponentKind.ENGINE.damageShare, zones.get(0).share());
        assertEquals(ComponentDamage.HEALTH_SHARE_OF_MAX_HP, zones.get(0).health());
        assertEquals(10.0F * 0.5F / 70.0F, ComponentDamage.fraction(10.0F, zones.get(1), 100), 1e-6,
                "the second engine uses its own share and health");
        assertEquals(10.0F / 35.0F, ComponentDamage.fraction(10.0F, zones.get(0), 100), 1e-6,
                "a default zone matches M9");
    }

    @Test
    void badEntriesAreSkipped() throws Exception {
        HeliInfo heli = txt(
                "AddDamageZone = TURRET, 0, 0, 0, 1, 1, 1",
                "AddDamageZone = ENGINE, blade0",
                "AddDamageZone = ENGINE, 0, 0, 0, 0, 1, 1",
                "AddDamageZone = ENGINE, 0, 0, 0, 1, 1, 1, 50",
                "AddDamageZone = ENGINE, 0, 0, 0, 1, 1, 1");
        assertEquals(1, heli.damageZones.size(), "unknown kinds, part-only engines, empty boxes and bad shares are skipped");
        assertThrows(IllegalArgumentException.class, () -> new AircraftInfo.DamageZoneSpec(ComponentKind.ENGINE,
                Vec3.ZERO, new Vec3(1, 1, 1), "", Float.NaN, 0.0F));
    }

    @Test
    void zonesStopAtEightSlots() throws Exception {
        String[] lines = new String[10];
        for (int i = 0; i < lines.length; i++) {
            lines[i] = "AddDamageZone = FUEL_TANK, 0, 0, " + i + ", 0.5, 0.5, 0.5";
        }
        assertEquals(ComponentHealth.SLOTS, DamageZones.of(txt(lines)).size());
    }

    @Test
    void repairByHpFollowsEachComponentsOwnHealth() {
        long p = ComponentHealth.withLevel(ComponentHealth.FULL, 0, 0);
        p = ComponentHealth.withLevel(p, 1, 100);
        // Slot 0 holds 10 HP, slot 1 holds 100 HP: 5 HP is half of slot 0 and goes there first.
        long r = ComponentHealth.repairHp(p, new float[] {10.0F, 100.0F}, 5.0F);
        assertEquals(127.5, ComponentHealth.level(r, 0), 1.0);
        assertEquals(100, ComponentHealth.level(r, 1));
        // 15 HP fills slot 0 (10 HP) and spills 5 HP, about 13 levels, into slot 1.
        r = ComponentHealth.repairHp(p, new float[] {10.0F, 100.0F}, 15.0F);
        assertEquals(255, ComponentHealth.level(r, 0));
        assertEquals(113, ComponentHealth.level(r, 1));
        // With equal capacities it matches the M9 repair.
        assertEquals(ComponentHealth.repair(p, 2, 0.2F), ComponentHealth.repairHp(p, new float[] {35.0F, 35.0F}, 7.0F));
    }
}
