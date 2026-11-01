package io.github.mcheliceport.platform.event;

import com.mojang.brigadier.CommandDispatcher;
import java.util.function.Consumer;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The game events the shared code listens to. Each loader module forwards its own events here
 * (NeoForge's event bus, Fabric API callbacks or a Fabric mixin), so the shared code never
 * touches loader event classes.
 */
public final class ModEvents {

    /** End of each server tick. */
    public static final Event<Consumer<MinecraftServer>> SERVER_TICK_POST = new Event<>();
    public static final Event<Consumer<MinecraftServer>> SERVER_STARTED = new Event<>();
    public static final Event<Consumer<MinecraftServer>> SERVER_STOPPED = new Event<>();

    /** Start and end of each server level tick. */
    public static final Event<Consumer<ServerLevel>> LEVEL_TICK_PRE = new Event<>();
    public static final Event<Consumer<ServerLevel>> LEVEL_TICK_POST = new Event<>();
    public static final Event<Consumer<ServerLevel>> LEVEL_UNLOAD = new Event<>();

    /** After each entity tick, on both sides (riding entities included). */
    public static final Event<Consumer<Entity>> ENTITY_TICK_POST = new Event<>();

    /** An entity joins a level, on both sides. Return true to stop it joining. */
    public static final Event<EntityJoin> ENTITY_JOIN_LEVEL = new Event<>();

    public static final Event<Consumer<ServerPlayer>> PLAYER_LOGGED_IN = new Event<>();
    public static final Event<Consumer<ServerPlayer>> PLAYER_LOGGED_OUT = new Event<>();

    public static final Event<RegisterCommands> REGISTER_COMMANDS = new Event<>();

    /** A player right-clicks an entity. A non-null result cancels the click with that result. */
    public static final Event<EntityInteract> ENTITY_INTERACT = new Event<>();

    /** Tags were (re)loaded, on either side. */
    public static final Event<Runnable> TAGS_UPDATED = new Event<>();

    private ModEvents() {
    }

    @FunctionalInterface
    public interface EntityJoin {
        /** @return true to cancel the join */
        boolean onJoin(Entity entity, Level level);
    }

    @FunctionalInterface
    public interface RegisterCommands {
        void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context,
                Commands.CommandSelection selection);
    }

    @FunctionalInterface
    public interface EntityInteract {
        @Nullable InteractionResult interact(Player player, Level level, InteractionHand hand, ItemStack stack,
                Entity target);
    }

    // Helpers for the loader modules.

    public static void fire(Event<Consumer<MinecraftServer>> event, MinecraftServer server) {
        for (Consumer<MinecraftServer> l : event.listeners()) {
            l.accept(server);
        }
    }

    public static void fireLevel(Event<Consumer<ServerLevel>> event, ServerLevel level) {
        for (Consumer<ServerLevel> l : event.listeners()) {
            l.accept(level);
        }
    }

    public static void firePlayer(Event<Consumer<ServerPlayer>> event, ServerPlayer player) {
        for (Consumer<ServerPlayer> l : event.listeners()) {
            l.accept(player);
        }
    }

    public static void entityTickPost(Entity entity) {
        for (Consumer<Entity> l : ENTITY_TICK_POST.listeners()) {
            l.accept(entity);
        }
    }

    /** @return true if a listener cancelled the join */
    public static boolean entityJoin(Entity entity, Level level) {
        for (EntityJoin l : ENTITY_JOIN_LEVEL.listeners()) {
            if (l.onJoin(entity, level)) {
                return true;
            }
        }
        return false;
    }

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher,
            CommandBuildContext context, Commands.CommandSelection selection) {
        for (RegisterCommands l : REGISTER_COMMANDS.listeners()) {
            l.register(dispatcher, context, selection);
        }
    }

    public static @Nullable InteractionResult entityInteract(Player player, Level level, InteractionHand hand,
            ItemStack stack, Entity target) {
        for (EntityInteract l : ENTITY_INTERACT.listeners()) {
            InteractionResult result = l.interact(player, level, hand, stack, target);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    public static void tagsUpdated() {
        for (Runnable l : TAGS_UPDATED.listeners()) {
            l.run();
        }
    }
}
