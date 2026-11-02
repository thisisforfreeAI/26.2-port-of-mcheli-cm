package io.github.mcheliceport.platform.fabric;

import io.github.mcheliceport.platform.ClientEvents;
import io.github.mcheliceport.platform.HudLayers;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.resources.Identifier;

/**
 * Fires {@link ClientEvents} from Fabric API's client events. What Fabric API has no event for
 * (frames, camera angles and distance, FOV, the hand, name tags, raw keys, the attack/use keys, the
 * mouse wheel, client entity ticks, after the level) is fired by the mixins in
 * {@code io.github.mcheliceport.fabricmixin.client}.
 */
final class FabricClientEvents {

    /** NeoForge's vanilla GUI layer names, which the shared code uses, to Fabric's HUD element ids. */
    private static final Map<Identifier, Identifier> VANILLA = Map.of(
            HudLayers.CHAT, VanillaHudElements.CHAT,
            HudLayers.TITLE, VanillaHudElements.TITLE_AND_SUBTITLE,
            HudLayers.CROSSHAIR, VanillaHudElements.CROSSHAIR,
            HudLayers.HOTBAR, VanillaHudElements.HOTBAR,
            HudLayers.PLAYER_HEALTH, VanillaHudElements.HEALTH_BAR,
            HudLayers.ARMOR_LEVEL, VanillaHudElements.ARMOR_BAR,
            HudLayers.FOOD_LEVEL, VanillaHudElements.FOOD_BAR,
            HudLayers.AIR_LEVEL, VanillaHudElements.AIR_BAR,
            HudLayers.BOSS_OVERLAY, VanillaHudElements.BOSS_BAR);

    private FabricClientEvents() {
    }

    static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> ClientEvents.run(ClientEvents.TICK_POST));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> ClientEvents.run(ClientEvents.LOGGING_IN));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientEvents.run(ClientEvents.LOGGING_OUT));
        LevelRenderEvents.COLLECT_SUBMITS.register(context -> ClientEvents.fire(ClientEvents.SUBMIT_GEOMETRY,
                new ClientEvents.Geometry(context.levelState(), context.poseStack(), context.submitNodeCollector())));
        LevelRenderEvents.END_EXTRACTION.register(context -> ClientEvents.fire(ClientEvents.LEVEL_EXTRACTED, context.level()));
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> ClientEvents.fire(ClientEvents.SCREEN_INIT_POST,
                new ClientEvents.ScreenInit(screen, screen.children(),
                        listener -> Screens.getWidgets(screen).add((AbstractWidget) listener))));

        // NeoForge's RenderGuiLayerEvent.Pre for the vanilla layers the mod hides.
        VANILLA.forEach((neoForgeName, fabricId) -> HudElementRegistry.replaceElement(fabricId, original ->
                (graphics, delta) -> {
                    var layer = new ClientEvents.GuiLayerRender(neoForgeName);
                    ClientEvents.fire(ClientEvents.RENDER_GUI_LAYER, layer);
                    if (!layer.isCanceled()) {
                        original.extractRenderState(graphics, delta);
                    }
                }));
        ClientEvents.fire(ClientEvents.REGISTER_HUD_LAYERS, new HudLayers() {
            @Override
            public void registerAbove(Identifier anchor, Identifier id, HudLayer layer) {
                HudElementRegistry.attachElementAfter(VANILLA.getOrDefault(anchor, anchor), id, layer::render);
            }

            @Override
            public void registerBelow(Identifier anchor, Identifier id, HudLayer layer) {
                HudElementRegistry.attachElementBefore(VANILLA.getOrDefault(anchor, anchor), id, layer::render);
            }

            @Override
            public void registerAboveAll(Identifier id, HudLayer layer) {
                HudElementRegistry.addLast(id, layer::render);
            }
        });
    }
}
