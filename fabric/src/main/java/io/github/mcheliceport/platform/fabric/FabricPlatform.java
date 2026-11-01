package io.github.mcheliceport.platform.fabric;

import io.github.mcheliceport.platform.Platform;
import io.github.mcheliceport.registry.RegEntry;
import io.netty.buffer.Unpooled;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Function;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.material.FlowingFluid;
import org.jspecify.annotations.Nullable;

/** The Fabric side of {@link Platform}: entries are registered as soon as they are declared. */
public final class FabricPlatform implements Platform {

    @Override
    public String loaderName() {
        return "fabric";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public java.nio.file.Path gameDir() {
        return FabricLoader.getInstance().getGameDir();
    }

    @Override
    public String modVersion() {
        return FabricLoader.getInstance().getModContainer(io.github.mcheliceport.McheliCePort.MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString()).orElse("");
    }

    @Override
    public net.minecraft.client.gui.screens.@Nullable Screen createConfigScreen(
            net.minecraft.client.gui.screens.@Nullable Screen parent) {
        return new net.neoforged.neoforge.client.gui.ConfigurationScreen(io.github.mcheliceport.McheliCePort.MOD_ID,
                parent);
    }

    /** The mod jar's files, through Fabric Loader. */
    public static ModFiles modFiles() {
        java.util.List<java.nio.file.Path> roots = FabricLoader.getInstance()
                .getModContainer(io.github.mcheliceport.McheliCePort.MOD_ID).orElseThrow().getRootPaths();
        return new ModFiles() {
            @Override
            public void visitFiles(String folder, io.github.mcheliceport.content.addon.BuiltinAddonPack.FileVisitor visitor)
                    throws java.io.IOException {
                for (java.nio.file.Path root : roots) {
                    java.nio.file.Path start = root.resolve(folder);
                    if (!java.nio.file.Files.isDirectory(start)) {
                        continue;
                    }
                    try (var files = java.nio.file.Files.walk(start)) {
                        for (java.nio.file.Path file : (Iterable<java.nio.file.Path>) files
                                .filter(java.nio.file.Files::isRegularFile)::iterator) {
                            String relative = root.relativize(file).toString().replace('\\', '/');
                            visitor.visit(relative, () -> java.nio.file.Files.newInputStream(file));
                        }
                    }
                }
            }

            @Override
            public boolean containsFile(String path) {
                return roots.stream().anyMatch(root -> java.nio.file.Files.isRegularFile(root.resolve(path)));
            }
        };
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R, T extends R> RegEntry<R, T> register(ResourceKey<? extends Registry<R>> registry, Identifier id,
            Function<Identifier, ? extends T> factory) {
        Registry<R> target = (Registry<R>) BuiltInRegistries.REGISTRY.getValue(registry.identifier());
        if (target == null) {
            throw new IllegalStateException("Unknown registry " + registry.identifier());
        }
        ResourceKey<R> key = ResourceKey.create(registry, id);
        Holder<R> holder = (Holder<R>) Registry.registerForHolder(target, key, factory.apply(id));
        return new RegEntry<>(key, () -> holder);
    }

    @Override
    public FlowingFluid createFuelFluid(boolean source) {
        return source ? new FuelFluid.Source() : new FuelFluid.Flowing();
    }

    // ---- menus ----

    @Override
    public <M extends AbstractContainerMenu> MenuType<M> createMenuType(MenuFactory<M> factory) {
        return new ExtendedMenuType<>((containerId, inventory, data) -> factory.create(containerId, inventory,
                wrap(data, inventory)), ByteBufCodecs.BYTE_ARRAY.cast());
    }

    private static RegistryFriendlyByteBuf wrap(byte[] data, Inventory inventory) {
        return new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(data), inventory.player.registryAccess());
    }

    @Override
    public void openMenu(ServerPlayer player, MenuProvider menu, Consumer<RegistryFriendlyByteBuf> data) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), player.registryAccess());
        try {
            data.accept(buf);
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            player.openMenu(new ExtendedMenuProvider<byte[]>() {
                @Override
                public byte[] getScreenOpeningData(ServerPlayer opener) {
                    return bytes;
                }

                @Override
                public Component getDisplayName() {
                    return menu.getDisplayName();
                }

                @Override
                public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player opener) {
                    return menu.createMenu(containerId, inventory, opener);
                }
            });
        } finally {
            buf.release();
        }
    }

    // ---- networking ----

    @Override
    public boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        return ServerPlayNetworking.canSend(player, type);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }

    @Override
    public void sendToPlayersTrackingEntity(Entity entity, CustomPacketPayload payload) {
        for (ServerPlayer player : PlayerLookup.tracking(entity)) {
            if (ServerPlayNetworking.canSend(player, payload.type())) {
                ServerPlayNetworking.send(player, payload);
            }
        }
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        ClientPlayNetworking.send(payload);
    }
}
