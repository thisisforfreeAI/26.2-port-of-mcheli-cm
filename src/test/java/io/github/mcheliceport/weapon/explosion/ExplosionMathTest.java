package io.github.mcheliceport.weapon.explosion;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Exit criterion E3: entity damage against upstream {@code MCH_ExplosionV2#doExplosionA}. */
class ExplosionMathTest {

    /** Upstream's lines, transcribed: d7 is distance / (2 × radius), density the block density. */
    private static float upstream(double d7, double blockDensity, float damagePower, boolean throughWall,
            float throughWallFactor) {
        double d9 = throughWall ? 1.0 : blockDensity;
        double d10 = (1.0 - d7) * d9;
        float damage = (int) ((d10 * d10 + d10) / 2.0 * 8.0 * damagePower + 1.0);
        if (throughWall && blockDensity < 1.0) {
            damage *= Math.max(0.0F, Math.min(1.0F, throughWallFactor));
        }
        return damage;
    }

    @Test
    void damageMatchesUpstream() {
        double[] distances = {0.0, 0.1, 0.25, 0.5, 0.75, 0.99, 1.0};
        double[] densities = {0.0, 0.3, 0.5, 1.0};
        float[] powers = {0.5F, 1.0F, 2.0F, 4.0F, 15.0F};
        for (double d7 : distances) {
            for (double density : densities) {
                for (float power : powers) {
                    for (boolean wall : new boolean[] {false, true}) {
                        for (float factor : new float[] {0.0F, 0.5F, 1.0F, 2.0F}) {
                            assertEquals(upstream(d7, density, power, wall, factor),
                                    McheliExplosion.entityDamage(1.0 - d7, density, power, wall, factor));
                        }
                    }
                }
            }
        }
    }

    @Test
    void knownValues() {
        // Point blank, nothing in the way, power 2: (1 + 1) / 2 * 8 * 2 + 1 = 17.
        assertEquals(17.0F, McheliExplosion.entityDamage(1.0, 1.0, 2.0F, false, 1.0F));
        // At the edge only the +1 remains.
        assertEquals(1.0F, McheliExplosion.entityDamage(0.0, 1.0, 2.0F, false, 1.0F));
        // Behind a wall a through-wall blast hits fully, then takes the factor.
        assertEquals(8.5F, McheliExplosion.entityDamage(1.0, 0.0, 2.0F, true, 0.5F));
    }
}
