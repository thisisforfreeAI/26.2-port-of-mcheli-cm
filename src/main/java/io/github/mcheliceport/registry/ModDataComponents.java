package io.github.mcheliceport.registry;

import com.mojang.serialization.Codec;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;

/**
 * The port's item data components: a vehicle's fuel and destruction command on its item
 * (upstream's {@code MCH_Fuel} and {@code MCH_Command} item NBT). The item's damage is vanilla's
 * {@code minecraft:damage}, as upstream used the item damage.
 */
public final class ModDataComponents {
    public static final Supplier<DataComponentType<java.util.UUID>> UAV_BOUND =
            ModRegistries.DATA_COMPONENT_TYPES.registerComponentType("uav_bound",
                    b -> b.persistent(net.minecraft.core.UUIDUtil.CODEC).networkSynchronized(net.minecraft.core.UUIDUtil.STREAM_CODEC));
    public static final Supplier<DataComponentType<io.github.mcheliceport.item.UavTabletData>> UAV_TABLET =
            ModRegistries.DATA_COMPONENT_TYPES.registerComponentType("uav_tablet",
                    b -> b.persistent(io.github.mcheliceport.item.UavTabletData.CODEC).networkSynchronized(io.github.mcheliceport.item.UavTabletData.STREAM_CODEC));

    /** The vehicle's fuel, set when {@code vehicles.itemFuel} is on. */
    public static final Supplier<DataComponentType<Integer>> AIRCRAFT_FUEL =
            ModRegistries.DATA_COMPONENT_TYPES.registerComponentType("aircraft_fuel",
                    b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    /** The command the vehicle runs when destroyed; its presence marks an item that carries vehicle data. */
    public static final Supplier<DataComponentType<String>> AIRCRAFT_COMMAND =
            ModRegistries.DATA_COMPONENT_TYPES.registerComponentType("aircraft_command",
                    b -> b.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));

    /** Loaded and reserve ammunition pairs, in vehicle weapon-set order. */
    public static final Supplier<DataComponentType<List<Integer>>> AIRCRAFT_AMMO =
            ModRegistries.DATA_COMPONENT_TYPES.registerComponentType("aircraft_ammo",
                    b -> b.persistent(Codec.INT.listOf())
                            .networkSynchronized(ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list())));

    private ModDataComponents() {
    }

    /** Loads the class, which registers the components. */
    static void init() {
    }
}
