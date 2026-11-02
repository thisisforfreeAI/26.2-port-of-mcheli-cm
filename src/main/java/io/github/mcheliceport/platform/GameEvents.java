package io.github.mcheliceport.platform;

import com.mojang.brigadier.CommandDispatcher;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Loader-neutral game events. The shared code listens here instead of on a loader's event bus;
 * each loader's entry point fires them from its own events (NeoForge: {@code NeoForgeEvents},
 * Fabric: {@code FabricEvents} and its mixins). Listeners run in the order they were added.
 */
public final class GameEvents {

    /** After the server's tick. */
    public static final Listeners<Consumer<MinecraftServer>> SERVER_TICK_POST = new Listeners<>();
    /** Once the server has started (worlds loaded). */
    public static final Listeners<Consumer<MinecraftServer>> SERVER_STARTED = new Listeners<>();
    /** Once the server has stopped. */
    public static final Listeners<Consumer<MinecraftServer>> SERVER_STOPPED = new Listeners<>();
    /** Before a server level ticks its entities. */
    public static final Listeners<Consumer<ServerLevel>> LEVEL_TICK_PRE = new Listeners<>();
    /** After a server level's tick. */
    public static final Listeners<Consumer<ServerLevel>> LEVEL_TICK_POST = new Listeners<>();
    /** A server level unloads. */
    public static final Listeners<Consumer<ServerLevel>> LEVEL_UNLOAD = new Listeners<>();
    /** After an entity ticked, on the server (client entities: see stage 4 of docs/FABRIC_PLAN.md). */
    public static final Listeners<Consumer<Entity>> ENTITY_TICK_POST = new Listeners<>();
    /** An entity is added to a server level; a listener returning false keeps it out. */
    public static final Listeners<EntityJoin> ENTITY_JOIN = new Listeners<>();
    public static final Listeners<Consumer<ServerPlayer>> PLAYER_LOGGED_IN = new Listeners<>();
    public static final Listeners<Consumer<ServerPlayer>> PLAYER_LOGGED_OUT = new Listeners<>();
    public static final Listeners<CommandRegistration> REGISTER_COMMANDS = new Listeners<>();
    /** A player uses an item on an entity, on either side; the first non-null result wins. */
    public static final Listeners<EntityInteract> ENTITY_INTERACT = new Listeners<>();
    /** Adds pack sources to a pack repository. {@code trusted}: the client's vanilla-only data repository. */
    public static final Listeners<PackFinders> ADD_PACK_FINDERS = new Listeners<>();
    /** Tags were (re)loaded. */
    public static final Listeners<Runnable> TAGS_UPDATED = new Listeners<>();

    private GameEvents() {
    }

    @FunctionalInterface
    public interface EntityJoin {
        boolean allow(Entity entity, Level level);
    }

    @FunctionalInterface
    public interface CommandRegistration {
        void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context);
    }

    @FunctionalInterface
    public interface EntityInteract {
        @Nullable InteractionResult interact(Player player, Entity target, InteractionHand hand);
    }

    @FunctionalInterface
    public interface PackFinders {
        void add(PackType type, boolean trusted, Consumer<RepositorySource> sources);
    }

    // ---- firing, for the loaders ----

    public static <T> void fire(Listeners<Consumer<T>> event, T value) {
        for (Consumer<T> listener : event.listeners) {
            listener.accept(value);
        }
    }

    public static boolean allowJoin(Entity entity, Level level) {
        for (EntityJoin listener : ENTITY_JOIN.listeners) {
            if (!listener.allow(entity, level)) {
                return false;
            }
        }
        return true;
    }

    public static @Nullable InteractionResult interact(Player player, Entity target, InteractionHand hand) {
        for (EntityInteract listener : ENTITY_INTERACT.listeners) {
            InteractionResult result = listener.interact(player, target, hand);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        for (CommandRegistration listener : REGISTER_COMMANDS.listeners) {
            listener.register(dispatcher, context);
        }
    }

    public static void addPackFinders(PackType type, boolean trusted, Consumer<RepositorySource> sources) {
        for (PackFinders listener : ADD_PACK_FINDERS.listeners) {
            listener.add(type, trusted, sources);
        }
    }

    public static void tagsUpdated() {
        for (Runnable listener : TAGS_UPDATED.listeners) {
            listener.run();
        }
    }

    /** The listeners of one event. */
    public static final class Listeners<T> {
        private final List<T> listeners = new CopyOnWriteArrayList<>();

        Listeners() {
        }

        public void add(T listener) {
            listeners.add(listener);
        }

        /** For the loaders: calls {@code action} with each listener, in order. */
        public void forEach(Consumer<? super T> action) {
            listeners.forEach(action);
        }
    }
}
