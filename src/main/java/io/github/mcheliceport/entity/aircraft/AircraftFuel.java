package io.github.mcheliceport.entity.aircraft;

/**
 * Fuel rules of upstream's {@code MCH_EntityAircraft}: consumption every 20 ticks with a carried
 * fraction ({@code updateFuel}), the "may the engine run" test ({@code canUseFuel}) and refuelling
 * from a fuel can ({@code handleItemFuelRefuel}).
 */
public final class AircraftFuel {

    /** Upstream's fuel fluid; the port has no fluids yet, so it is only a name. */
    public static final String DEFAULT_FUEL_TYPE = "mch_fuel";

    private AircraftFuel() {
    }

    /** The per-vehicle consumption state upstream keeps in {@code fuelConsumption}. */
    public static final class Meter {
        public double carry;
    }

    /**
     * Upstream {@code canUseFuel}: a vehicle without a tank, with more than 1 fuel, or in infinite
     * fuel mode may run.
     */
    public static boolean canUseFuel(int maxFuel, int fuel, boolean infinity) {
        return maxFuel <= 0 || fuel > 1 || infinity;
    }

    /**
     * The consumption half of upstream {@code updateFuel}: on every 20th tick with fuel above 1 and
     * a running engine, adds {@code min(throttle × 1.4, 1) × consumption × fuelFactor} to the carry
     * and burns its whole units.
     *
     * @param syncedThrottle the synced throttle ({@code getThrottle}, steps of 0.05)
     * @return the fuel left
     */
    public static int consume(Meter meter, int tickCount, int fuel, double syncedThrottle, float fuelConsumption,
            float fuelFactor) {
        if (tickCount % 20 == 0 && fuel > 1 && syncedThrottle > 0.0) {
            double throttleFactor = Math.min(syncedThrottle * 1.4, 1.0);
            meter.carry += throttleFactor * fuelConsumption * fuelFactor;
            if (meter.carry > 1.0) {
                int toConsume = (int) meter.carry;
                meter.carry -= toConsume;
                return Math.max(fuel - toConsume, 0);
            }
        }
        return fuel;
    }

    /**
     * Upstream {@code handleItemFuelRefuel}: how much fuel a fuel can with the given damage moves
     * into a tank (its remaining durability, up to the free space).
     */
    public static int refuelAmount(int fuel, int maxFuel, int canDamage, int canMaxDamage) {
        int fuelInItem = canMaxDamage - canDamage;
        if (fuelInItem <= 0) {
            return 0;
        }
        return Math.max(Math.min(maxFuel - fuel, fuelInItem), 0);
    }
}
