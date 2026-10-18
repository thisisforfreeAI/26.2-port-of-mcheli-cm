package io.github.mcheliceport.registry;

import io.github.mcheliceport.McheliCePort;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Upstream {@code MCH_Fluids}: the {@code mch_fuel} fluid, which fuel cans, supply vehicles and
 * the vehicles' tanks all hold. As upstream it has no block, so its bucket cannot be emptied into
 * the world; it moves between fluid containers, tanks and vehicles.
 */
public final class ModFluids {

    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, McheliCePort.MOD_ID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, McheliCePort.MOD_ID);

    /** Upstream's colour for the fluid over water's textures ({@code 0xCC8A5A2B}). */
    public static final int FUEL_COLOR = 0xCC8A5A2B;

    public static final DeferredHolder<FluidType, FluidType> FUEL_TYPE = FLUID_TYPES.register("mch_fuel",
            () -> new FluidType(FluidType.Properties.create()));

    private static final BaseFlowingFluid.Properties FUEL_PROPERTIES = new BaseFlowingFluid.Properties(
            FUEL_TYPE, () -> ModFluids.FUEL.get(), () -> ModFluids.FLOWING_FUEL.get())
            .bucket(() -> ModItems.FUEL_BUCKET.get());

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> FUEL = FLUIDS.register("mch_fuel",
            () -> new BaseFlowingFluid.Source(FUEL_PROPERTIES));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_FUEL = FLUIDS.register(
            "flowing_mch_fuel", () -> new BaseFlowingFluid.Flowing(FUEL_PROPERTIES));

    private ModFluids() {
    }

    static void init() {
    }
}
