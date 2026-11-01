package io.github.mcheliceport.entity.aircraft;

import io.github.mcheliceport.compat.fluid.FuelFluids;
import net.minecraft.world.level.material.Fluid;
import org.jspecify.annotations.Nullable;

/**
 * A fuelled vehicle's tank for pipes and tanks (upstream's {@code IFluidHandler} on
 * {@code MCH_EntityAircraft}): they may fill it with a fuel the vehicle burns, without mixing
 * fuels, and drain what it holds. Loader-neutral; the NeoForge module exposes it as a fluid
 * capability.
 */
public final class AircraftFluidHandler {

    private final AircraftEntity aircraft;

    AircraftFluidHandler(AircraftEntity aircraft) {
        this.aircraft = aircraft;
    }

    /** The fluid in the tank, or null when it is empty. */
    public @Nullable Fluid contents() {
        return this.aircraft.getFuel() > 0 ? FuelFluids.fluidOf(this.aircraft.getFuelType()) : null;
    }

    public int amount() {
        return this.contents() == null ? 0 : this.aircraft.getFuel();
    }

    public int capacity() {
        return this.aircraft.getMaxFuel();
    }

    public boolean isValid(Fluid fluid) {
        return this.aircraft.getAcInfo() != null && FuelFluids.typeOf(this.aircraft.getAcInfo(), fluid) != null;
    }

    /** Upstream {@code fill}: no overfilling and no mixing of fuels. {@code hasData}: the fluid carries components. */
    public int insert(Fluid fluid, int amount, boolean hasData, boolean simulate) {
        if (amount <= 0 || this.aircraft.getAcInfo() == null || this.aircraft.isElectric() || hasData) {
            return 0;
        }
        String type = FuelFluids.typeOf(this.aircraft.getAcInfo(), fluid);
        int fuel = this.aircraft.getFuel();
        int max = this.aircraft.getMaxFuel();
        if (type == null || fuel >= max || fuel > 0 && !type.equals(this.aircraft.getFuelType())) {
            return 0;
        }
        int accepted = Math.min(amount, max - fuel);
        if (accepted > 0 && !simulate) {
            this.aircraft.setFuelType(type);
            this.aircraft.setFuel(fuel + accepted);
        }
        return accepted;
    }

    /** Upstream {@code drain}: only the fuel it holds. {@code fluid} null drains whatever it holds. */
    public int extract(@Nullable Fluid fluid, int amount, boolean simulate) {
        Fluid held = this.contents();
        if (amount <= 0 || held == null || fluid != null && fluid != held) {
            return 0;
        }
        int drained = Math.min(amount, this.aircraft.getFuel());
        if (drained > 0 && !simulate) {
            this.aircraft.setFuel(this.aircraft.getFuel() - drained);
        }
        return drained;
    }
}
