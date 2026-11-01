package io.github.mcheliceport.platform.fabric;

import io.github.mcheliceport.platform.Platform;
import io.github.mcheliceport.registry.RegEntry;
import io.netty.buffer.Unpooled;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Function;
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
        return FabricClientHooks.configScreen(parent);
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
        net.minecraft.core.Holder<R> holder = (net.minecraft.core.Holder<R>) Registry.registerForHolder(target, key, factory.apply(id));
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

    @Override
    public net.minecraft.world.item.CreativeModeTab.Builder creativeTabBuilder(Identifier after) {
        return net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab.builder();
    }

    @Override
    public net.minecraft.server.packs.repository.Pack.Metadata packMetadata(Component description, boolean hidden) {
        return new net.minecraft.server.packs.repository.Pack.Metadata(description,
                net.minecraft.server.packs.repository.PackCompatibility.COMPATIBLE,
                net.minecraft.world.flag.FeatureFlagSet.of(), java.util.List.of());
    }

    @Override
    public net.minecraft.world.item.Item.Properties noCombineRepair(net.minecraft.world.item.Item.Properties properties) {
        return properties;
    }

    @Override
    public <T extends Entity> net.minecraft.world.entity.EntityType.Builder<T> trackDeltas(
            net.minecraft.world.entity.EntityType.Builder<T> builder) {
        return builder.alwaysUpdateVelocity(true);
    }

    // ---- game ----

    /** NeoForge's persistent data, saved under the same name as NeoForge's tag. */
    private static final net.fabricmc.fabric.api.attachment.v1.AttachmentType<net.minecraft.nbt.CompoundTag> PERSISTENT_DATA =
            net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry.create(
                    Identifier.fromNamespaceAndPath(io.github.mcheliceport.McheliCePort.MOD_ID, "persistent_data"),
                    builder -> builder.persistent(net.minecraft.nbt.CompoundTag.CODEC)
                            .initializer(net.minecraft.nbt.CompoundTag::new));

    @Override
    public io.github.mcheliceport.platform.ChunkTickets chunkTickets(Identifier id) {
        return new FabricChunkTickets(id);
    }

    @Override
    public net.minecraft.nbt.CompoundTag persistentData(Entity entity) {
        return entity.getAttachedOrCreate(PERSISTENT_DATA);
    }

    @Override
    public ServerPlayer fakePlayer(net.minecraft.server.level.ServerLevel level, com.mojang.authlib.GameProfile profile) {
        return net.fabricmc.fabric.api.entity.FakePlayer.get(level, profile);
    }

    @Override
    public net.minecraft.world.level.block.state.BlockState fluidPlaceBlock(net.minecraft.world.level.LevelAccessor level,
            net.minecraft.core.BlockPos pos, net.minecraft.core.BlockPos liquidPos,
            net.minecraft.world.level.block.state.BlockState state) {
        return state;
    }

    @Override
    public java.util.Collection<ServerPlayer> playersTracking(Entity entity) {
        return PlayerLookup.tracking(entity);
    }

    @Override
    public float explosionResistance(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
            net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.material.FluidState fluid) {
        return Math.max(state.getBlock().getExplosionResistance(), fluid.getExplosionResistance());
    }

    @Override
    public net.minecraft.world.InteractionResult useItemFirst(net.minecraft.world.item.ItemStack stack,
            net.minecraft.world.item.context.UseOnContext context) {
        return net.minecraft.world.InteractionResult.PASS;
    }

    @Override
    public boolean explosionCancelled(net.minecraft.world.level.Level level,
            net.minecraft.world.level.ServerExplosion explosion) {
        return false;
    }

    @Override
    public void explosionDetonate(net.minecraft.world.level.Level level, net.minecraft.world.level.ServerExplosion explosion,
            java.util.List<Entity> entities, java.util.List<net.minecraft.core.BlockPos> blocks) {
    }

    // ---- fluid and energy items ----

    /** Fabric's transfer API counts droplets: 81,000 a bucket, 81 a millibucket. */
    private static final long DROPLETS_PER_MB = net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants.BUCKET / 1000;

    @Override
    public boolean isFluidContainer(net.minecraft.world.item.ItemStack stack) {
        return !stack.isEmpty() && net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage.ITEM.find(stack,
                net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext.withConstant(stack.copyWithCount(1)))
                != null;
    }

    @Override
    public @Nullable Drained drainContainer(net.minecraft.world.item.ItemStack input,
            java.util.function.Predicate<net.minecraft.world.level.material.Fluid> accepts, int max,
            java.util.function.Predicate<net.minecraft.world.item.ItemStack> canCommit) {
        var slot = net.fabricmc.fabric.api.transfer.v1.item.ContainerStorage.of(
                new net.minecraft.world.SimpleContainer(input.copyWithCount(1)), null).getSlot(0);
        var context = net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext.ofSingleSlot(slot);
        var storage = net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage.ITEM.find(input.copyWithCount(1), context);
        if (storage == null || max <= 0) {
            return null;
        }
        for (var view : storage.nonEmptyViews()) {
            var fluid = view.getResource();
            if (!fluid.componentsMatch(net.minecraft.core.component.DataComponentPatch.EMPTY)
                    || !accepts.test(fluid.getFluid())) {
                continue;
            }
            try (var transaction = net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()) {
                long droplets = storage.extract(fluid, max * DROPLETS_PER_MB, transaction);
                int drained = (int) (droplets / DROPLETS_PER_MB);
                net.minecraft.world.item.ItemStack container = slot.getResource().toStack((int) slot.getAmount());
                if (drained <= 0 || !canCommit.test(container)) {
                    return null;
                }
                transaction.commit();
                return new Drained(fluid.getFluid(), drained, container);
            }
        }
        return null;
    }

    // Fabric API has no item energy API: batteries need Team Reborn Energy (not wired up yet).
    @Override
    public boolean isBattery(net.minecraft.world.item.ItemStack stack) {
        return false;
    }

    @Override
    public int extractItemEnergy(net.minecraft.world.item.ItemStack stack, int max) {
        return 0;
    }

    @Override
    public int insertItemEnergy(net.minecraft.world.item.ItemStack stack, int max) {
        return 0;
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
        FabricClientHooks.sendToServer(payload);
    }
}
