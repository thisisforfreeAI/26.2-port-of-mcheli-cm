package io.github.mcheliceport.registry;

import io.github.mcheliceport.platform.Platform;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;

/**
 * Upstream {@code MCH_Fluids}: the {@code mch_fuel} fluid, which fuel cans, supply vehicles and
 * the vehicles' tanks all hold. As upstream it has no block, so its bucket cannot be emptied into
 * the world; it moves between fluid containers, tanks and vehicles. Each loader builds the fluid
 * objects ({@link Platform#createFuelFluid}).
 */
public final class ModFluids {
    public static final ModRegister<Fluid> FLUIDS = ModRegister.create(Registries.FLUID);

    /** Upstream's colour for the fluid over water's textures ({@code 0xCC8A5A2B}). */
    public static final int FUEL_COLOR = 0xCC8A5A2B;

    public static final RegEntry<Fluid, FlowingFluid> FUEL = FLUIDS.register("mch_fuel",
            () -> Platform.get().createFuelFluid(true));
    public static final RegEntry<Fluid, FlowingFluid> FLOWING_FUEL = FLUIDS.register("flowing_mch_fuel",
            () -> Platform.get().createFuelFluid(false));

    private ModFluids() {
    }

    static void init() {
    }
}
