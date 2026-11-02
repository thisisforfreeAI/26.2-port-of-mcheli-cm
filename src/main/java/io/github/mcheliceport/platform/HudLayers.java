package io.github.mcheliceport.platform;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * Registers HUD layers, ordered against vanilla's or the mod's own (NeoForge's GUI layers, Fabric's
 * HUD elements). Vanilla layers go by NeoForge's {@code VanillaGuiLayers} names, which the Fabric
 * build maps to Fabric's {@code VanillaHudElements}.
 */
public interface HudLayers {
    Identifier CHAT = Identifier.withDefaultNamespace("chat");
    Identifier TITLE = Identifier.withDefaultNamespace("title");
    Identifier CROSSHAIR = Identifier.withDefaultNamespace("crosshair");
    Identifier HOTBAR = Identifier.withDefaultNamespace("hotbar");
    Identifier PLAYER_HEALTH = Identifier.withDefaultNamespace("player_health");
    Identifier ARMOR_LEVEL = Identifier.withDefaultNamespace("armor_level");
    Identifier FOOD_LEVEL = Identifier.withDefaultNamespace("food_level");
    Identifier AIR_LEVEL = Identifier.withDefaultNamespace("air_level");
    Identifier BOSS_OVERLAY = Identifier.withDefaultNamespace("boss_overlay");

    void registerAbove(Identifier anchor, Identifier id, HudLayer layer);

    void registerBelow(Identifier anchor, Identifier id, HudLayer layer);

    /** Above every other layer. */
    void registerAboveAll(Identifier id, HudLayer layer);

    /** Extracts a HUD layer's render state for this frame. */
    @FunctionalInterface
    interface HudLayer {
        void render(GuiGraphicsExtractor graphics, DeltaTracker delta);
    }
}
