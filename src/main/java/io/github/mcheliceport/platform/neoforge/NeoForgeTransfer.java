package io.github.mcheliceport.platform.neoforge;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * NeoForge's item energy and fluid access, behind {@link io.github.mcheliceport.compat.energy.EnergyCompat}
 * and {@link io.github.mcheliceport.compat.fluid.FuelFluids}. The port's energy unit is NeoForge's
 * (upstream's internal unit was Forge Energy's, the same scale); a battery is any item with
 * NeoForge's item energy capability.
 */
public final class NeoForgeTransfer {

    private NeoForgeTransfer() {
    }

    /** An item with a fluid capability. */
    public static boolean isFluidContainer(ItemStack stack) {
        return !stack.isEmpty()
                && ItemAccess.forStack(stack.copyWithCount(1)).getCapability(Capabilities.Fluid.ITEM) != null;
    }

    /** See {@link io.github.mcheliceport.platform.Platform#drainContainer}. */
    public static io.github.mcheliceport.platform.Platform.@Nullable Drained drainContainer(ItemStack input,
            java.util.function.Predicate<net.minecraft.world.level.material.Fluid> accepts, int max,
            java.util.function.Predicate<ItemStack> canCommit) {
        net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource> slot =
                net.neoforged.neoforge.transfer.item.VanillaContainerWrapper.of(
                        new net.minecraft.world.SimpleContainer(input.copyWithCount(1)));
        net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.fluid.FluidResource> handler =
                ItemAccess.forHandlerIndexStrict(slot, 0).getCapability(Capabilities.Fluid.ITEM);
        if (handler == null) {
            return null;
        }
        for (int i = 0; i < handler.size(); i++) {
            net.neoforged.neoforge.transfer.fluid.FluidResource resource = handler.getResource(i);
            if (resource.isEmpty() || !accepts.test(resource.getFluid())) {
                continue;
            }
            try (Transaction transaction = Transaction.openRoot()) {
                int drained = handler.extract(i, resource, max, transaction);
                ItemStack container = slot.getResource(0).toStack(slot.getAmountAsInt(0));
                if (drained <= 0 || !canCommit.test(container)) {
                    return null;
                }
                transaction.commit();
                return new io.github.mcheliceport.platform.Platform.Drained(resource.getFluid(), drained, container);
            }
        }
        return null;
    }

    /** The stack's energy handler, working on the stack itself; null when it is not a battery. */
    public static @Nullable EnergyHandler handler(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        return ItemAccess.forStack(stack).getCapability(Capabilities.Energy.ITEM);
    }

    public static int extract(EnergyHandler handler, int max) {
        if (max <= 0) {
            return 0;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            int extracted = Math.max(0, handler.extract(max, transaction));
            transaction.commit();
            return extracted;
        }
    }

    public static int insert(EnergyHandler handler, int max) {
        if (max <= 0) {
            return 0;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            int inserted = Math.max(0, handler.insert(max, transaction));
            transaction.commit();
            return inserted;
        }
    }
}
