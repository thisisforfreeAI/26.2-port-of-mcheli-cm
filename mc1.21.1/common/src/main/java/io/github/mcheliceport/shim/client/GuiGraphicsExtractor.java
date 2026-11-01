package io.github.mcheliceport.shim.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

/**
 * 1.21.1 stand-in for 26.2's {@code GuiGraphicsExtractor}, drawing straight through
 * {@link GuiGraphics}. 26.2's 2D pose ({@link #pose()}) is a {@link Matrix3x2fStack}; every draw
 * applies it on top of the vanilla pose stack.
 */
public final class GuiGraphicsExtractor {

    private final GuiGraphics graphics;
    private final Matrix3x2fStack pose = new Matrix3x2fStack(16);
    private final Deque<ScreenRectangle> scissors = new ArrayDeque<>();

    private GuiGraphicsExtractor(GuiGraphics graphics) {
        this.graphics = graphics;
    }

    private static @Nullable GuiGraphicsExtractor last;

    /** The extractor for {@code graphics}, reused while the same vanilla object is passed in. */
    public static GuiGraphicsExtractor of(GuiGraphics graphics) {
        GuiGraphicsExtractor current = last;
        if (current == null || current.graphics != graphics) {
            current = new GuiGraphicsExtractor(graphics);
            last = current;
        }
        return current;
    }

    public GuiGraphics vanilla() {
        return graphics;
    }

    public int guiWidth() {
        return graphics.guiWidth();
    }

    public int guiHeight() {
        return graphics.guiHeight();
    }

    public Matrix3x2fStack pose() {
        return pose;
    }

    /** Runs {@code draw} with the 2D pose applied. */
    public void withPose(Runnable draw) {
        PoseStack stack = graphics.pose();
        stack.pushPose();
        stack.mulPose(toMatrix4f(pose));
        try {
            draw.run();
        } finally {
            stack.popPose();
        }
    }

    public static Matrix4f toMatrix4f(Matrix3x2f m) {
        return new Matrix4f(m.m00(), m.m01(), 0, 0, m.m10(), m.m11(), 0, 0, 0, 0, 1, 0, m.m20(), m.m21(), 0, 1);
    }

    /** Ends 26.2's stratum (a later layer drawn on top); immediate drawing needs only a flush. */
    public void nextStratum() {
        graphics.flush();
    }

    // ---- text ----

    public void text(Font font, @Nullable String text, int x, int y, int color) {
        text(font, text, x, y, color, true);
    }

    public void text(Font font, @Nullable String text, int x, int y, int color, boolean shadow) {
        withPose(() -> graphics.drawString(font, text, x, y, color, shadow));
    }

    public void text(Font font, Component text, int x, int y, int color) {
        text(font, text, x, y, color, true);
    }

    public void text(Font font, Component text, int x, int y, int color, boolean shadow) {
        withPose(() -> graphics.drawString(font, text, x, y, color, shadow));
    }

    public void text(Font font, FormattedCharSequence text, int x, int y, int color) {
        text(font, text, x, y, color, true);
    }

    public void text(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
        withPose(() -> graphics.drawString(font, text, x, y, color, shadow));
    }

    public void centeredText(Font font, String text, int x, int y, int color) {
        withPose(() -> graphics.drawCenteredString(font, text, x, y, color));
    }

    public void centeredText(Font font, Component text, int x, int y, int color) {
        withPose(() -> graphics.drawCenteredString(font, text, x, y, color));
    }

    public void centeredText(Font font, FormattedCharSequence text, int x, int y, int color) {
        withPose(() -> graphics.drawCenteredString(font, text, x, y, color));
    }

    // ---- shapes and textures ----

    public void fill(int x1, int y1, int x2, int y2, int color) {
        withPose(() -> graphics.fill(x1, y1, x2, y2, color));
    }

    public void fill(RenderPipelines.Pipeline pipeline, int x1, int y1, int x2, int y2, int color) {
        fill(x1, y1, x2, y2, color);
    }

    public void hLine(int x1, int x2, int y, int color) {
        withPose(() -> graphics.hLine(x1, x2, y, color));
    }

    public void vLine(int x, int y1, int y2, int color) {
        withPose(() -> graphics.vLine(x, y1, y2, color));
    }

