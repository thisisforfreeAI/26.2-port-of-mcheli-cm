package io.github.mcheliceport.client.hud;

/**
 * Where the HUD interpreter draws: upstream's {@code MCH_HudItem} drawing helpers, in GUI
 * coordinates, without any Minecraft type. The game draws through {@code GuiHudCanvas}; the tests
 * record the calls.
 *
 * <p>Colours are ARGB as upstream passed them to GL. Lines are one GUI pixel wide (upstream's
 * {@code glLineWidth(scaleFactor)}).
 */
public interface HudCanvas {

    /** {@code GL_LINES}: separate segments, two points each. */
    int LINES = 1;
    /** {@code GL_LINE_LOOP}: a closed polyline. */
    int LINE_LOOP = 2;
    /** {@code GL_LINE_STRIP}: an open polyline. */
    int LINE_STRIP = 3;

    /** Upstream {@code drawLine}: {@code xy} holds x, y pairs; {@code mode} is one of the constants above. */
    void line(double[] xy, int color, int mode);

    /**
     * Upstream {@code drawLineStipple}: separate segments ({@code GL_LINES}) drawn with a 16-bit
     * pattern, lowest bit first, each bit covering {@code factor} GUI pixels along the segment.
     */
    void lineStipple(double[] xy, int color, int factor, int pattern);

    /** Upstream {@code drawRect}: a filled rectangle between two corners, in either order. */
    void rect(double x0, double y0, double x1, double y1, int color);

    /** Upstream {@code drawPoints} with {@code scaleFactor * 2} pixels: squares two GUI pixels wide. */
    void points(double[] xy, int color);

    /**
     * Upstream {@code drawTexture}: part of {@code mcheli:textures/gui/<name>.png}, rotated by
     * {@code rot} degrees about the rectangle's centre. UVs are in texels of the image.
     */
    void texture(String name, double left, double top, double width, double height, double uLeft, double vTop,
            double uWidth, double vHeight, float rot);

    /** Upstream {@code drawString}/{@code drawCenteredString}: text with a shadow. */
    void text(String text, int x, int y, int color, boolean centered);

    /** Upstream's {@code translate(x, y)}, {@code rotate(degrees, 0, 0, 1)}, {@code translate(-x, -y)} in a pushed matrix. */
    void pushRotation(double x, double y, float degrees);

    /** Pops the matrix of {@link #pushRotation}. */
    void popRotation();
}
