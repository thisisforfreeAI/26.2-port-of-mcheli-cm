package io.github.mcheliceport.shim.client.event;

import net.minecraft.resources.ResourceLocation;

/** Names of NeoForge's vanilla HUD layers, used as anchors in {@link RegisterGuiLayersEvent} and as the names in {@link RenderGuiLayerEvent}. */
public final class VanillaGuiLayers {
    public static final ResourceLocation CHAT = ResourceLocation.withDefaultNamespace("chat");
    public static final ResourceLocation TITLE = ResourceLocation.withDefaultNamespace("title");
    public static final ResourceLocation CROSSHAIR = ResourceLocation.withDefaultNamespace("crosshair");
    public static final ResourceLocation HOTBAR = ResourceLocation.withDefaultNamespace("hotbar");
    public static final ResourceLocation PLAYER_HEALTH = ResourceLocation.withDefaultNamespace("player_health");
    public static final ResourceLocation ARMOR_LEVEL = ResourceLocation.withDefaultNamespace("armor_level");
    public static final ResourceLocation FOOD_LEVEL = ResourceLocation.withDefaultNamespace("food_level");
    public static final ResourceLocation AIR_LEVEL = ResourceLocation.withDefaultNamespace("air_level");
    public static final ResourceLocation BOSS_OVERLAY = ResourceLocation.withDefaultNamespace("boss_overlay");

    private VanillaGuiLayers() {
    }
}