    public void outline(int x, int y, int width, int height, int color) {
        withPose(() -> graphics.renderOutline(x, y, width, height, color));
    }

    public void fillGradient(int x1, int y1, int x2, int y2, int top, int bottom) {
        withPose(() -> graphics.fillGradient(x1, y1, x2, y2, top, bottom));
    }

    /** {@code blit(pipeline, texture, x, y, u, v, width, height, textureWidth, textureHeight)}. */
    public void blit(RenderPipelines.Pipeline pipeline, ResourceLocation texture, int x, int y, float u, float v,
            int width, int height, int textureWidth, int textureHeight) {
        blit(pipeline, texture, x, y, u, v, width, height, width, height, textureWidth, textureHeight, -1);
    }

    public void blit(RenderPipelines.Pipeline pipeline, ResourceLocation texture, int x, int y, float u, float v,
            int width, int height, int textureWidth, int textureHeight, int color) {
        blit(pipeline, texture, x, y, u, v, width, height, width, height, textureWidth, textureHeight, color);
    }

    /** {@code blit(pipeline, texture, x, y, u, v, width, height, regionWidth, regionHeight, textureWidth, textureHeight)}. */
    public void blit(RenderPipelines.Pipeline pipeline, ResourceLocation texture, int x, int y, float u, float v,
            int width, int height, int regionWidth, int regionHeight, int textureWidth, int textureHeight) {
        blit(pipeline, texture, x, y, u, v, width, height, regionWidth, regionHeight, textureWidth, textureHeight, -1);
    }

    public void blit(RenderPipelines.Pipeline pipeline, ResourceLocation texture, int x, int y, float u, float v,
            int width, int height, int regionWidth, int regionHeight, int textureWidth, int textureHeight, int color) {
        withColor(color, () -> {
            RenderSystem.enableBlend();
            graphics.blit(texture, x, y, width, height, u, v, regionWidth, regionHeight, textureWidth, textureHeight);
        });
    }

    public void blitSprite(RenderPipelines.Pipeline pipeline, ResourceLocation sprite, int x, int y, int width,
            int height) {
        blitSprite(pipeline, sprite, x, y, width, height, -1);
    }

    public void blitSprite(RenderPipelines.Pipeline pipeline, ResourceLocation sprite, int x, int y, int width,
            int height, int color) {
        withColor(color, () -> {
            RenderSystem.enableBlend();
            graphics.blitSprite(sprite, x, y, width, height);
        });
    }

    private void withColor(int argb, Runnable draw) {
        boolean tinted = argb != -1;
        if (tinted) {
            graphics.setColor((argb >> 16 & 255) / 255.0F, (argb >> 8 & 255) / 255.0F, (argb & 255) / 255.0F,
                    (argb >>> 24) / 255.0F);
        }
        try {
            withPose(draw);
        } finally {
            if (tinted) {
                graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            }
        }
    }

    // ---- items and tooltips ----

    public void item(ItemStack stack, int x, int y) {
        withPose(() -> graphics.renderItem(stack, x, y));
    }

    public void itemDecorations(Font font, ItemStack stack, int x, int y) {
        withPose(() -> graphics.renderItemDecorations(font, stack, x, y));
    }

    public void setTooltipForNextFrame(Font font, ItemStack stack, int x, int y) {
        graphics.renderTooltip(font, stack, x, y);
    }

    public void setTooltipForNextFrame(Font font, Component text, int x, int y) {
        graphics.renderTooltip(font, text, x, y);
    }

    public void setComponentTooltipForNextFrame(Font font, List<Component> lines, int x, int y) {
        graphics.renderComponentTooltip(font, lines, x, y);
    }

    public void setTooltipForNextFrame(Font font, List<FormattedCharSequence> lines, int x, int y) {
        graphics.renderTooltip(font, lines, x, y);
    }

    // ---- scissor ----

    public void enableScissor(int x1, int y1, int x2, int y2) {
        scissors.push(new ScreenRectangle(x1, y1, x2 - x1, y2 - y1));
        graphics.enableScissor(x1, y1, x2, y2);
    }

    public void disableScissor() {
        scissors.poll();
        graphics.disableScissor();
    }

    public @Nullable ScreenRectangle peekScissorStack() {
        return scissors.peek();
    }
}
