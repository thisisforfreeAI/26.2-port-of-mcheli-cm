package io.github.mcheliceport.weapon.explosion;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** The lowest row a wreck's blast may break keeps the block the wreck rests on. */
class WreckGroundLevelTest {

    @Test
    void keepsTheBlockUnderTheWreck() {
        // On a full block at y 63 the feet are at 64: row 63 stays, 64 may go.
        assertEquals(64, McheliExplosion.groundLevel(64.0));
        assertEquals(64, McheliExplosion.groundLevel(63.9999));
        assertEquals(64, McheliExplosion.groundLevel(64.001));
        // On a bottom slab at 63 the feet are at 63.5: the slab stays too.
        assertEquals(64, McheliExplosion.groundLevel(63.5));
        assertEquals(-3, McheliExplosion.groundLevel(-3.0));
    }
}
