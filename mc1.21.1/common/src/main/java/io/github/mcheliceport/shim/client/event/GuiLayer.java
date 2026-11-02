package io.github.mcheliceport.shim.client.event;

import io.github.mcheliceport.shim.client.GuiGraphicsExtractor;
import net.minecraft.client.DeltaTracker;

/** A HUD layer: NeoForge's {@code GuiLayer} with 26.2's graphics. */
@FunctionalInterface
public interface GuiLayer {
    void render(GuiGraphicsExtractor graphics, DeltaTracker delta);
}
