package io.github.mcheliceport.fabric;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.client.McheliCePortClient;
import io.github.mcheliceport.shim.client.ClientFluids;
import io.github.mcheliceport.shim.client.GuiGraphicsExtractor;
import io.github.mcheliceport.shim.client.SubmitNodeCollector;
import io.github.mcheliceport.shim.client.event.ClientBus;
import io.github.mcheliceport.shim.client.event.ClientPlayerNetworkEvent;
import io.github.mcheliceport.shim.client.event.ClientRegistrations;
import io.github.mcheliceport.shim.client.event.ClientTickEvent;
import io.github.mcheliceport.shim.client.event.RegisterMenuScreensEvent;
import io.github.mcheliceport.shim.client.event.ScreenEvent;
import io.github.mcheliceport.shim.client.event.SubmitCustomGeometryEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry;
import net.fabricmc.fabric.api.client.render.fluid.v1.SimpleFluidRenderHandler;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.Fluid;

/**
 * Fabric client entry point: the client half of the payload handlers, the shared client set-up,
 * its registrations handed to Fabric API, and the port's client events posted from Fabric API's
 * events (the rest come from this module's client mixins).
 */
public final class McheliCePortFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        FabricNetwork.clientSender = ClientPlayNetworking::send;
        FabricNetwork.registerClientReceivers();
        McheliCePortClient.init();
        register();
        events();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void register() {
        var renderers = ClientRegistrations.renderers();
        for (var e : renderers.entities) {
            EntityRendererRegistry.register((EntityType) e.type(), (EntityRendererProvider) e.provider());
        }
        for (var e : renderers.blockEntities) {
            BlockEntityRenderers.register((BlockEntityType) e.type(), (BlockEntityRendererProvider) e.provider());
        }
        for (var e : ClientRegistrations.layers().layers) {
            EntityModelLayerRegistry.registerModelLayer(e.location(), e.definition()::get);
        }
        for (var e : ClientRegistrations.screens().screens) {
            screen(e);
        }
        ClientRegistrations.keyMappings().mappings().forEach(KeyBindingHelper::registerKeyBinding);
        var hudLayers = ClientRegistrations.guiLayers().ordered();
        HudRenderCallback.EVENT.register((graphics, delta) -> {
            var g = GuiGraphicsExtractor.of(graphics);
            for (var layer : hudLayers) {
                layer.layer().render(g, delta);
            }
        });
        var helper = ResourceManagerHelper.get(PackType.CLIENT_RESOURCES);
        for (var e : ClientRegistrations.reloadListeners().listeners()) {
            helper.registerReloadListener(new Identified(e.id(), e.listener()));
        }
        for (ClientFluids.Entry e : ClientFluids.ENTRIES) {
            var handler = new SimpleFluidRenderHandler(e.still(), e.flowing(), e.tint() & 0xFFFFFF);
            Fluid[] fluids = e.fluids().stream().map(f -> (Fluid) f.get()).toArray(Fluid[]::new);
            for (Fluid fluid : fluids) {
                FluidRenderHandlerRegistry.INSTANCE.register(fluid, handler);
            }
            BlockRenderLayerMap.INSTANCE.putFluids(RenderType.translucent(), fluids);
        }
    }

    @SuppressWarnings("unchecked")
    private static <M extends AbstractContainerMenu, U extends Screen & MenuAccess<M>> void screen(
            RegisterMenuScreensEvent.Entry<M, U> e) {
        MenuScreens.<M, U>register((MenuType<M>) e.type(), (menu, inventory, title) -> e.constructor().create(menu, inventory, title));
    }

    private static void events() {
        ClientTickEvents.START_CLIENT_TICK.register(mc -> ClientBus.post(new ClientTickEvent.Pre()));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> ClientBus.post(new ClientTickEvent.Post()));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> ClientBus.post(new ClientPlayerNetworkEvent.LoggingIn()));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> ClientBus.post(new ClientPlayerNetworkEvent.LoggingOut()));
        ScreenEvents.AFTER_INIT.register((mc, screen, width, height) -> {
            List<net.minecraft.client.gui.components.events.GuiEventListener> listeners = new ArrayList<>(screen.children());
            ClientBus.post(new ScreenEvent.Init.Post(screen, listeners, listener -> {
                if (listener instanceof AbstractWidget widget) {
                    Screens.getButtons(screen).add(widget);
                }
            }));
        });
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
            ClientBus.post(new SubmitCustomGeometryEvent(new PoseStack(), new SubmitNodeCollector(buffers)));
            buffers.endBatch();
        });
    }

    /** A reload listener with the id Fabric API orders listeners by. */
    private record Identified(ResourceLocation id, PreparableReloadListener listener)
            implements IdentifiableResourceReloadListener {
        @Override
        public ResourceLocation getFabricId() {
            return id;
        }

        @Override
        public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager manager,
                ProfilerFiller preparations, ProfilerFiller reload, Executor background, Executor game) {
            return listener.reload(barrier, manager, preparations, reload, background, game);
        }

        @Override
        public String getName() {
            return McheliCePort.MOD_ID + ":" + id.getPath();
        }
    }
}
