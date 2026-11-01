package io.github.mcheliceport.neoforge;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.client.McheliCePortClient;
import io.github.mcheliceport.shim.client.ClientFluids;
import io.github.mcheliceport.shim.client.GuiGraphicsExtractor;
import io.github.mcheliceport.shim.client.KeyContext;
import io.github.mcheliceport.shim.client.SubmitNodeCollector;
import io.github.mcheliceport.shim.client.event.ClientBus;
import io.github.mcheliceport.shim.client.event.ClientRegistrations;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.TriState;

/**
 * NeoForge client entry point: runs the shared client set-up, hands what it registered to
 * NeoForge's registration events, and posts the port's client events from NeoForge's own.
 */
@Mod(value = McheliCePort.MOD_ID, dist = Dist.CLIENT)
public final class McheliCePortNeoForgeClient {

    public McheliCePortNeoForgeClient(IEventBus modBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        McheliCePortClient.init();
        registerModBus(modBus);
        registerGameBus(NeoForge.EVENT_BUS);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerModBus(IEventBus bus) {
        bus.addListener((EntityRenderersEvent.RegisterRenderers event) -> {
            var registered = ClientRegistrations.renderers();
            for (var e : registered.entities) {
                event.registerEntityRenderer((EntityType) e.type(), (EntityRendererProvider) e.provider());
            }
            for (var e : registered.blockEntities) {
                event.registerBlockEntityRenderer((BlockEntityType) e.type(), (BlockEntityRendererProvider) e.provider());
            }
        });
        bus.addListener((EntityRenderersEvent.RegisterLayerDefinitions event) -> {
            for (var e : ClientRegistrations.layers().layers) {
                event.registerLayerDefinition(e.location(), e.definition());
            }
        });
        bus.addListener((RegisterMenuScreensEvent event) -> {
            for (var e : ClientRegistrations.screens().screens) {
                screen(event, e);
            }
        });
        bus.addListener((RegisterKeyMappingsEvent event) -> {
            for (var mapping : ClientRegistrations.keyMappings().mappings()) {
                KeyContext context = KeyContext.of(mapping);
                if (context != null) {
                    mapping.setKeyConflictContext(new IKeyConflictContext() {
                        @Override
                        public boolean isActive() {
                            return context.isActive();
                        }

                        // KeyModifier.NONE suppresses a key while Ctrl/Shift/Alt is held unless its
                        // context conflicts with IN_GAME; Ctrl and Ctrl+key must work while riding.
                        @Override
                        public boolean conflicts(IKeyConflictContext other) {
                            return other == this || other == KeyConflictContext.IN_GAME;
                        }
                    });
                }
                event.register(mapping);
            }
        });
        bus.addListener((RegisterGuiLayersEvent event) -> {
            for (var e : ClientRegistrations.guiLayers().ordered()) {
                event.registerAboveAll(e.id(), (graphics, delta) -> e.layer().render(GuiGraphicsExtractor.of(graphics), delta));
            }
        });
        bus.addListener((RegisterClientReloadListenersEvent event) -> {
            for (var e : ClientRegistrations.reloadListeners().listeners()) {
                event.registerReloadListener(e.listener());
            }
        });
        bus.addListener((RegisterClientExtensionsEvent event) -> {
            for (ClientFluids.Entry e : ClientFluids.ENTRIES) {
                IClientFluidTypeExtensions extensions = new IClientFluidTypeExtensions() {
                    @Override
                    public ResourceLocation getStillTexture() {
                        return e.still();
                    }

                    @Override
                    public ResourceLocation getFlowingTexture() {
                        return e.flowing();
                    }

                    @Override
                    public int getTintColor() {
                        return e.tint();
                    }
                };
                var types = e.fluids().stream().map(f -> ((Fluid) f.get()).getFluidType()).distinct()
                        .toArray(net.neoforged.neoforge.fluids.FluidType[]::new);
                event.registerFluidType(extensions, types);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static <M extends AbstractContainerMenu, U extends net.minecraft.client.gui.screens.Screen
            & net.minecraft.client.gui.screens.inventory.MenuAccess<M>> void screen(RegisterMenuScreensEvent event,
            io.github.mcheliceport.shim.client.event.RegisterMenuScreensEvent.Entry<M, U> e) {
        event.<M, U>register((MenuType<M>) e.type(), (menu, inventory, title) -> e.constructor().create(menu, inventory, title));
    }

    private static void registerGameBus(IEventBus bus) {
        bus.addListener((ClientTickEvent.Pre e) -> ClientBus.post(new io.github.mcheliceport.shim.client.event.ClientTickEvent.Pre()));
        bus.addListener((ClientTickEvent.Post e) -> ClientBus.post(new io.github.mcheliceport.shim.client.event.ClientTickEvent.Post()));
        bus.addListener((RenderFrameEvent.Pre e) -> ClientBus.post(new io.github.mcheliceport.shim.client.event.RenderFrameEvent.Pre(e.getPartialTick())));
        bus.addListener((RenderFrameEvent.Post e) -> ClientBus.post(new io.github.mcheliceport.shim.client.event.RenderFrameEvent.Post(e.getPartialTick())));
        bus.addListener((ViewportEvent.ComputeFov e) -> {
            var shim = ClientBus.post(new io.github.mcheliceport.shim.client.event.ViewportEvent.ComputeFov(
                    e.getCamera(), e.getPartialTick(), e.getFOV(), e.usedConfiguredFov()));
            e.setFOV(shim.getFOV());
        });
        bus.addListener((ViewportEvent.ComputeCameraAngles e) -> {
            var shim = ClientBus.post(new io.github.mcheliceport.shim.client.event.ViewportEvent.ComputeCameraAngles(
                    e.getCamera(), e.getPartialTick(), e.getYaw(), e.getPitch(), e.getRoll()));
            e.setYaw(shim.getYaw());
            e.setPitch(shim.getPitch());
            e.setRoll(shim.getRoll());
        });
        bus.addListener((CalculateDetachedCameraDistanceEvent e) -> e.setDistance(ClientBus.post(
                new io.github.mcheliceport.shim.client.event.CalculateDetachedCameraDistanceEvent(e.getDistance())).getDistance()));
        bus.addListener((InputEvent.Key e) -> ClientBus.post(new io.github.mcheliceport.shim.client.event.InputEvent.Key(
                e.getKey(), e.getScanCode(), e.getAction(), e.getModifiers())));
        bus.addListener((InputEvent.MouseScrollingEvent e) -> {
            if (ClientBus.post(new io.github.mcheliceport.shim.client.event.InputEvent.MouseScrollingEvent(
                    e.getScrollDeltaX(), e.getScrollDeltaY())).isCanceled()) {
                e.setCanceled(true);
            }
        });
        bus.addListener((InputEvent.InteractionKeyMappingTriggered e) -> {
            int button = e.isAttack() ? 0 : e.isUseItem() ? 1 : 2;
            var shim = ClientBus.post(new io.github.mcheliceport.shim.client.event.InputEvent.InteractionKeyMappingTriggered(
                    button, e.getKeyMapping(), e.getHand()));
            if (shim.isCanceled()) {
                e.setSwingHand(shim.shouldSwingHand());
                e.setCanceled(true);
            }
        });
        bus.addListener((ScreenEvent.Init.Post e) -> ClientBus.post(new io.github.mcheliceport.shim.client.event.ScreenEvent.Init.Post(
                e.getScreen(), e.getListenersList(), e::addListener)));
        bus.addListener((ClientPlayerNetworkEvent.LoggingIn e) -> ClientBus.post(
                new io.github.mcheliceport.shim.client.event.ClientPlayerNetworkEvent.LoggingIn()));
        bus.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> ClientBus.post(
                new io.github.mcheliceport.shim.client.event.ClientPlayerNetworkEvent.LoggingOut()));
        bus.addListener((RenderHandEvent e) -> {
            if (ClientBus.post(new io.github.mcheliceport.shim.client.event.RenderHandEvent()).isCanceled()) {
                e.setCanceled(true);
            }
        });
        bus.addListener((RenderNameTagEvent e) -> {
            var shim = ClientBus.post(new io.github.mcheliceport.shim.client.event.RenderNameTagEvent(e.getEntity(), e.getContent()));
            Boolean can = shim.canRender();
            if (can != null) {
                e.setCanRender(can ? TriState.TRUE : TriState.FALSE);
            }
        });
        bus.addListener((RenderGuiLayerEvent.Pre e) -> {
            if (ClientBus.post(new io.github.mcheliceport.shim.client.event.RenderGuiLayerEvent.Pre(e.getName())).isCanceled()) {
                e.setCanceled(true);
            }
        });
        bus.addListener((RenderLevelStageEvent e) -> {
            if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
                return;
            }
            var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
            ClientBus.post(new io.github.mcheliceport.shim.client.event.SubmitCustomGeometryEvent(new PoseStack(),
                    new SubmitNodeCollector(buffers)));
            buffers.endBatch();
        });
    }
}
