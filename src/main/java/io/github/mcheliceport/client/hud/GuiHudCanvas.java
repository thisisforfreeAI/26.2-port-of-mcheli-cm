package io.github.mcheliceport.client.hud;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;
import org.jspecify.annotations.Nullable;

/**
 * Draws the HUD through vanilla's GUI: lines become quads one GUI pixel wide (upstream set
 * {@code glLineWidth(scaleFactor)}), stippled lines become dashes (decision D15), points become
 * 2×2 squares, and textures are drawn rotated. Text uses the game font with a shadow, as
 * upstream's {@code drawString}.
 */
public final class GuiHudCanvas implements HudCanvas {

    private static final float HALF_WIDTH = 0.5F;
    private static final Map<String, int[]> TEXTURE_SIZES = new HashMap<>();

    private final GuiGraphicsExtractor graphics;
    private final Font font;

    public GuiHudCanvas(GuiGraphicsExtractor graphics) {
        this.graphics = graphics;
        this.font = Minecraft.getInstance().font;
    }

    /** Forgets texture sizes after a resource reload. */
    public static void clearTextureSizes() {
        TEXTURE_SIZES.clear();
    }

    /** 1.12's font drew a colour without alpha bits as opaque; the current font skips it. */
    static int textColor(int color) {
        return (color & 0xFC000000) == 0 ? color | 0xFF000000 : color;
    }

    @Override
    public void line(double[] xy, int color, int mode) {
        int points = xy.length / 2;
        if (points < 2) {
            return;
        }
        QuadBuilder quads = new QuadBuilder(false);
        switch (mode) {
            case LINES -> {
                for (int i = 0; i + 3 < xy.length; i += 4) {
                    quads.segment(xy[i], xy[i + 1], xy[i + 2], xy[i + 3], true);
                }
            }
            case LINE_LOOP, LINE_STRIP -> {
                for (int i = 0; i + 3 < xy.length; i += 2) {
                    quads.segment(xy[i], xy[i + 1], xy[i + 2], xy[i + 3], true);
                }
                if (mode == LINE_LOOP && points > 2) {
                    quads.segment(xy[xy.length - 2], xy[xy.length - 1], xy[0], xy[1], true);
                }
            }
            default -> throw new IllegalArgumentException("line mode " + mode);
        }
        this.submit(RenderPipelines.GUI, TextureSetup.noTexture(), quads, color);
    }

    @Override
    public void lineStipple(double[] xy, int color, int factor, int pattern) {
        QuadBuilder quads = new QuadBuilder(false);
        float step = Math.max(1, factor);
        for (int i = 0; i + 3 < xy.length; i += 4) {
            double x0 = xy[i];
            double y0 = xy[i + 1];
            double x1 = xy[i + 2];
            double y1 = xy[i + 3];
            // GL counts the stipple along the major axis, one bit per factor pixels, from each segment's start.
            double length = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
            if (length <= 0) {
                continue;
            }
            int bits = (int) Math.ceil(length / step);
            int runStart = -1;
            for (int b = 0; b <= bits; b++) {
                boolean on = b < bits && (pattern >> (b % 16) & 1) != 0;
                if (on && runStart < 0) {
                    runStart = b;
                } else if (!on && runStart >= 0) {
                    double t0 = Math.min(1.0, runStart * step / length);
                    double t1 = Math.min(1.0, b * step / length);
                    quads.segment(x0 + (x1 - x0) * t0, y0 + (y1 - y0) * t0, x0 + (x1 - x0) * t1,
                            y0 + (y1 - y0) * t1, false);
                    runStart = -1;
                }
            }
        }
        this.submit(RenderPipelines.GUI, TextureSetup.noTexture(), quads, color);
    }

    @Override
    public void rect(double x0, double y0, double x1, double y1, int color) {
        QuadBuilder quads = new QuadBuilder(false);
        quads.rect((float) Math.min(x0, x1), (float) Math.min(y0, y1), (float) Math.max(x0, x1),
                (float) Math.max(y0, y1));
        this.submit(RenderPipelines.GUI, TextureSetup.noTexture(), quads, color);
    }

