package io.github.mcheliceport.fabric;

import io.github.mcheliceport.platform.event.ModEvents;
import io.github.mcheliceport.wingman.block.WingmanMarkerBlockEntity;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionResult;
import org.jspecify.annotations.Nullable;

/** Forwards Fabric API's events to the shared {@link ModEvents}. Entity ticks and joins come from mixins. */
public final class FabricEvents {

    static volatile @Nullable MinecraftServer server;

    private FabricEvents() {
    }

    static void register() {
        ServerLifecycleEvents.SERVER_STARTING.register(s -> server = s);
        ServerLifecycleEvents.SERVER_STARTED.register(s -> ModEvents.fire(ModEvents.SERVER_STARTED, s));
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
            ModEvents.fire(ModEvents.SERVER_STOPPED, s);
            server = null;
        });
        ServerTickEvents.END_SERVER_TICK.register(s -> ModEvents.fire(ModEvents.SERVER_TICK_POST, s));
        ServerTickEvents.START_WORLD_TICK.register(level -> ModEvents.fireLevel(ModEvents.LEVEL_TICK_PRE, level));
        ServerTickEvents.END_WORLD_TICK.register(level -> ModEvents.fireLevel(ModEvents.LEVEL_TICK_POST, level));
        ServerWorldEvents.UNLOAD.register((s, level) -> ModEvents.fireLevel(ModEvents.LEVEL_UNLOAD, level));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, s) ->
                ModEvents.firePlayer(ModEvents.PLAYER_LOGGED_IN, handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, s) ->
                ModEvents.firePlayer(ModEvents.PLAYER_LOGGED_OUT, handler.player));
        CommandRegistrationCallback.EVENT.register(ModEvents::registerCommands);
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            InteractionResult result = ModEvents.entityInteract(player, level, hand, player.getItemInHand(hand),
                    entity);
            return result == null ? InteractionResult.PASS : result;
        });
        CommonLifecycleEvents.TAGS_LOADED.register((registries, client) -> ModEvents.tagsUpdated());
        // NeoForge's BlockEntity#onLoad.
        ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register((blockEntity, level) -> {
            if (blockEntity instanceof WingmanMarkerBlockEntity marker) {
                marker.onLoad();
            }
        });
    }
}
