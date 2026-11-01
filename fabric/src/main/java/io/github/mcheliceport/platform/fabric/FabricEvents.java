package io.github.mcheliceport.platform.fabric;

import io.github.mcheliceport.platform.GameEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.world.InteractionResult;

/**
 * Fires {@link GameEvents} from Fabric API's events. Entity ticks and pack finders have no Fabric
 * event: {@code ServerLevelEntityTickMixin} and {@code PackRepositoryMixin} fire those.
 */
final class FabricEvents {

    private FabricEvents() {
    }

    static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> GameEvents.fire(GameEvents.SERVER_TICK_POST, server));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> GameEvents.fire(GameEvents.SERVER_STARTED, server));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> GameEvents.fire(GameEvents.SERVER_STOPPED, server));
        ServerTickEvents.START_LEVEL_TICK.register(level -> GameEvents.fire(GameEvents.LEVEL_TICK_PRE, level));
        ServerTickEvents.END_LEVEL_TICK.register(level -> GameEvents.fire(GameEvents.LEVEL_TICK_POST, level));
        ServerLevelEvents.UNLOAD.register((server, level) -> GameEvents.fire(GameEvents.LEVEL_UNLOAD, level));
        ServerEntityEvents.ALLOW_LOAD.register((entity, level, reason, fromDisk) -> GameEvents.allowJoin(entity, level));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                GameEvents.fire(GameEvents.PLAYER_LOGGED_IN, handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                GameEvents.fire(GameEvents.PLAYER_LOGGED_OUT, handler.player));
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) ->
                GameEvents.registerCommands(dispatcher, context));
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            // Like NeoForge's EntityInteract: only the plain interaction, not the "interact at" one.
            if (hit != null) {
                return InteractionResult.PASS;
            }
            InteractionResult result = GameEvents.interact(player, entity, hand);
            return result != null ? result : InteractionResult.PASS;
        });
        CommonLifecycleEvents.TAGS_LOADED.register((registries, client) -> GameEvents.tagsUpdated());
    }
}