    /** A filled triangle (upstream's {@code GL_TRIANGLES} target markers). */
    public void triangle(double x0, double y0, double x1, double y1, double x2, double y2, int color) {
        QuadBuilder quads = new QuadBuilder(false);
        quads.add(new float[] {(float) x0, (float) y0, (float) x1, (float) y1, (float) x2, (float) y2,
                (float) x2, (float) y2});
        this.submit(RenderPipelines.GUI, TextureSetup.noTexture(), quads, color);
    }

    /** A textured rectangle of a {@code mcheli:textures/gui} texture, tinted by {@code color}, UVs 0..1. */
    public void tintedTexture(String name, double left, double top, double width, double height, double u0,
            double v0, double u1, double v1, int color) {
        Identifier id = Identifier.fromNamespaceAndPath("mcheli", "textures/gui/" + name + ".png");
        AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(id);
        texturedQuad(this, texture, RenderPipelines.GUI_TEXTURED, color, left, top, width, height, u0, v0, u1, v1,
                0.0F);
    }

    /** A whole texture stretched over a rectangle (upstream's direct {@code GL_QUADS} overlays). */
    public void texture(Identifier id, double left, double top, double width, double height) {
        this.texture(id, left, top, width, height, 0xFFFFFFFF);
    }

    /** As {@link #texture(Identifier, double, double, double, double)}, tinted by {@code color}. */
    public void texture(Identifier id, double left, double top, double width, double height, int color) {
        AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(id);
        texturedQuad(this, texture, RenderPipelines.GUI_TEXTURED, color, left, top, width, height, 0.0, 0.0, 1.0, 1.0,
                0.0F);
    }

    @Override
    public void points(double[] xy, int color) {
        QuadBuilder quads = new QuadBuilder(false);
        for (int i = 0; i + 1 < xy.length; i += 2) {
            float x = (float) xy[i];
            float y = (float) xy[i + 1];
            quads.rect(x - 1.0F, y - 1.0F, x + 1.0F, y + 1.0F);
        }
        this.submit(RenderPipelines.GUI, TextureSetup.noTexture(), quads, color);
    }

    @Override
    public void texture(String name, double left, double top, double width, double height, double uLeft, double vTop,
            double uWidth, double vHeight, float rot) {
        Identifier id = Identifier.fromNamespaceAndPath("mcheli", "textures/gui/" + name.toLowerCase(Locale.ROOT) + ".png");
        AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(id);
        int[] size = TEXTURE_SIZES.computeIfAbsent(id.toString(), k -> {
            int w = texture.getTexture().getWidth(0);
            int h = texture.getTexture().getHeight(0);
            return new int[] {w > 0 ? w : 256, h > 0 ? h : 256};
        });
        texturedQuad(this, texture, RenderPipelines.GUI_TEXTURED, 0xFFFFFFFF, left, top, width, height,
                uLeft / size[0], vTop / size[1], (uLeft + uWidth) / size[0], (vTop + vHeight) / size[1], rot);
    }

