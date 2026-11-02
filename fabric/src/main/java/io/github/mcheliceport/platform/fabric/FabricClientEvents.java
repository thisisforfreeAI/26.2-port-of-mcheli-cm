package io.github.mcheliceport.platform.fabric;

import io.github.mcheliceport.platform.ClientEvents;
import io.github.mcheliceport.platform.ClientRegistrars;
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

    /** The registration events: Fabric's registries take entries while the client initialises. */
    static void registerContent() {
        ClientEvents.fire(ClientEvents.REGISTER_PIPELINES, net.minecraft.client.renderer.RenderPipelines::register);
        ClientEvents.fire(ClientEvents.REGISTER_PARTICLE_GROUPS,
                net.fabricmc.fabric.api.client.particle.v1.ParticleGroupRegistry::register);
        ClientEvents.fire(ClientEvents.REGISTER_KEY_MAPPINGS, new ClientRegistrars.KeyMappings() {
            @Override
            public void registerCategory(net.minecraft.client.KeyMapping.Category category) {
                net.minecraft.client.KeyMapping.Category.register(category.id());
            }

            @Override
            public void register(net.minecraft.client.KeyMapping key) {
                net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.registerKeyMapping(key);
            }
        });
        ClientEvents.fire(ClientEvents.REGISTER_RENDERERS, new ClientRegistrars.Renderers() {
            @Override
            public <T extends net.minecraft.world.entity.Entity> void registerEntityRenderer(
                    net.minecraft.world.entity.EntityType<? extends T> type,
                    net.minecraft.client.renderer.entity.EntityRendererProvider<T> provider) {
                net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(type, provider);
            }

            @Override
            @SuppressWarnings({"unchecked", "rawtypes"})
            public <T extends net.minecraft.world.level.block.entity.BlockEntity,
                    S extends net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState> void registerBlockEntityRenderer(
                    net.minecraft.world.level.block.entity.BlockEntityType<? extends T> type,
                    net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider<T, S> provider) {
                net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry.register(
                        (net.minecraft.world.level.block.entity.BlockEntityType) type,
                        (net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider) provider);
            }
        });
        ClientEvents.fire(ClientEvents.REGISTER_LAYER_DEFINITIONS, (layer, definition) ->
                net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry.registerModelLayer(layer, definition::get));
        ClientEvents.fire(ClientEvents.REGISTER_MENU_SCREENS, new ClientRegistrars.MenuScreenRegistrar() {
            @Override
            public <M extends net.minecraft.world.inventory.AbstractContainerMenu,
                    U extends net.minecraft.client.gui.screens.Screen & net.minecraft.client.gui.screens.inventory.MenuAccess<M>> void register(
                    net.minecraft.world.inventory.MenuType<? extends M> type,
                    net.minecraft.client.gui.screens.MenuScreens.ScreenConstructor<M, U> constructor) {
                net.minecraft.client.gui.screens.MenuScreens.register(type, constructor);
            }
        });
        ClientEvents.fire(ClientEvents.REGISTER_ITEM_MODELS, (id, codec) ->
                net.minecraft.client.renderer.item.ItemModels.ID_MAPPER.put(id, codec));
        ClientEvents.fire(ClientEvents.REGISTER_PICTURE_IN_PICTURE, new ClientRegistrars.PictureInPicture() {
            @Override
            public <T extends net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState> void register(
                    Class<T> stateClass,
                    java.util.function.Supplier<net.minecraft.client.gui.render.pip.PictureInPictureRenderer<T>> factory) {
                net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry.register(
                        context -> factory.get());
            }
        });
        ClientEvents.fire(ClientEvents.ADD_RELOAD_LISTENERS, (id, listener) ->
                net.fabricmc.fabric.api.resource.v1.ResourceLoader.get(net.minecraft.server.packs.PackType.CLIENT_RESOURCES)
                        .registerReloadListener(id, listener));
        ClientEvents.fire(ClientEvents.REGISTER_FEATURE_RENDERERS, new ClientRegistrars.FeatureRenderers() {
            @Override
            public <S extends net.minecraft.client.renderer.feature.submit.SubmitNode> void register(
                    net.minecraft.client.renderer.feature.FeatureRendererType<S> type,
                    java.util.function.Supplier<net.minecraft.client.renderer.feature.FeatureRenderer<S>> renderer) {
                net.fabricmc.fabric.api.client.rendering.v1.FeatureRendererRegistry.register(type, renderer);
            }
        });
        ClientEvents.fire(ClientEvents.REGISTER_FLUID_MODELS, (still, flowing, tint, stillFluid, flowingFluid) ->
                net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderingRegistry.register(stillFluid, flowingFluid,
                        new net.minecraft.client.renderer.block.FluidModel.Unbaked(
                                new net.minecraft.client.resources.model.sprite.Material(still),
                                new net.minecraft.client.resources.model.sprite.Material(flowing), null,
                                (net.minecraft.client.color.block.BlockTintSource) state -> tint)));
    }
}
