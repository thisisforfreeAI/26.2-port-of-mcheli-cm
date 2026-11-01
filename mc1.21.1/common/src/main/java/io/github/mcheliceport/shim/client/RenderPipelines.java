package io.github.mcheliceport.shim.client;

/**
 * Names for 26.2's GUI render pipelines. On 1.21.1 GUI drawing picks its shader itself, so these
 * only tell {@link GuiGraphicsExtractor} what kind of draw was meant.
 */
public final class RenderPipelines {

    public enum Pipeline { GUI, GUI_TEXTURED, GUI_INVERT, OTHER }

    public static final Pipeline GUI = Pipeline.GUI;
    public static final Pipeline GUI_TEXTURED = Pipeline.GUI_TEXTURED;
    public static final Pipeline GUI_INVERT = Pipeline.GUI_INVERT;

    private RenderPipelines() {
    }
}