    /** A rotated textured rectangle, as upstream's {@code drawTexture}/{@code drawTexturedModalRectRotate}. */
    static void texturedQuad(GuiHudCanvas canvas, AbstractTexture texture, RenderPipeline pipeline, int color,
            double left, double top, double width, double height, double u0, double v0, double u1, double v1,
            float rot) {
        double cx = left + width / 2.0;
        double cy = top + height / 2.0;
        double sin = Math.sin(Math.toRadians(rot));
        double cos = Math.cos(Math.toRadians(rot));
        double[][] corners = {{-width / 2.0, height / 2.0, u0, v1}, {width / 2.0, height / 2.0, u1, v1},
                {width / 2.0, -height / 2.0, u1, v0}, {-width / 2.0, -height / 2.0, u0, v0}};
        QuadBuilder quads = new QuadBuilder(true);
        float[] q = new float[16];
        for (int i = 0; i < 4; i++) {
            double x = corners[i][0];
            double y = corners[i][1];
            q[i * 4] = (float) (cx + x * cos - y * sin);
            q[i * 4 + 1] = (float) (cy + x * sin + y * cos);
            q[i * 4 + 2] = (float) corners[i][2];
            q[i * 4 + 3] = (float) corners[i][3];
        }
        quads.add(q);
        GpuSampler sampler = RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST);
        canvas.submit(pipeline, TextureSetup.singleTexture(texture.getTextureView(), sampler), quads, color);
    }

    @Override
    public void text(String text, int x, int y, int color, boolean centered) {
        if (centered) {
            this.graphics.centeredText(this.font, text, x, y, textColor(color));
        } else {
            this.graphics.text(this.font, text, x, y, textColor(color));
        }
    }

    @Override
    public void pushRotation(double x, double y, float degrees) {
        Matrix3x2fStack pose = this.graphics.pose();
        pose.pushMatrix();
        pose.translate((float) x, (float) y);
        pose.rotate((float) Math.toRadians(degrees));
        pose.translate((float) -x, (float) -y);
    }

    @Override
    public void popRotation() {
        this.graphics.pose().popMatrix();
    }

    void submit(RenderPipeline pipeline, TextureSetup textures, QuadBuilder quads, int color) {
        float[] vertices = quads.toArray();
        if (vertices.length == 0) {
            return;
        }
        Matrix3x2f pose = new Matrix3x2f(this.graphics.pose());
        @Nullable ScreenRectangle scissor = this.graphics.peekScissorStack();
        int stride = quads.textured ? 4 : 2;
        this.graphics.submitGuiElementRenderState(new HudShapeRenderState(pipeline, textures, pose, vertices,
                quads.textured, color, scissor, HudShapeRenderState.bounds(vertices, stride, pose, scissor)));
    }

    /** Collects quads, wound the way vanilla's GUI quads are so they survive back-face culling. */
    static final class QuadBuilder {
        final boolean textured;
        private float[] data = new float[64];
        private int size;

        QuadBuilder(boolean textured) {
            this.textured = textured;
        }

        void rect(float x0, float y0, float x1, float y1) {
            this.add(new float[] {x0, y0, x0, y1, x1, y1, x1, y0});
        }

        /** A segment one GUI pixel wide; {@code caps} extends it by half a pixel at each end. */
        void segment(double x0, double y0, double x1, double y1, boolean caps) {
            double dx = x1 - x0;
            double dy = y1 - y0;
            double len = Math.sqrt(dx * dx + dy * dy);
            if (len < 1.0E-6) {
                if (caps) {
                    this.rect((float) x0 - HALF_WIDTH, (float) y0 - HALF_WIDTH, (float) x0 + HALF_WIDTH,
                            (float) y0 + HALF_WIDTH);
                }
                return;
            }
            double ux = dx / len * HALF_WIDTH;
            double uy = dy / len * HALF_WIDTH;
            if (caps) {
                x0 -= ux;
                y0 -= uy;
                x1 += ux;
                y1 += uy;
            }
            double nx = -uy;
            double ny = ux;
            this.add(new float[] {(float) (x0 + nx), (float) (y0 + ny), (float) (x0 - nx), (float) (y0 - ny),
                    (float) (x1 - nx), (float) (y1 - ny), (float) (x1 + nx), (float) (y1 + ny)});
        }

        /** Adds one quad (4 corners), reversing it if it is wound the other way. */
        void add(float[] quad) {
            int stride = this.textured ? 4 : 2;
            float ax = quad[0];
            float ay = quad[1];
            float bx = quad[stride];
            float by = quad[stride + 1];
            float cx = quad[stride * 2];
            float cy = quad[stride * 2 + 1];
            float cross = (bx - ax) * (cy - by) - (by - ay) * (cx - bx);
            if (cross > 0) {
                float[] reversed = new float[quad.length];
                for (int v = 0; v < 4; v++) {
                    System.arraycopy(quad, v * stride, reversed, (3 - v) * stride, stride);
                }
                quad = reversed;
            }
            if (this.size + quad.length > this.data.length) {
                this.data = java.util.Arrays.copyOf(this.data, Math.max(this.data.length * 2, this.size + quad.length));
            }
            System.arraycopy(quad, 0, this.data, this.size, quad.length);
            this.size += quad.length;
        }

        float[] toArray() {
            return java.util.Arrays.copyOf(this.data, this.size);
        }
    }
}
