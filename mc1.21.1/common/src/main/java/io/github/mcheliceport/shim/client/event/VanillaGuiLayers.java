package io.github.mcheliceport.shim.client.event;

import net.minecraft.resources.ResourceLocation;

/** Names of NeoForge's vanilla HUD layers, used only as anchors in {@link RegisterGuiLayersEvent}. */
public final class VanillaGuiLayers {
    public static final ResourceLocation CHAT = ResourceLocation.withDefaultNamespace("chat");
    public static final ResourceLocation TITLE = ResourceLocation.withDefaultNamespace("title");
    public static final ResourceLocation CROSSHAIR = ResourceLocation.withDefaultNamespace("crosshair");
    public static final ResourceLocation HOTBAR = ResourceLocation.withDefaultNamespace("hotbar");

    private VanillaGuiLayers() {
    }
}
