package io.github.mcheliceport.neoforge;

import io.github.mcheliceport.block.ChargerBlockEntity;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.aircraft.AircraftFluidHandler;
import io.github.mcheliceport.registry.ModBlocks;
import io.github.mcheliceport.registry.ModEntityTypes;
import io.github.mcheliceport.registry.ModItems;
import java.util.List;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.wrappers.FluidBucketWrapper;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

/** The 26.2 port's capabilities: cargo inventories, the charger's energy, vehicle tanks and the fuel bucket. */
final class NeoForgeCapabilities {

    private NeoForgeCapabilities() {
    }

    static void register(IEventBus modBus) {
        modBus.addListener((RegisterCapabilitiesEvent event) -> {
            event.registerEntity(Capabilities.ItemHandler.ENTITY, ModEntityTypes.CONTAINER.get(),
                    (cargo, context) -> new InvWrapper(cargo));
            event.registerEntity(Capabilities.ItemHandler.ENTITY_AUTOMATION, ModEntityTypes.CONTAINER.get(),
                    (cargo, side) -> new InvWrapper(cargo));
            event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlocks.CHARGER_ENTITY.get(),
                    (charger, side) -> energy(charger.energyHandler()));
            for (var type : List.of(ModEntityTypes.HELICOPTER, ModEntityTypes.PLANE, ModEntityTypes.SHIP,
                    ModEntityTypes.TANK, ModEntityTypes.VEHICLE)) {
                event.registerEntity(Capabilities.FluidHandler.ENTITY, type.get(), (aircraft, side) -> {
                    AircraftFluidHandler tank = ((AircraftEntity) aircraft).fluidHandler();
                    return tank == null ? null : fluid(tank);
                });
            }
            event.registerItem(Capabilities.FluidHandler.ITEM, (stack, context) -> new FluidBucketWrapper(stack),
                    ModItems.FUEL_BUCKET.get());
        });
    }

    private static IEnergyStorage energy(ChargerBlockEntity.Handler handler) {
        return new IEnergyStorage() {
            @Override
            public int receiveEnergy(int max, boolean simulate) {
                return handler.insert(max, simulate);
            }

            @Override
            public int extractEnergy(int max, boolean simulate) {
                return 0;
            }

            @Override
            public int getEnergyStored() {
                return handler.amount();
            }

            @Override
            public int getMaxEnergyStored() {
                return handler.capacity();
            }

            @Override
            public boolean canExtract() {
                return false;
            }

            @Override
            public boolean canReceive() {
                return true;
            }
        };
    }

    private static IFluidHandler fluid(AircraftFluidHandler tank) {
        return new IFluidHandler() {
            @Override
            public int getTanks() {
                return 1;
            }

            @Override
            public FluidStack getFluidInTank(int index) {
                Fluid held = tank.contents();
                return held == null ? FluidStack.EMPTY : new FluidStack(held, tank.amount());
            }

            @Override
            public int getTankCapacity(int index) {
                return tank.capacity();
            }

            @Override
            public boolean isFluidValid(int index, FluidStack stack) {
                return tank.isValid(stack.getFluid());
            }

            @Override
            public int fill(FluidStack resource, FluidAction action) {
                return resource.isEmpty() ? 0 : tank.insert(resource.getFluid(), resource.getAmount(),
                        !resource.getComponentsPatch().isEmpty(), action.simulate());
            }

            @Override
            public FluidStack drain(FluidStack resource, FluidAction action) {
                if (resource.isEmpty() || !resource.getComponentsPatch().isEmpty()) {
                    return FluidStack.EMPTY;
                }
                int amount = tank.extract(resource.getFluid(), resource.getAmount(), action.simulate());
                return amount <= 0 ? FluidStack.EMPTY : resource.copyWithAmount(amount);
            }

            @Override
            public FluidStack drain(int max, FluidAction action) {
                Fluid held = tank.contents();
                int amount = held == null ? 0 : tank.extract(held, max, action.simulate());
                return amount <= 0 ? FluidStack.EMPTY : new FluidStack(held, amount);
            }
        };
    }
}
