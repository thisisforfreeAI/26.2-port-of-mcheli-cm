package io.github.mcheliceport.entity.aircraft;

import io.github.mcheliceport.registry.ModDataComponents;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The vehicle data an item carries: upstream's {@code setAcDataToItem} and
 * {@code getAcDataFromItem}, on item data components.
 */
public final class AircraftItemData {

    private AircraftItemData() {
    }

    /** The values read from an item; {@code fuel} and {@code damage} are null when their option is off. */
    public record Values(String command, @Nullable Integer fuel, @Nullable Integer damage) {
    }

    /** Upstream {@code setAcDataToItem}. */
    public static void write(ItemStack stack, String command, int fuel, int damage, boolean itemFuel,
            boolean itemDamage) {
        stack.set(ModDataComponents.AIRCRAFT_COMMAND.get(), command);
        if (itemFuel) {
            stack.set(ModDataComponents.AIRCRAFT_FUEL.get(), fuel);
        }
        if (itemDamage && stack.isDamageableItem()) {
            stack.setDamageValue(damage);
        }
    }

    /**
     * Upstream {@code getAcDataFromItem}: nothing unless the item carries vehicle data (upstream
     * checked for any NBT tag; the port checks for the command component, which every write sets).
     */
    public static @Nullable Values read(ItemStack stack, boolean itemFuel, boolean itemDamage) {
        String command = stack.get(ModDataComponents.AIRCRAFT_COMMAND.get());
        if (command == null) {
            return null;
        }
        Integer fuel = itemFuel ? stack.getOrDefault(ModDataComponents.AIRCRAFT_FUEL.get(), 0) : null;
        Integer damage = itemDamage ? stack.getDamageValue() : null;
        return new Values(command, fuel, damage);
    }
}
