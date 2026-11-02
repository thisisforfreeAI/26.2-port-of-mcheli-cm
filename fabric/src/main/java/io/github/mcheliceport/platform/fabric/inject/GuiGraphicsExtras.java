package io.github.mcheliceport.platform.fabric.inject;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.jspecify.annotations.Nullable;

/** NeoForge's additions to {@code GuiGraphicsExtractor}. */
public interface GuiGraphicsExtras {
    default @Nullable ScreenRectangle peekScissorStack() {
        throw new AssertionError("implemented by GuiGraphicsExtractorMixin");
    }

    default void submitGuiElementRenderState(GuiElementRenderState renderState) {
        throw new AssertionError("implemented by GuiGraphicsExtractorMixin");
    }

    default void submitPictureInPictureRenderState(PictureInPictureRenderState renderState) {
        throw new AssertionError("implemented by GuiGraphicsExtractorMixin");
    }
}
