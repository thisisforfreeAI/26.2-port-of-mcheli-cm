package io.github.mcheliceport.neoforge;

import io.github.mcheliceport.platform.Transfer;
import java.util.function.Predicate;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import org.jspecify.annotations.Nullable;

public final class NeoForgeTransfer implements Transfer {

    @Override
    public boolean isFluidContainer(ItemStack stack) {
        return stack.getCapability(Capabilities.FluidHandler.ITEM) != null;
    }

    @Override
    public @Nullable Drained drain(ItemStack stack, Predicate<Fluid> accept, int max) {
        if (max <= 0) {
            return null;
        }
        ItemStack copy = stack.copyWithCount(1);
        IFluidHandlerItem handler = copy.getCapability(Capabilities.FluidHandler.ITEM);
        if (handler == null) {
            return null;
        }
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            FluidStack contents = handler.getFluidInTank(tank);
            if (contents.isEmpty() || !accept.test(contents.getFluid())) {
                continue;
            }
            FluidStack drained = handler.drain(contents.copyWithAmount(Math.min(max, contents.getAmount())),
                    IFluidHandler.FluidAction.EXECUTE);
            if (!drained.isEmpty()) {
                return new Drained(drained.getFluid(), drained.getAmount(), handler.getContainer());
            }
        }
        return null;
    }

    @Override
    public boolean isBattery(ItemStack stack) {
        return stack.getCapability(Capabilities.EnergyStorage.ITEM) != null;
    }

    @Override
    public int extractEnergy(ItemStack stack, int max) {
        IEnergyStorage energy = stack.getCapability(Capabilities.EnergyStorage.ITEM);
        return energy == null ? 0 : energy.extractEnergy(max, false);
    }

    @Override
    public int insertEnergy(ItemStack stack, int max) {
        IEnergyStorage energy = stack.getCapability(Capabilities.EnergyStorage.ITEM);
        return energy == null ? 0 : energy.receiveEnergy(max, false);
    }
}
