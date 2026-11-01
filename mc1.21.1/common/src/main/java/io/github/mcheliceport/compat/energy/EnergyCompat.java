package io.github.mcheliceport.compat.energy;

import io.github.mcheliceport.platform.Services;
import net.minecraft.world.item.ItemStack;

/**
 * Battery items for electric vehicles and the charger (upstream {@code MCH_EnergyCompat}). A
 * battery is any item with the loader's item energy capability: NeoForge's energy storage, or on
 * Fabric the Team Reborn Energy API when a mod provides it. Upstream's GregTech EU and HBM HE
 * bridges are dropped (PORTING_PLAN.md section 5.9).
 */
public final class EnergyCompat {

    private EnergyCompat() {
    }

    /** Upstream {@code isBattery}. */
    public static boolean isBattery(ItemStack stack) {
        return !stack.isEmpty() && Services.TRANSFER.isBattery(stack);
    }

    /** Upstream {@code dischargeFromItem}: takes up to {@code max} energy out of a battery. */
    public static int dischargeFromItem(ItemStack stack, int max) {
        return max <= 0 || stack.isEmpty() ? 0 : Math.max(0, Services.TRANSFER.extractEnergy(stack, max));
    }

    /** Upstream {@code chargeToItem}: puts up to {@code max} energy into a battery. */
    public static int chargeToItem(ItemStack stack, int max) {
        return max <= 0 || stack.isEmpty() ? 0 : Math.max(0, Services.TRANSFER.insertEnergy(stack, max));
    }
}
