package io.github.mcheliceport.platform.neoforge;

import io.github.mcheliceport.platform.GameEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Fires {@link GameEvents} from NeoForge's events. */
final class NeoForgeEvents {

    private NeoForgeEvents() {
    }

    static void register(IEventBus modBus) {
        IEventBus bus = NeoForge.EVENT_BUS;
        bus.addListener((ServerTickEvent.Post e) -> GameEvents.fire(GameEvents.SERVER_TICK_POST, e.getServer()));
        bus.addListener((ServerStartedEvent e) -> GameEvents.fire(GameEvents.SERVER_STARTED, e.getServer()));
        bus.addListener((ServerStoppedEvent e) -> GameEvents.fire(GameEvents.SERVER_STOPPED, e.getServer()));
        bus.addListener((LevelTickEvent.Pre e) -> {
            if (e.getLevel() instanceof ServerLevel level) {
                GameEvents.fire(GameEvents.LEVEL_TICK_PRE, level);
            }
        });
        bus.addListener((LevelTickEvent.Post e) -> {
            if (e.getLevel() instanceof ServerLevel level) {
                GameEvents.fire(GameEvents.LEVEL_TICK_POST, level);
            }
        });
        bus.addListener((LevelEvent.Unload e) -> {
            if (e.getLevel() instanceof ServerLevel level) {
                GameEvents.fire(GameEvents.LEVEL_UNLOAD, level);
            }
        });
        bus.addListener((EntityTickEvent.Post e) -> {
            if (!e.getEntity().level().isClientSide()) {
                GameEvents.fire(GameEvents.ENTITY_TICK_POST, e.getEntity());
            }
        });
        bus.addListener((EntityJoinLevelEvent e) -> {
            if (!e.getLevel().isClientSide() && !GameEvents.allowJoin(e.getEntity(), e.getLevel())) {
                e.setCanceled(true);
            }
        });
        bus.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer player) {
                GameEvents.fire(GameEvents.PLAYER_LOGGED_IN, player);
            }
        });
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer player) {
                GameEvents.fire(GameEvents.PLAYER_LOGGED_OUT, player);
            }
        });
        bus.addListener((RegisterCommandsEvent e) -> GameEvents.registerCommands(e.getDispatcher(), e.getBuildContext()));
        bus.addListener((PlayerInteractEvent.EntityInteract e) -> {
            InteractionResult result = GameEvents.interact(e.getEntity(), e.getTarget(), e.getHand());
            if (result != null) {
                e.setCancellationResult(result);
                e.setCanceled(true);
            }
        });
        bus.addListener((TagsUpdatedEvent e) -> GameEvents.tagsUpdated());
        modBus.addListener((AddPackFindersEvent e) ->
                GameEvents.addPackFinders(e.getPackType(), e.isTrusted(), e::addRepositorySource));
    }
}
