package io.github.mcheliceport.client.hud;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.mcheliceport.shim.client.GuiGraphicsExtractor;
import io.github.mcheliceport.shim.client.RenderPipelines;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.opengl.GL11;

/**
 * Quads in GUI coordinates, of one colour, for the HUD's lines, points, rectangles and textures:
 * vanilla's GUI only draws axis-aligned rectangles, and HUD lines run at any angle. Built by
 * {@link GuiHudCanvas}. 26.2 submits these as a GUI element; 1.21.1 draws them at once, after
 * flushing what the GUI has batched, so they keep their place in the drawing order.
 */
public final class HudShapeRenderState {
    private HudShapeRenderState() {}

    /**
     * @param vertices x, y (and u, v when {@code textured}) of each corner, four corners per quad,
     *                 already wound as vanilla's GUI quads
     */
    public static void draw(GuiGraphicsExtractor g, RenderPipelines.Pipeline pipeline,
            @Nullable AbstractTexture texture, Matrix3x2fc pose, float[] vertices, boolean textured, int color) {
        int stride = textured ? 4 : 2;
        if (vertices.length < stride * 4 || textured && texture == null) {
            return;
        }
        g.nextStratum();
        Matrix4f m = new Matrix4f(g.vanilla().pose().last().pose())
                .mul(GuiGraphicsExtractor.toMatrix4f(new Matrix3x2f(pose)));
        RenderSystem.enableBlend();
        switch (pipeline) {
            case GUI_INVERT -> RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR, GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ZERO);
            case GUI_TEXTURED_ADDITIVE -> RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ONE);
            default -> RenderSystem.defaultBlendFunc();
        }
        BufferBuilder buffer;
        if (textured) {
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            texture.bind();
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
            RenderSystem.setShaderTexture(0, texture.getId());
            buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        } else {
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        }
        for (int i = 0; i + stride <= vertices.length; i += stride) {
            buffer.addVertex(m, vertices[i], vertices[i + 1], 0.0F);
            if (textured) {
                buffer.setUv(vertices[i + 2], vertices[i + 3]);
            }
            buffer.setColor(color);
        }
        MeshData mesh = buffer.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }
}
