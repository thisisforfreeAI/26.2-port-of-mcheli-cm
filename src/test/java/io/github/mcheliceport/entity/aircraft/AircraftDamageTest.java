package io.github.mcheliceport.entity.aircraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

/** Exit criterion E2: upstream's armour maths, hit cooldowns and pick-up decisions. */
class AircraftDamageTest {

    @Test
    void damageAfterArmour() {
        AircraftDamage.Armor armor = new AircraftDamage.Armor(2.0F, 50.0F, 0.5F, false);
        RandomSource rand = RandomSource.create(1L);
        assertEquals((20.0F * 0.5F) - 2.0F,
                AircraftDamage.calculate(AircraftDamage.Kind.OTHER, 20.0F, 1.0F, armor, rand, 0).damage(), 1.0E-6F);
        assertEquals((50.0F * 0.5F) - 2.0F,
                AircraftDamage.calculate(AircraftDamage.Kind.PLAYER, 80.0F, 1.0F, armor, rand, 0).damage(), 1.0E-6F,
                "capped at ArmorMaxDamage");
        assertEquals(0.0F, AircraftDamage.calculate(AircraftDamage.Kind.OTHER, 3.0F, 1.0F, armor, rand, 0).damage(),
                "absorbed by ArmorMinDamage");
        assertEquals(20.0F * 0.5F * 0.5F - 2.0F,
                AircraftDamage.calculate(AircraftDamage.Kind.OTHER, 20.0F, 0.5F, armor, rand, 0).damage(), 1.0E-6F,
                "a weak hit box reduces before the armour");
        assertEquals((20.0F * 0.5F - 2.0F) * 3.0F,
                AircraftDamage.calculate(AircraftDamage.Kind.OTHER, 20.0F, 3.0F, armor, rand, 0).damage(), 1.0E-6F,
                "a weak spot multiplies after the armour");

        AircraftDamage.Result explosion = AircraftDamage.calculate(AircraftDamage.Kind.EXPLOSION, 10.0F, 1.0F, armor,
                rand, 7);
        assertEquals(1, explosion.timeSinceHit());
        AircraftDamage.Result fire = AircraftDamage.calculate(AircraftDamage.Kind.ON_FIRE, 1.0F, 1.0F, armor, rand, 0);
        assertEquals(1.0F, fire.damage(), "fire ignores armour");
        assertEquals(10, fire.timeSinceHit());
        AircraftDamage.Result lava = AircraftDamage.calculate(AircraftDamage.Kind.LAVA, 1.0F, 1.0F, armor, rand, 0);
        assertTrue(lava.damage() >= 2.0F && lava.damage() <= 9.0F, "lava ×2 to ×9: " + lava.damage());
        assertEquals(2, lava.timeSinceHit());
        assertEquals(0.0F, AircraftDamage.calculate(AircraftDamage.Kind.OTHER, 100.0F, 1.0F,
                new AircraftDamage.Armor(0, 100, 1, true), rand, 0).damage(), "invulnerable");
        assertEquals(4, AircraftDamage.calculate(AircraftDamage.Kind.OTHER, 10.0F, 1.0F, armor, rand, 4)
                .timeSinceHit(), "other damage keeps the cooldown");
    }

    @Test
    void pickUpRules() {
        assertTrue(AircraftDamage.isPickUp(true, true, true, true, false, true), "creative always");
        assertFalse(AircraftDamage.isPickUp(false, true, false, false, false, false), "creative-only content");
        assertFalse(AircraftDamage.isPickUp(false, false, true, false, false, false), "PreventingBroken");
        assertTrue(AircraftDamage.isPickUp(false, false, false, true, true, true), "pickaxe rule");
        assertFalse(AircraftDamage.isPickUp(false, false, false, true, false, false));
        assertTrue(AircraftDamage.isPickUp(false, false, false, false, false, false), "empty vehicle");
        assertFalse(AircraftDamage.isPickUp(false, false, false, false, false, true), "ridden vehicle");

        assertTrue(AircraftDamage.dropsItem(false, false, false));
        assertTrue(AircraftDamage.dropsItem(true, false, true));
        assertFalse(AircraftDamage.dropsItem(true, true, true), "sneaking flips it in creative");
        assertTrue(AircraftDamage.dropsItem(true, true, false));
        assertFalse(AircraftDamage.dropsItem(true, false, false));
    }
}
