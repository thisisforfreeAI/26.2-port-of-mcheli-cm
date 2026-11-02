package io.github.mcheliceport.fabric;

import io.github.mcheliceport.platform.Transfer;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.item.base.SingleStackStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import org.jspecify.annotations.Nullable;

/**
 * Fabric's transfer API for fluid containers, in millibuckets (81 droplets each). Fabric API has no
 * energy API, so there are no batteries on Fabric.
 */
public final class FabricTransfer implements Transfer {

    private static final long DROPLETS_PER_MB = FluidConstants.BUCKET / 1000;

    @Override
    public boolean isFluidContainer(ItemStack stack) {
        return FluidStorage.ITEM.find(stack, ContainerItemContext.withConstant(stack)) != null;
    }

    @Override
    public @Nullable Drained drain(ItemStack stack, Predicate<Fluid> accept, int max) {
        if (max <= 0) {
            return null;
        }
        ItemStack[] slot = {stack.copyWithCount(1)};
        SingleStackStorage holder = new SingleStackStorage() {
            @Override
            protected ItemStack getStack() {
                return slot[0];
            }

            @Override
            protected void setStack(ItemStack s) {
                slot[0] = s;
            }
        };
        ContainerItemContext context = ContainerItemContext.ofSingleSlot(holder);
        Storage<FluidVariant> storage = context.find(FluidStorage.ITEM);
        if (storage == null) {
            return null;
        }
        for (StorageView<FluidVariant> view : storage.nonEmptyViews()) {
            FluidVariant variant = view.getResource();
            if (variant.isBlank() || !accept.test(variant.getFluid())) {
                continue;
            }
            try (Transaction transaction = Transaction.openOuter()) {
                long extracted = storage.extract(variant, max * DROPLETS_PER_MB, transaction);
                int mb = (int) (extracted / DROPLETS_PER_MB);
                if (mb > 0) {
                    transaction.commit();
                    return new Drained(variant.getFluid(), mb, slot[0]);
                }
            }
        }
        return null;
    }

    @Override
    public boolean isBattery(ItemStack stack) {
        return false;
    }

    @Override
    public int extractEnergy(ItemStack stack, int max) {
        return 0;
    }

    @Override
    public int insertEnergy(ItemStack stack, int max) {
        return 0;
    }
}
