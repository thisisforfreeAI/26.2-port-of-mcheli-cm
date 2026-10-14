package io.github.mcheliceport.entity.aircraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Exit criterion E2: upstream's fuel consumption and refuelling rules. */
class AircraftFuelTest {

    @Test
    void fuelBurnsEveryTwentyTicksWithACarry() {
        AircraftFuel.Meter meter = new AircraftFuel.Meter();
        int fuel = 100;
        // Throttle 0.5: min(0.7, 1) × 1.0 × 1.0 = 0.7 per 20 ticks.
        int[] expected = new int[11];
        double carry = 0.0;
        int f = 100;
        for (int i = 1; i <= 10; i++) {
            carry += 0.7;
            if (carry > 1.0) {
                int c = (int) carry;
                carry -= c;
                f -= c;
            }
            expected[i] = f;
        }
        for (int tick = 1; tick <= 200; tick++) {
            fuel = AircraftFuel.consume(meter, tick, fuel, 0.5, 1.0F, 1.0F);
            if (tick % 20 == 0) {
                assertEquals(expected[tick / 20], fuel, "tick " + tick);
            }
        }
        assertEquals(94, fuel);
    }

    @Test
    void fuelRules() {
        AircraftFuel.Meter meter = new AircraftFuel.Meter();
        assertEquals(50, AircraftFuel.consume(meter, 20, 50, 0.0, 5.0F, 1.0F), "engine off");
        assertEquals(1, AircraftFuel.consume(meter, 20, 1, 1.0, 5.0F, 1.0F), "the last unit is kept");
        assertEquals(45, AircraftFuel.consume(meter, 20, 50, 1.0, 5.5F, 1.0F), "full throttle burns 5.5");
        assertEquals(0, AircraftFuel.consume(new AircraftFuel.Meter(), 40, 3, 1.0, 10.0F, 2.0F), "never negative");

        assertTrue(AircraftFuel.canUseFuel(0, 0, false), "no tank");
        assertTrue(AircraftFuel.canUseFuel(100, 2, false));
        assertFalse(AircraftFuel.canUseFuel(100, 1, false));
        assertTrue(AircraftFuel.canUseFuel(100, 0, true), "infinite fuel");

        assertEquals(100, AircraftFuel.refuelAmount(0, 500, 0, 100));
        assertEquals(30, AircraftFuel.refuelAmount(470, 500, 0, 100), "up to the free space");
        assertEquals(40, AircraftFuel.refuelAmount(0, 500, 60, 100));
        assertEquals(0, AircraftFuel.refuelAmount(0, 500, 100, 100), "an empty can");
        assertEquals(0, AircraftFuel.refuelAmount(600, 500, 0, 100), "an overfull tank");
    }
}
