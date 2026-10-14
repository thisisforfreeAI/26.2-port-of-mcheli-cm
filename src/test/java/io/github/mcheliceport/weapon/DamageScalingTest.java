package io.github.mcheliceport.weapon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The config's damage lists against upstream {@code MCH_Config#applyDamageVsEntity}/{@code applyDamageByExternal}. */
class DamageScalingTest {

    /** Upstream's loop, with class names replaced by entity type IDs as decision D19 says. */
    private static float upstream(List<String> lines, String id, float damage) {
        for (String s : lines) {
            String[] splt = s.split("\\s*,\\s*");
            double factor;
            String name;
            if (splt.length == 1) {
                factor = Double.parseDouble(splt[0]);
                name = "";
            } else if (splt.length == 2) {
                factor = Double.parseDouble(splt[0]);
                name = splt[1];
            } else {
                factor = 1.0;
                name = "";
            }
            // Upstream: name.isEmpty() || targetName.indexOf(name) > 0, where targetName starts with "class ".
            if (name.isEmpty() || ("class " + id).indexOf(name) > 0) {
                damage = (float) (damage * factor);
            }
        }
        return damage;
    }

    @Test
    void matchesUpstreamForEveryCombination() {
        List<List<String>> configs = List.of(
                List.of("1.0"),
                List.of("1.0", "3.0, flansmod"),
                List.of("0.5"),
                List.of("2.0, zombie", "0.25, minecraft"),
                List.of("1.5", "1.5, a, b"),
                List.of());
        List<String> ids = List.of("minecraft:zombie", "minecraft:player", "flansmod:bullet",
                "mcheli_ce_port:helicopter");
        for (List<String> config : configs) {
            for (String id : ids) {
                for (float damage : new float[] {0.0F, 1.0F, 7.5F, 100.0F}) {
                    assertEquals(upstream(config, id, damage),
                            DamageScaling.apply(DamageScaling.parseAll(config), id, damage), config + " " + id);
                }
            }
        }
    }

    @Test
    void externalMatchesEitherSource() {
        List<DamageScaling.Entry> entries = DamageScaling.parseAll(List.of("1.0", "0.5, flansmod"));
        assertEquals(5.0F, DamageScaling.applyEither(entries, "flansmod:gun_user", null, 10.0F));
        assertEquals(5.0F, DamageScaling.applyEither(entries, "minecraft:player", "flansmod:bullet", 10.0F));
        // Upstream multiplies once per entry even when both match.
        assertEquals(5.0F, DamageScaling.applyEither(entries, "flansmod:a", "flansmod:b", 10.0F));
        assertEquals(10.0F, DamageScaling.applyEither(entries, "minecraft:player", "minecraft:arrow", 10.0F));
    }

    @Test
    void entryParsing() {
        assertEquals(new DamageScaling.Entry(3.0, "flansmod"), DamageScaling.Entry.parse("3.0, flansmod"));
        assertEquals(new DamageScaling.Entry(2.0, ""), DamageScaling.Entry.parse(" 2 "));
        assertEquals(new DamageScaling.Entry(1.0, ""), DamageScaling.Entry.parse("2, a, b"));
        assertTrue(DamageScaling.Entry.isValid("0.5,x"));
        assertFalse(DamageScaling.Entry.isValid("x, 0.5"));
    }
}
