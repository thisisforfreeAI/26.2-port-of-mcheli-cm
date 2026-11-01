package io.github.mcheliceport.shim;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;

/** Small 26.2 to 1.21.1 adapters that do not deserve their own class. */
public final class Compat {

    private Compat() {
    }

    /** 26.2's {@code Item#use} returns a bare result; 1.21.1 wants the held stack with it. */
    public static InteractionResultHolder<ItemStack> holder(InteractionResult result, ItemStack stack) {
        return new InteractionResultHolder<>(result, stack);
    }
}
