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
