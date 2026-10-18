package io.github.mcheliceport.compat.fluid;

import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.entity.aircraft.AircraftFuel;
import io.github.mcheliceport.registry.ModFluids;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import org.jspecify.annotations.Nullable;

/**
 * Fuel type names and fluids. Definitions and saved vehicles name fuels as 1.12's Forge fluid
 * registry did ({@code FuelType}, {@code AcFuelType}): unprefixed names such as {@code mch_fuel}.
 * Here such a name means the port's fuel for {@code mch_fuel}, otherwise the source fluid of any
 * namespace with that path; a name with a namespace means exactly that fluid.
 */
public final class FuelFluids {

    private FuelFluids() {
    }

    /** The fluid a fuel type name stands for, or {@code null} when none is registered. */
    public static @Nullable Fluid fluidOf(@Nullable String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        if (name.equals(AircraftFuel.DEFAULT_FUEL_TYPE)) {
            return ModFluids.FUEL.get();
        }
        if (name.indexOf(':') >= 0) {
            Identifier id = Identifier.tryParse(name);
            return id == null ? null : BuiltInRegistries.FLUID.getOptional(id).map(FuelFluids::source).orElse(null);
        }
        for (Fluid fluid : BuiltInRegistries.FLUID) {
            if (fluid == source(fluid) && BuiltInRegistries.FLUID.getKey(fluid).getPath().equals(name)) {
                return fluid;
            }
        }
        return null;
    }

    /**
     * The name under which a definition accepts a fluid (one of its {@code FuelType} names), or
     * {@code null} when the vehicle cannot burn it (upstream {@code isFuelValid}).
     */
    public static @Nullable String typeOf(AircraftInfo info, Fluid fluid) {
        Fluid source = source(fluid);
        for (String name : info.getFluidType().keySet()) {
            if (fluidOf(name) == source) {
                return name;
            }
        }
        return null;
    }

    /** Upstream's fuel slot test: an item with a fluid capability. */
    public static boolean isFluidContainer(ItemStack stack) {
        return !stack.isEmpty()
                && ItemAccess.forStack(stack.copyWithCount(1)).getCapability(Capabilities.Fluid.ITEM) != null;
    }

    private static Fluid source(Fluid fluid) {
        return fluid instanceof FlowingFluid flowing ? flowing.getSource() : fluid;
    }
}
