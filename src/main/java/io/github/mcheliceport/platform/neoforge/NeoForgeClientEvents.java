package io.github.mcheliceport.platform.neoforge;

import io.github.mcheliceport.platform.ClientEvents;
import io.github.mcheliceport.platform.ClientRegistrars;
import io.github.mcheliceport.platform.HudLayers;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Fires {@link ClientEvents} from NeoForge's client events. */
public final class NeoForgeClientEvents {

    private NeoForgeClientEvents() {
    }

    public static void register(IEventBus modBus) {
        IEventBus bus = NeoForge.EVENT_BUS;
        bus.addListener((ClientTickEvent.Post e) -> ClientEvents.run(ClientEvents.TICK_POST));
        bus.addListener((RenderFrameEvent.Pre e) -> ClientEvents.run(ClientEvents.RENDER_FRAME_PRE));
        bus.addListener((RenderFrameEvent.Post e) -> ClientEvents.run(ClientEvents.RENDER_FRAME_POST));
        bus.addListener((ClientPlayerNetworkEvent.LoggingIn e) -> ClientEvents.run(ClientEvents.LOGGING_IN));
        bus.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> ClientEvents.run(ClientEvents.LOGGING_OUT));
        bus.addListener((EntityTickEvent.Post e) -> {
            if (e.getEntity().level().isClientSide()) {
                ClientEvents.fire(ClientEvents.ENTITY_TICK_POST, e.getEntity());
            }
        });
        bus.addListener((SubmitCustomGeometryEvent e) -> ClientEvents.fire(ClientEvents.SUBMIT_GEOMETRY,
                new ClientEvents.Geometry(e.getLevelRenderState(), e.getPoseStack(), e.getSubmitNodeCollector())));
        bus.addListener((net.neoforged.neoforge.client.event.ViewportEvent.ComputeCameraAngles e) -> {
            var angles = new ClientEvents.CameraAngles(e.getCamera(), e.getPartialTick(), e.getYaw(), e.getPitch(), e.getRoll());
            ClientEvents.fire(ClientEvents.CAMERA_ANGLES, angles);
            e.setYaw(angles.getYaw());
            e.setPitch(angles.getPitch());
            e.setRoll(angles.getRoll());
        });
        bus.addListener((net.neoforged.neoforge.client.event.ViewportEvent.ComputeFov e) -> {
            var fov = new ClientEvents.Fov(e.getCamera(), e.getPartialTick(), e.getFOV());
            ClientEvents.fire(ClientEvents.COMPUTE_FOV, fov);
            e.setFOV((float) fov.getFOV());
        });
        bus.addListener((net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent e) -> {
            var distance = new ClientEvents.DetachedDistance(e.getDistance());
            ClientEvents.fire(ClientEvents.DETACHED_CAMERA_DISTANCE, distance);
            e.setDistance(distance.getDistance());
        });
        bus.addListener((net.neoforged.neoforge.client.event.RenderHandEvent e) -> {
            var hand = new ClientEvents.Cancelable();
            ClientEvents.fire(ClientEvents.RENDER_HAND, hand);
            if (hand.isCanceled()) {
                e.setCanceled(true);
            }
        });
        bus.addListener((net.neoforged.neoforge.client.event.RenderGuiLayerEvent.Pre e) -> {
            var layer = new ClientEvents.GuiLayerRender(e.getName());
            ClientEvents.fire(ClientEvents.RENDER_GUI_LAYER, layer);
            if (layer.isCanceled()) {
                e.setCanceled(true);
            }
        });
        bus.addListener((net.neoforged.neoforge.client.event.RenderNameTagEvent.CanRender e) -> {
            var tag = new ClientEvents.NameTag(e.getEntity());
            ClientEvents.fire(ClientEvents.NAME_TAG, tag);
            if (tag.hidden()) {
                e.setCanRender(net.minecraft.util.TriState.FALSE);
            }
        });
        bus.addListener((net.neoforged.neoforge.client.event.InputEvent.Key e) ->
                ClientEvents.fire(ClientEvents.KEY, new ClientEvents.KeyInput(e.getKeyEvent(), e.getAction())));
        bus.addListener((net.neoforged.neoforge.client.event.InputEvent.InteractionKeyMappingTriggered e) -> {
            var key = new ClientEvents.InteractionKey(e.isAttack(), e.isUseItem());
            ClientEvents.fire(ClientEvents.INTERACTION_KEY, key);
            if (!key.shouldSwingHand()) {
                e.setSwingHand(false);
            }
            if (key.isCanceled()) {
                e.setCanceled(true);
            }
        });
        bus.addListener((net.neoforged.neoforge.client.event.InputEvent.MouseScrollingEvent e) -> {
            var scroll = new ClientEvents.MouseScroll(e.getScrollDeltaY());
            ClientEvents.fire(ClientEvents.MOUSE_SCROLL, scroll);
            if (scroll.isCanceled()) {
                e.setCanceled(true);
            }
        });
        bus.addListener((net.neoforged.neoforge.client.event.ScreenEvent.Init.Post e) -> ClientEvents.fire(
                ClientEvents.SCREEN_INIT_POST, new ClientEvents.ScreenInit(e.getScreen(), e.getListenersList(), e::addListener)));
        bus.addListener((net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent e) ->
                ClientEvents.fire(ClientEvents.LEVEL_EXTRACTED, e.getLevel()));
        bus.addListener((net.neoforged.neoforge.client.event.RenderLevelStageEvent.AfterLevel e) -> ClientEvents.fire(
                ClientEvents.AFTER_LEVEL, new ClientEvents.AfterLevel(e.getLevelRenderState(), e.getModelViewMatrix())));
        modBus.addListener((RegisterGuiLayersEvent e) -> ClientEvents.fire(ClientEvents.REGISTER_HUD_LAYERS,
                new HudLayers() {
                    @Override
                    public void registerAbove(Identifier anchor, Identifier id, HudLayer layer) {
                        e.registerAbove(anchor, id, layer::render);
                    }

                    @Override
                    public void registerBelow(Identifier anchor, Identifier id, HudLayer layer) {
                        e.registerBelow(anchor, id, layer::render);
                    }

                    @Override
                    public void registerAboveAll(Identifier id, HudLayer layer) {
                        e.registerAboveAll(id, layer::render);
                    }
                }));
        bus.addListener((net.neoforged.neoforge.client.event.FlipFrameEvent e) -> ClientEvents.run(ClientEvents.FLIP_FRAME));
        // Highest, as ThermalVision's own listener was: it cancels and resubmits before other mods see the entity.
        bus.addListener(net.neoforged.bus.api.EventPriority.HIGHEST, (net.neoforged.neoforge.client.event.RenderLivingEvent.Pre<?, ?, ?> e) -> {
            var living = new ClientEvents.RenderLiving(e.getRenderer(), e.getRenderState(), e.getPoseStack(),
                    e.getSubmitNodeCollector());
            living.setCanceled(e.isCanceled());
            ClientEvents.fire(ClientEvents.RENDER_LIVING_PRE, living);
            e.setCanceled(living.isCanceled());
        });
        bus.addListener((net.neoforged.neoforge.client.event.RenderLivingEvent.Post<?, ?, ?> e) -> ClientEvents.fire(
                ClientEvents.RENDER_LIVING_POST, new ClientEvents.RenderLiving(e.getRenderer(), e.getRenderState(),
                        e.getPoseStack(), e.getSubmitNodeCollector())));
        bus.addListener((net.neoforged.neoforge.client.event.RegisterFeatureRenderersEvent e) -> ClientEvents.fire(
                ClientEvents.REGISTER_FEATURE_RENDERERS, new ClientRegistrars.FeatureRenderers() {
                    @Override
                    public <S extends net.minecraft.client.renderer.feature.submit.SubmitNode> void register(
                            net.minecraft.client.renderer.feature.FeatureRendererType<S> type,
                            java.util.function.Supplier<net.minecraft.client.renderer.feature.FeatureRenderer<S>> renderer) {
                        e.register(type, renderer.get());
                    }
                }));
        registerContent(modBus);
    }

    /** The mod-bus registration events. */
    private static void registerContent(IEventBus modBus) {
        modBus.addListener((net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent e) ->
                e.registerEntityModifier(new com.google.common.reflect.TypeToken<net.minecraft.client.renderer.entity.LivingEntityRenderer<
                        net.minecraft.world.entity.LivingEntity, net.minecraft.client.renderer.entity.state.LivingEntityRenderState, ?>>() {
                }, (net.minecraft.world.entity.LivingEntity entity,
                        net.minecraft.client.renderer.entity.state.LivingEntityRenderState state) ->
                        ClientEvents.LIVING_RENDER_STATE.forEach(listener -> listener.accept(entity, state, state.partialTick))));
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent e) ->
                ClientEvents.fire(ClientEvents.REGISTER_PIPELINES, e::registerPipeline));
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterParticleGroupsEvent e) ->
                ClientEvents.fire(ClientEvents.REGISTER_PARTICLE_GROUPS, e::register));
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent e) -> ClientEvents.fire(
                ClientEvents.REGISTER_KEY_MAPPINGS, new ClientRegistrars.KeyMappings() {
                    @Override
                    public void registerCategory(net.minecraft.client.KeyMapping.Category category) {
                        e.registerCategory(category);
                    }

                    @Override
                    public void register(net.minecraft.client.KeyMapping key) {
                        e.register(key);
                    }
                }));
        modBus.addListener((net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers e) -> ClientEvents.fire(
                ClientEvents.REGISTER_RENDERERS, new ClientRegistrars.Renderers() {
                    @Override
                    public <T extends net.minecraft.world.entity.Entity> void registerEntityRenderer(
                            net.minecraft.world.entity.EntityType<? extends T> type,
                            net.minecraft.client.renderer.entity.EntityRendererProvider<T> provider) {
                        e.registerEntityRenderer(type, provider);
                    }

                    @Override
                    public <T extends net.minecraft.world.level.block.entity.BlockEntity,
                            S extends net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState> void registerBlockEntityRenderer(
                            net.minecraft.world.level.block.entity.BlockEntityType<? extends T> type,
                            net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider<T, S> provider) {
                        e.registerBlockEntityRenderer(type, provider);
                    }
                }));
        modBus.addListener((net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterLayerDefinitions e) ->
                ClientEvents.fire(ClientEvents.REGISTER_LAYER_DEFINITIONS, e::registerLayerDefinition));
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterMenuScreensEvent e) -> ClientEvents.fire(
                ClientEvents.REGISTER_MENU_SCREENS, new ClientRegistrars.MenuScreenRegistrar() {
                    @Override
                    public <M extends net.minecraft.world.inventory.AbstractContainerMenu,
                            U extends net.minecraft.client.gui.screens.Screen & net.minecraft.client.gui.screens.inventory.MenuAccess<M>> void register(
                            net.minecraft.world.inventory.MenuType<? extends M> type,
                            net.minecraft.client.gui.screens.MenuScreens.ScreenConstructor<M, U> constructor) {
                        e.register(type, constructor);
                    }
                }));
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterItemModelsEvent e) ->
                ClientEvents.fire(ClientEvents.REGISTER_ITEM_MODELS, e::register));
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent e) -> ClientEvents.fire(
                ClientEvents.REGISTER_PICTURE_IN_PICTURE, new ClientRegistrars.PictureInPicture() {
                    @Override
                    public <T extends net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState> void register(
                            Class<T> stateClass,
                            java.util.function.Supplier<net.minecraft.client.gui.render.pip.PictureInPictureRenderer<T>> factory) {
                        e.register(stateClass, factory);
                    }
                }));
        modBus.addListener((net.neoforged.neoforge.client.event.AddClientReloadListenersEvent e) ->
                ClientEvents.fire(ClientEvents.ADD_RELOAD_LISTENERS, e::addListener));
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterFluidModelsEvent e) -> ClientEvents.fire(
                ClientEvents.REGISTER_FLUID_MODELS, (still, flowing, tint, stillFluid, flowingFluid) -> e.register(
                        new net.minecraft.client.renderer.block.FluidModel.Unbaked(
                                new net.minecraft.client.resources.model.sprite.Material(still),
                                new net.minecraft.client.resources.model.sprite.Material(flowing), null,
                                (net.neoforged.neoforge.client.fluid.FluidTintSource) state -> tint),
                        stillFluid, flowingFluid)));
    }
}
