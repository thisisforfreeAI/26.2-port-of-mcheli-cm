package io.github.mcheliceport.platform.neoforge;

import io.github.mcheliceport.platform.ClientEvents;
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
    }
}
