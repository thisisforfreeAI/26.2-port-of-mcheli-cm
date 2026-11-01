package io.github.mcheliceport.entity.aircraft;

import io.github.mcheliceport.compat.fluid.FuelFluids;
import java.util.Objects;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * A fuelled vehicle's tank as a NeoForge fluid handler (upstream's {@code IFluidHandler} on
 * {@code MCH_EntityAircraft}): pipes and tanks may fill it with a fuel the vehicle burns, without
 * mixing fuels, and drain what it holds.
 */
final class AircraftFluidHandler extends SnapshotJournal<AircraftFluidHandler.Tank>
        implements ResourceHandler<FluidResource> {

    record Tank(int fuel, String type) {
    }

    private final AircraftEntity aircraft;

    AircraftFluidHandler(AircraftEntity aircraft) {
        this.aircraft = aircraft;
    }

    private @Nullable Fluid contents() {
        return this.aircraft.getFuel() > 0 ? FuelFluids.fluidOf(this.aircraft.getFuelType()) : null;
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public FluidResource getResource(int index) {
        Objects.checkIndex(index, 1);
        Fluid fluid = this.contents();
        return fluid == null ? FluidResource.EMPTY : FluidResource.of(fluid);
    }

    @Override
    public long getAmountAsLong(int index) {
        Objects.checkIndex(index, 1);
        return this.contents() == null ? 0 : this.aircraft.getFuel();
    }

    @Override
    public long getCapacityAsLong(int index, FluidResource resource) {
        Objects.checkIndex(index, 1);
        return this.aircraft.getMaxFuel();
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        Objects.checkIndex(index, 1);
        return !resource.isEmpty() && this.aircraft.getAcInfo() != null
                && FuelFluids.typeOf(this.aircraft.getAcInfo(), resource.getFluid()) != null;
    }

    /** Upstream {@code fill}: no overfilling and no mixing of fuels. */
    @Override
    public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
        Objects.checkIndex(index, 1);
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        if (this.aircraft.getAcInfo() == null || this.aircraft.isElectric() || !resource.isComponentsPatchEmpty()) {
            return 0;
        }
        String type = FuelFluids.typeOf(this.aircraft.getAcInfo(), resource.getFluid());
        int fuel = this.aircraft.getFuel();
        int max = this.aircraft.getMaxFuel();
        if (type == null || fuel >= max || fuel > 0 && !type.equals(this.aircraft.getFuelType())) {
            return 0;
        }
        int accepted = Math.min(amount, max - fuel);
        if (accepted > 0) {
            this.updateSnapshots(transaction);
            this.aircraft.setFuelType(type);
            this.aircraft.setFuel(fuel + accepted);
        }
        return accepted;
    }

    /** Upstream {@code drain}: only the fuel it holds. */
    @Override
    public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
        Objects.checkIndex(index, 1);
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        Fluid fluid = this.contents();
        if (fluid == null || resource.getFluid() != fluid || !resource.isComponentsPatchEmpty()) {
            return 0;
        }
        int drained = Math.min(amount, this.aircraft.getFuel());
        if (drained > 0) {
            this.updateSnapshots(transaction);
            this.aircraft.setFuel(this.aircraft.getFuel() - drained);
        }
        return drained;
    }

    @Override
    protected Tank createSnapshot() {
        return new Tank(this.aircraft.getFuel(), this.aircraft.getFuelType());
    }

    @Override
    protected void revertToSnapshot(Tank snapshot) {
        this.aircraft.setFuelType(snapshot.type());
        this.aircraft.setFuel(snapshot.fuel());
    }
}
