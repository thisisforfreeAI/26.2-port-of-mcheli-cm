package io.github.mcheliceport.compat.energy;

import io.github.mcheliceport.platform.Platform;
import net.minecraft.world.item.ItemStack;

/**
 * Battery items for electric vehicles and the charger (upstream {@code MCH_EnergyCompat}): any item
 * the loader's item energy API knows. Upstream's GregTech EU and HBM HE bridges are dropped
 * (PORTING_PLAN.md section 5.9).
 */
public final class EnergyCompat {

    private EnergyCompat() {
    }

    /** Upstream {@code isBattery}. */
    public static boolean isBattery(ItemStack stack) {
        return !stack.isEmpty() && Platform.get().isBattery(stack);
    }

    /** Upstream {@code dischargeFromItem}: takes up to {@code max} energy out of a battery. */
    public static int dischargeFromItem(ItemStack stack, int max) {
        return stack.isEmpty() || max <= 0 ? 0 : Platform.get().extractItemEnergy(stack, max);
    }

    /** Upstream {@code chargeToItem}: puts up to {@code max} energy into a battery. */
    public static int chargeToItem(ItemStack stack, int max) {
        return stack.isEmpty() || max <= 0 ? 0 : Platform.get().insertItemEnergy(stack, max);
    }
}
