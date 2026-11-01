package io.github.mcheliceport.platform;

import io.github.mcheliceport.registry.RegEntry;
import java.nio.file.Path;
import java.util.ServiceLoader;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * The seam between the mod and its mod loader. The mod's code calls this interface; the NeoForge
 * build implements it in {@code platform.neoforge} and the Fabric build in {@code fabric/}. Each
 * build names its implementation in {@code META-INF/services/io.github.mcheliceport.platform.Platform}.
 * See docs/FABRIC_PLAN.md.
 */
public interface Platform {

    /** The running loader's implementation. */
    static Platform get() {
        return Holder.INSTANCE;
    }

    /** {@code "neoforge"} or {@code "fabric"}. */
    String loaderName();

    /** Whether a mod with this id is loaded. */
    boolean isModLoaded(String modId);

    /** The game's config folder. */
    Path configDir();

    /** The mod's own version, e.g. {@code 0.6.0}. */
    String modVersion();

    /** Client only: the mod's config screen, or {@code null} when the loader has none. */
    net.minecraft.client.gui.screens.@org.jspecify.annotations.Nullable Screen createConfigScreen(
            net.minecraft.client.gui.screens.@org.jspecify.annotations.Nullable Screen parent);

    /** The game folder ({@code .minecraft} or the server folder). */
    Path gameDir();

    /** The files of the mod's own jar (or of the build output folders in development). */
    interface ModFiles extends io.github.mcheliceport.content.addon.BuiltinAddonPack.Source {
        /** Whether the jar has a file at {@code path}, relative to its root. */
        boolean containsFile(String path);
    }

    /**
     * Registers an entry. NeoForge defers the factory to its registry event; Fabric registers it
     * at once. Either way the returned entry resolves once registration has run.
     */
    <R, T extends R> RegEntry<R, T> register(ResourceKey<? extends Registry<R>> registry, Identifier id,
            Function<Identifier, ? extends T> factory);

    /**
     * The source ({@code source}) or flowing form of {@code mch_fuel}, for {@code ModFluids}; both
     * forms come from the same loader-specific fluid type.
     */
    net.minecraft.world.level.material.FlowingFluid createFuelFluid(boolean source);

    /** Builds a menu's client side from the data {@link #openMenu} wrote. */
    @FunctionalInterface
    interface MenuFactory<M extends net.minecraft.world.inventory.AbstractContainerMenu> {
        M create(int containerId, net.minecraft.world.entity.player.Inventory inventory,
                net.minecraft.network.RegistryFriendlyByteBuf data);
    }

    /** A menu type whose client side is built from extra data (NeoForge's {@code IMenuTypeExtension.create}). */
    <M extends net.minecraft.world.inventory.AbstractContainerMenu> net.minecraft.world.inventory.MenuType<M>
            createMenuType(MenuFactory<M> factory);

    /** Opens a menu of {@link #createMenuType} for {@code player}, sending what {@code data} writes. */
    void openMenu(net.minecraft.server.level.ServerPlayer player, net.minecraft.world.MenuProvider menu,
            java.util.function.Consumer<net.minecraft.network.RegistryFriendlyByteBuf> data);

    /**
     * A creative tab builder placed after {@code after} (NeoForge orders tabs; Fabric pages them
     * in registration order and ignores {@code after}).
     */
    net.minecraft.world.item.CreativeModeTab.Builder creativeTabBuilder(net.minecraft.resources.Identifier after);

    /**
     * Metadata of a pack the mod adds, compatible with this game version. {@code hidden}: NeoForge
     * hides it from the pack screen (Fabric cannot, and lists it).
     */
    net.minecraft.server.packs.repository.Pack.Metadata packMetadata(net.minecraft.network.chat.Component description,
            boolean hidden);

    /** NeoForge's {@code setNoCombineRepair}: the item cannot be repaired by combining two in a grid. No-op on Fabric. */
    net.minecraft.world.item.Item.Properties noCombineRepair(net.minecraft.world.item.Item.Properties properties);

    /** Sends the entity's velocity to clients with every position update (NeoForge {@code setShouldReceiveVelocityUpdates}). */
    <T extends net.minecraft.world.entity.Entity> net.minecraft.world.entity.EntityType.Builder<T> trackDeltas(
            net.minecraft.world.entity.EntityType.Builder<T> builder);

    // ---- game ----

    /**
     * Chunk tickets owned by entity UUIDs, not saved with the world (NeoForge's {@code TicketController}).
     * Create them while the mod initialises.
     */
    ChunkTickets chunkTickets(Identifier id);

