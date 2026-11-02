package io.github.mcheliceport.fabricmixin.client;

import io.github.mcheliceport.platform.fabric.inject.GuiGraphicsExtras;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Implements {@link GuiGraphicsExtras} as NeoForge's patch does. */
@Mixin(GuiGraphicsExtractor.class)
abstract class GuiGraphicsExtractorMixin implements GuiGraphicsExtras {
    @Shadow @Final private GuiGraphicsExtractor.ScissorStack scissorStack;
    @Shadow @Final private GuiRenderState guiRenderState;

    @Override
    public @Nullable ScreenRectangle peekScissorStack() {
        return this.scissorStack.peek();
    }

    @Override
    public void submitGuiElementRenderState(GuiElementRenderState renderState) {
        this.guiRenderState.addGuiElement(renderState);
    }

    @Override
    public void submitPictureInPictureRenderState(PictureInPictureRenderState renderState) {
        this.guiRenderState.addPicturesInPictureState(renderState);
    }
}
