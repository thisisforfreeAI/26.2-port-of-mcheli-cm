package io.github.mcheliceport.effect;

import static org.junit.jupiter.api.Assertions.*;
import io.github.mcheliceport.content.ContentLocation;
import io.github.mcheliceport.content.weapon.WeaponInfo;
import org.junit.jupiter.api.Test;

class BlastStyleTest {
    @Test
    void weaponDefinitionsSelectCosmeticFamilyAndFireTakesPriority() {
        WeaponInfo weapon = new WeaponInfo(new ContentLocation("test", "weapon"), "weapon", "test");
        weapon.type = "machinegun2";
        assertEquals(BlastStyle.CANNON, BlastStyle.forWeapon(weapon, false));
        weapon.type = "BOMB";
        assertEquals(BlastStyle.HEAVY, BlastStyle.forWeapon(weapon, false));
        weapon.type = "atmissile";
        assertEquals(BlastStyle.PENETRATOR, BlastStyle.forWeapon(weapon, false));
        weapon.type = "rocket";
        assertEquals(BlastStyle.BLAST, BlastStyle.forWeapon(weapon, false));
        weapon.isFAE = true;
        assertEquals(BlastStyle.INCENDIARY, BlastStyle.forWeapon(weapon, false));
        weapon.isFAE = false;
        weapon.flaming = true;
        assertEquals(BlastStyle.INCENDIARY, BlastStyle.forWeapon(weapon, false));
        assertEquals(BlastStyle.INCENDIARY, BlastStyle.forWeapon(null, true));
        assertEquals(BlastStyle.BLAST, BlastStyle.forWeapon(null, false));
    }
}
