package io.github.mcheliceport.registry;

import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.platform.Services;
import io.github.mcheliceport.platform.registry.DeferredHolder;
import io.github.mcheliceport.platform.registry.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.Fluid;

/**
 * Upstream {@code MCH_Fluids}: the {@code mch_fuel} fluid, which fuel cans, supply vehicles and
 * the vehicles' tanks all hold. As upstream it has no block, so its bucket cannot be emptied into
 * the world; it moves between fluid containers, tanks and vehicles. The loader makes the fluid
 * objects (NeoForge also registers its {@code FluidType}).
 */
public final class ModFluids {

    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, McheliCePort.MOD_ID);

    /** Upstream's colour for the fluid over water's textures ({@code 0xCC8A5A2B}). */
    public static final int FUEL_COLOR = 0xCC8A5A2B;

    public static final DeferredHolder<Fluid, FuelFluid.Source> FUEL = FLUIDS.register("mch_fuel",
            () -> Services.REGISTRAR.fuelSource());
    public static final DeferredHolder<Fluid, FuelFluid.Flowing> FLOWING_FUEL = FLUIDS.register(
            "flowing_mch_fuel", () -> Services.REGISTRAR.fuelFlowing());

    private ModFluids() {
    }

    static void init() {
    }
}
