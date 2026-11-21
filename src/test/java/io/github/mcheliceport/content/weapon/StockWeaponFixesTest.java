package io.github.mcheliceport.content.weapon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mcheliceport.content.ContentRegistries;
import io.github.mcheliceport.content.ContentTestSupport;
import io.github.mcheliceport.content.hud.HudFormula;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The optional stock weapon fixes give back upstream's legacy TXT values for exactly the fields
 * its YAML conversion lost, and switching them off restores the stock YAML values.
 */
class StockWeaponFixesTest {

    @AfterEach
    void reset() {
        StockWeaponFixes.apply(false);
    }

    @Test
    void restoredWeaponsMatchLegacyAndRevert() {
        ContentRegistries.Snapshot yaml = ContentTestSupport.load(ContentTestSupport.stockPack());
        ContentRegistries.Snapshot legacy = ContentTestSupport.load(ContentTestSupport.legacyPack());
        ContentRegistries.Snapshot pristine = ContentTestSupport.load(ContentTestSupport.stockPack());
        Map<String, Map<String, Object>> fixes = StockWeaponFixes.load();
        assertEquals(65, fixes.size());

        StockWeaponFixes.prepare(yaml.weapon(), info -> true);
        assertEquals(fixes.size(), StockWeaponFixes.preparedCount(), "every fix names a stock weapon");
        StockWeaponFixes.apply(true);
        assertTrue(StockWeaponFixes.isApplied());

        for (String name : fixes.keySet()) {
            WeaponInfo fixed = yaml.weapon().get(name);
            WeaponInfo old = legacy.weapon().get(name);
            assertNotNull(old, name);
            assertEquals(factors(old), factors(fixed), name + " damage factors");
            assertEquals(old.bomblet, fixed.bomblet, name + " bomblet");
            assertEquals(old.bombletSTime, fixed.bombletSTime, name + " bombletSTime");
            assertEquals(old.bombletDiff, fixed.bombletDiff, 1e-6, name + " bombletDiff");
            assertEquals(old.isFAE, fixed.isFAE, name + " isFAE");
            assertEquals(old.dispenseRange, fixed.dispenseRange, name + " dispenseRange");
            assertEquals(modernItem(old.dispenseItemLoc), fixed.dispenseItemLoc, name + " dispenseItemLoc");
            assertEquals(old.length, fixed.length, 1e-6, name + " length");
            assertEquals(old.radius, fixed.radius, 1e-6, name + " radius");
            assertEquals(old.angle, fixed.angle, 1e-4, name + " angle");
            assertEquals(old.markTime, fixed.markTime, name + " markTime");
        }
        // A dispenser that had no item now uses a 26.2 item.
        assertEquals("bone_meal", yaml.weapon().get("dispenser_fertilizer").dispenseItemLoc);

        StockWeaponFixes.apply(false);
        assertFalse(StockWeaponFixes.isApplied());
        for (String name : fixes.keySet()) {
            WeaponInfo now = yaml.weapon().get(name);
            WeaponInfo stock = pristine.weapon().get(name);
            assertEquals(factors(stock), factors(now), name);
            assertEquals(stock.bomblet, now.bomblet, name);
            assertEquals(stock.bombletSTime, now.bombletSTime, name);
            assertEquals(stock.isFAE, now.isFAE, name);
            assertEquals(stock.explosionInWater, now.explosionInWater, name);
            assertEquals(stock.dispenseItemLoc, now.dispenseItemLoc, name);
            assertEquals(stock.markTime, now.markTime, name);
        }
        assertNull(yaml.weapon().get("agm114").damageFactor);
    }

    @Test
    void addonWeaponsAreLeftAlone() {
        ContentRegistries.Snapshot yaml = ContentTestSupport.load(ContentTestSupport.stockPack());
        StockWeaponFixes.prepare(yaml.weapon(), info -> false);
        StockWeaponFixes.apply(true);
        assertEquals(0, StockWeaponFixes.preparedCount());
        assertNull(yaml.weapon().get("agm114").damageFactor);
    }

    @Test
    void hoveringLabelConditionIsABooleanTest() {
        assertEquals("hovering", HudFormula.toFormula("hovering==1"));
        assertEquals("!hovering", HudFormula.toFormula("hovering == 0"));
    }

    private static Map<String, Float> factors(WeaponInfo info) {
        return info.damageFactor == null ? Map.of() : info.damageFactor.getFactors();
    }

    private static String modernItem(String legacy) {
        if (legacy == null) {
            return null;
        }
        return switch (legacy) {
            case "dye" -> "bone_meal";
            case "boat" -> "oak_boat";
            default -> legacy;
        };
    }
}
