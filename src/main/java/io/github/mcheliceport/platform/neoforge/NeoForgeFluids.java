package io.github.mcheliceport.platform.neoforge;

import io.github.mcheliceport.registry.ModFluids;
import io.github.mcheliceport.registry.ModItems;
import io.github.mcheliceport.registry.ModRegister;
import io.github.mcheliceport.registry.RegEntry;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** NeoForge's fluid type for {@code mch_fuel}, which {@link ModFluids}' fluids share. */
final class NeoForgeFluids {
    static final ModRegister<FluidType> FLUID_TYPES = ModRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES);
    static final RegEntry<FluidType, FluidType> FUEL_TYPE = FLUID_TYPES.register("mch_fuel",
            () -> new FluidType(FluidType.Properties.create()));
    static final BaseFlowingFluid.Properties FUEL_PROPERTIES = new BaseFlowingFluid.Properties(
            FUEL_TYPE, ModFluids.FUEL, ModFluids.FLOWING_FUEL)
            .bucket(ModItems.FUEL_BUCKET);

    private NeoForgeFluids() {
    }
}
