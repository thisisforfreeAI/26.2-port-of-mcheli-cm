package io.github.mcheliceport.registry;

import com.mojang.serialization.Codec;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;

/**
 * The port's item data components: a vehicle's fuel and destruction command on its item
 * (upstream's {@code MCH_Fuel} and {@code MCH_Command} item NBT). The item's damage is vanilla's
 * {@code minecraft:damage}, as upstream used the item damage.
 */
public final class ModDataComponents {

    /** The vehicle's fuel, set when {@code vehicles.itemFuel} is on. */
    public static final Supplier<DataComponentType<Integer>> AIRCRAFT_FUEL =
            ModRegistries.DATA_COMPONENT_TYPES.registerComponentType("aircraft_fuel",
                    b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    /** The command the vehicle runs when destroyed; its presence marks an item that carries vehicle data. */
    public static final Supplier<DataComponentType<String>> AIRCRAFT_COMMAND =
            ModRegistries.DATA_COMPONENT_TYPES.registerComponentType("aircraft_command",
                    b -> b.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));

    private ModDataComponents() {
    }

    /** Loads the class, which registers the components. */
    static void init() {
    }
}