    /** A per-entity tag the mod keeps across saves (NeoForge's {@code getPersistentData}). */
    net.minecraft.nbt.CompoundTag persistentData(net.minecraft.world.entity.Entity entity);

    /** A fake player for using items on blocks without a real player. */
    net.minecraft.server.level.ServerPlayer fakePlayer(net.minecraft.server.level.ServerLevel level,
            com.mojang.authlib.GameProfile profile);

    /** The block a fluid interaction places, after other mods had their say (NeoForge's fluid place event). */
    net.minecraft.world.level.block.state.BlockState fluidPlaceBlock(net.minecraft.world.level.LevelAccessor level,
            net.minecraft.core.BlockPos pos, net.minecraft.core.BlockPos liquidPos,
            net.minecraft.world.level.block.state.BlockState state);

    /** The players tracking an entity (receiving its updates). */
    java.util.Collection<net.minecraft.server.level.ServerPlayer> playersTracking(net.minecraft.world.entity.Entity entity);

    /** A block's and its fluid's explosion resistance, the higher of the two, without an explosion (modded blocks may look at the position). */
    float explosionResistance(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
            net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.material.FluidState fluid);

    /** NeoForge's {@code onItemUseFirst}, before {@code useOn}; PASS on Fabric. */
    net.minecraft.world.InteractionResult useItemFirst(net.minecraft.world.item.ItemStack stack,
            net.minecraft.world.item.context.UseOnContext context);

    /** Whether another mod cancelled this explosion (NeoForge's explosion start event; never on Fabric). */
    boolean explosionCancelled(net.minecraft.world.level.Level level, net.minecraft.world.level.ServerExplosion explosion);

    /** Lets other mods change the entities and blocks an explosion affects (NeoForge's detonate event). */
    void explosionDetonate(net.minecraft.world.level.Level level, net.minecraft.world.level.ServerExplosion explosion,
            java.util.List<net.minecraft.world.entity.Entity> entities, java.util.List<net.minecraft.core.BlockPos> blocks);

    // ---- fluid and energy items ----

    /** What {@link #drainContainer} took out of a container. */
    record Drained(net.minecraft.world.level.material.Fluid fluid, int amount,
            net.minecraft.world.item.ItemStack container) {
    }

    /** Whether the item holds or takes fluids (a fuel can, a bucket, another mod's tank item). */
    boolean isFluidContainer(net.minecraft.world.item.ItemStack stack);

    /**
     * Drains up to {@code max} millibuckets of the first fluid {@code accepts} takes from one item
     * of {@code input} (which is not changed). Nothing happens unless something was drained and
     * {@code canCommit} accepts the emptied container; then the result holds the fluid, the amount
     * and the container left over.
     */
    @org.jspecify.annotations.Nullable Drained drainContainer(net.minecraft.world.item.ItemStack input,
            java.util.function.Predicate<net.minecraft.world.level.material.Fluid> accepts, int max,
            java.util.function.Predicate<net.minecraft.world.item.ItemStack> canCommit);

    /** Whether the item is a battery the loader's energy API knows. */
    boolean isBattery(net.minecraft.world.item.ItemStack stack);

    /** Takes up to {@code max} energy out of a battery stack, in place. */
    int extractItemEnergy(net.minecraft.world.item.ItemStack stack, int max);

    /** Puts up to {@code max} energy into a battery stack, in place. */
    int insertItemEnergy(net.minecraft.world.item.ItemStack stack, int max);

    // ---- networking ----

    /** Whether {@code player}'s connection knows the payload (false for fake players and clients without the mod). */
    boolean canSend(net.minecraft.server.level.ServerPlayer player,
            net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<?> type);

    /** Sends a payload to one player; check {@link #canSend} first. */
    void sendToPlayer(net.minecraft.server.level.ServerPlayer player,
            net.minecraft.network.protocol.common.custom.CustomPacketPayload payload);

    /** Sends a payload to every player tracking {@code entity}. */
    void sendToPlayersTrackingEntity(net.minecraft.world.entity.Entity entity,
            net.minecraft.network.protocol.common.custom.CustomPacketPayload payload);

    /** Client only: sends a payload to the server. */
    void sendToServer(net.minecraft.network.protocol.common.custom.CustomPacketPayload payload);

    final class Holder {
        private static final Platform INSTANCE = ServiceLoader.load(Platform.class, Platform.class.getClassLoader())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No io.github.mcheliceport.platform.Platform "
                        + "implementation: META-INF/services is missing from this build"));

        private Holder() {
        }
    }
}
