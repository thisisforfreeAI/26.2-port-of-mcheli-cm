package io.github.mcheliceport.mixin.client;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The mouse movement gathered this frame, which NeoForge and 26.2 expose as velocity. */
@Mixin(MouseHandler.class)
public interface MouseHandlerAccessor {
    @Accessor("accumulatedDX")
    double mcheli$dx();

    @Accessor("accumulatedDY")
    double mcheli$dy();
}
