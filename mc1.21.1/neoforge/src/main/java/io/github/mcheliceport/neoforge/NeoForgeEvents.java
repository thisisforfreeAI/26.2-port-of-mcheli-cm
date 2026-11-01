package io.github.mcheliceport.neoforge;

import io.github.mcheliceport.platform.event.ModEvents;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
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

/** Forwards NeoForge's events to the shared {@link ModEvents}. */
final class NeoForgeEvents {

    static final List<Source> PACK_SOURCES = new ArrayList<>();

    record Source(PackType type, Consumer<Consumer<Pack>> packs) {
    }

    private NeoForgeEvents() {
    }

    static void register(IEventBus modBus) {
        IEventBus bus = NeoForge.EVENT_BUS;
        bus.addListener((ServerTickEvent.Post e) -> ModEvents.fire(ModEvents.SERVER_TICK_POST, e.getServer()));
        bus.addListener((ServerStartedEvent e) -> ModEvents.fire(ModEvents.SERVER_STARTED, e.getServer()));
        bus.addListener((ServerStoppedEvent e) -> ModEvents.fire(ModEvents.SERVER_STOPPED, e.getServer()));
        bus.addListener((LevelTickEvent.Pre e) -> {
            if (e.getLevel() instanceof ServerLevel level) {
                ModEvents.fireLevel(ModEvents.LEVEL_TICK_PRE, level);
            }
        });
        bus.addListener((LevelTickEvent.Post e) -> {
            if (e.getLevel() instanceof ServerLevel level) {
                ModEvents.fireLevel(ModEvents.LEVEL_TICK_POST, level);
            }
        });
        bus.addListener((LevelEvent.Unload e) -> {
            if (e.getLevel() instanceof ServerLevel level) {
                ModEvents.fireLevel(ModEvents.LEVEL_UNLOAD, level);
            }
        });
        bus.addListener((EntityTickEvent.Post e) -> ModEvents.entityTickPost(e.getEntity()));
        bus.addListener((EntityJoinLevelEvent e) -> {
            if (ModEvents.entityJoin(e.getEntity(), e.getLevel())) {
                e.setCanceled(true);
            }
        });
        bus.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer player) {
                ModEvents.firePlayer(ModEvents.PLAYER_LOGGED_IN, player);
            }
        });
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer player) {
                ModEvents.firePlayer(ModEvents.PLAYER_LOGGED_OUT, player);
            }
        });
        bus.addListener((RegisterCommandsEvent e) ->
                ModEvents.registerCommands(e.getDispatcher(), e.getBuildContext(), e.getCommandSelection()));
        bus.addListener((PlayerInteractEvent.EntityInteract e) -> {
            InteractionResult result = ModEvents.entityInteract(e.getEntity(), e.getLevel(), e.getHand(),
                    e.getItemStack(), e.getTarget());
            if (result != null) {
                e.setCancellationResult(result);
                e.setCanceled(true);
            }
        });
        bus.addListener((TagsUpdatedEvent e) -> ModEvents.tagsUpdated());
        // Data packs go to the untrusted repository only; see ModPackFinders.
        modBus.addListener((AddPackFindersEvent e) -> {
            if (e.getPackType() == PackType.SERVER_DATA && e.isTrusted()) {
                return;
            }
            for (Source source : PACK_SOURCES) {
                if (source.type() == e.getPackType()) {
                    e.addRepositorySource(source.packs()::accept);
                }
            }
        });
    }
}
