package io.github.mcheliceport.client.hud;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;

/**
 * Quads in GUI coordinates, of one colour, for the HUD's lines, points, rectangles and textures:
 * vanilla's GUI only draws axis-aligned rectangles, and HUD lines run at any angle. Built by
 * {@link GuiHudCanvas}; drawn with a vanilla GUI pipeline, so it works on every backend.
 *
 * @param vertices x, y (and u, v when {@code textured}) of each corner, four corners per quad,
 *                 already wound as vanilla's GUI quads
 */
public record HudShapeRenderState(RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2fc pose,
        float[] vertices, boolean textured, int color, @Nullable ScreenRectangle scissorArea,
        @Nullable ScreenRectangle bounds) implements GuiElementRenderState {

    @Override
    public void buildVertices(VertexConsumer consumer) {
        int stride = this.textured ? 4 : 2;
        for (int i = 0; i + stride <= this.vertices.length; i += stride) {
            VertexConsumer v = consumer.addVertexWith2DPose(this.pose, this.vertices[i], this.vertices[i + 1]);
            if (this.textured) {
                v.setUv(this.vertices[i + 2], this.vertices[i + 3]);
            }
            v.setColor(this.color);
        }
    }

    /** The screen area of the quads, after the pose, cut by the scissor; null for no quads. */
    static @Nullable ScreenRectangle bounds(float[] vertices, int stride, Matrix3x2fc pose,
            @Nullable ScreenRectangle scissorArea) {
        if (vertices.length < stride) {
            return null;
        }
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        for (int i = 0; i + 1 < vertices.length; i += stride) {
            minX = Math.min(minX, vertices[i]);
            maxX = Math.max(maxX, vertices[i]);
            minY = Math.min(minY, vertices[i + 1]);
            maxY = Math.max(maxY, vertices[i + 1]);
        }
        int x0 = (int) Math.floor(minX);
        int y0 = (int) Math.floor(minY);
        int x1 = (int) Math.ceil(maxX);
        int y1 = (int) Math.ceil(maxY);
        ScreenRectangle rect = new ScreenRectangle(x0, y0, Math.max(1, x1 - x0), Math.max(1, y1 - y0))
                .transformMaxBounds(pose);
        return scissorArea != null ? scissorArea.intersection(rect) : rect;
    }
}
