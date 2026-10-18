package io.github.mcheliceport.compat.energy;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * Battery items for electric vehicles and the charger (upstream {@code MCH_EnergyCompat}). The
 * port's energy unit is NeoForge's (upstream's internal unit was Forge Energy's, the same scale);
 * a battery is any item with NeoForge's item energy capability. Upstream's GregTech EU and HBM HE
 * bridges are dropped (PORTING_PLAN.md section 5.9).
 */
public final class EnergyCompat {

    private EnergyCompat() {
    }

    /** The stack's energy handler, working on the stack itself; null when it is not a battery. */
    public static @Nullable EnergyHandler handler(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        return ItemAccess.forStack(stack).getCapability(Capabilities.Energy.ITEM);
    }

    /** Upstream {@code isBattery}. */
    public static boolean isBattery(ItemStack stack) {
        return handler(stack) != null;
    }

    /** Upstream {@code dischargeFromItem}: takes up to {@code max} energy out of a battery. */
    public static int dischargeFromItem(ItemStack stack, int max) {
        EnergyHandler handler = handler(stack);
        return handler != null ? extract(handler, max) : 0;
    }

    /** Upstream {@code chargeToItem}: puts up to {@code max} energy into a battery. */
    public static int chargeToItem(ItemStack stack, int max) {
        EnergyHandler handler = handler(stack);
        return handler != null ? insert(handler, max) : 0;
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
