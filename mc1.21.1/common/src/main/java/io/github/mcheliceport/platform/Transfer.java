package io.github.mcheliceport.platform;

import java.util.function.Predicate;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import org.jspecify.annotations.Nullable;

/** Fluid and energy items, through each loader's transfer API. */
public interface Transfer {

    /** Whether a single item of {@code stack} holds or can hold fluid. */
    boolean isFluidContainer(ItemStack stack);

    /**
     * Drains up to {@code max} of the first fluid in one item of {@code stack} that {@code accept}
     * takes, without touching {@code stack}. Returns null when nothing could be drained.
     */
    @Nullable Drained drain(ItemStack stack, Predicate<Fluid> accept, int max);

    /** {@code container} is what is left of the item after draining. */
    record Drained(Fluid fluid, int amount, ItemStack container) {
    }

    boolean isBattery(ItemStack stack);

    /** Takes up to {@code max} energy out of the battery stack (changing the stack); returns how much. */
    int extractEnergy(ItemStack stack, int max);

    /** Puts up to {@code max} energy into the battery stack (changing the stack); returns how much. */
    int insertEnergy(ItemStack stack, int max);
}
