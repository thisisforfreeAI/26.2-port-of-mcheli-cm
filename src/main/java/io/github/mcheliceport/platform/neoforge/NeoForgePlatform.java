package io.github.mcheliceport.platform.neoforge;

import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.platform.Platform;
import io.github.mcheliceport.registry.RegEntry;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The NeoForge side of {@link Platform}: entries go through one {@link DeferredRegister} per registry. */
public final class NeoForgePlatform implements Platform {
    private static final Map<ResourceKey<?>, DeferredRegister<?>> REGISTERS = new LinkedHashMap<>();
    private static final java.util.List<net.neoforged.neoforge.common.world.chunk.TicketController> TICKET_CONTROLLERS =
            new java.util.concurrent.CopyOnWriteArrayList<>();
    private static IEventBus modBus;

    @Override
    public String loaderName() {
        return "neoforge";
    }

    @Override
    public boolean isModLoaded(String modId) {
        // Null before FML has loaded the mods (plain unit tests).
        return ModList.get() != null && ModList.get().isLoaded(modId);
    }

    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public java.util.List<String> modListLines() {
        return ModList.get().getMods().stream()
                .map(mod -> "[" + mod.getModId() + "]  " + mod.getDisplayName() + "[" + mod.getVersion() + "]  "
                        + mod.getOwningFile().getFile().getFileName())
                .toList();
    }

    @Override
    public net.minecraft.world.item.crafting.Ingredient componentsIngredient(net.minecraft.world.item.ItemStack stack) {
        return net.neoforged.neoforge.common.crafting.DataComponentIngredient.of(false, stack);
    }

    @Override
    public String modVersion() {
        return ModList.get().getModContainerById(McheliCePort.MOD_ID)
                .map(container -> container.getModInfo().getVersion().toString()).orElse("");
    }

    @Override
    public net.minecraft.client.gui.screens.@org.jspecify.annotations.Nullable Screen createConfigScreen(
            net.minecraft.client.gui.screens.@org.jspecify.annotations.Nullable Screen parent) {
        return ModList.get().getModContainerById(McheliCePort.MOD_ID)
                .map(container -> (net.minecraft.client.gui.screens.Screen)
                        new net.neoforged.neoforge.client.gui.ConfigurationScreen(container, parent))
                .orElse(null);
    }

    @Override
    public Path gameDir() {
        return FMLPaths.GAMEDIR.get();
    }

    /** The mod jar's files, through FML. */
    public static ModFiles modFiles(net.neoforged.fml.ModContainer container) {
        net.neoforged.fml.jarcontents.JarContents contents =
                container.getModInfo().getOwningFile().getFile().getContents();
        return new ModFiles() {
            @Override
            public void visitFiles(String folder, io.github.mcheliceport.content.addon.BuiltinAddonPack.FileVisitor visitor)
                    throws java.io.IOException {
                try {
                    contents.visitContent(folder, (path, resource) -> {
                        try {
                            visitor.visit(path, resource::open);
                        } catch (java.io.IOException e) {
                            throw new java.io.UncheckedIOException(e);
                        }
                    });
                } catch (java.io.UncheckedIOException e) {
                    throw e.getCause();
                }
            }

            @Override
            public boolean containsFile(String path) {
                return contents.containsFile(path);
            }
        };
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R, T extends R> RegEntry<R, T> register(ResourceKey<? extends Registry<R>> registry, Identifier id,
            Function<Identifier, ? extends T> factory) {
        DeferredRegister<R> register;
        synchronized (REGISTERS) {
            register = (DeferredRegister<R>) REGISTERS.computeIfAbsent(registry, key -> {
                DeferredRegister<R> created = DeferredRegister.create(registry, McheliCePort.MOD_ID);
                if (modBus != null) {
                    created.register(modBus);
                }
                return created;
            });
        }
        DeferredHolder<R, T> holder = register.register(id.getPath(), factory);
        return new RegEntry<>(holder.getKey(), () -> holder);
    }

    @Override
    public net.minecraft.world.level.material.FlowingFluid createFuelFluid(boolean source) {
        return source ? new net.neoforged.neoforge.fluids.BaseFlowingFluid.Source(NeoForgeFluids.FUEL_PROPERTIES)
                : new net.neoforged.neoforge.fluids.BaseFlowingFluid.Flowing(NeoForgeFluids.FUEL_PROPERTIES);
    }

    @Override
    public <M extends net.minecraft.world.inventory.AbstractContainerMenu> net.minecraft.world.inventory.MenuType<M>
            createMenuType(MenuFactory<M> factory) {
        return net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(factory::create);
    }

    @Override
    public void openMenu(net.minecraft.server.level.ServerPlayer player, net.minecraft.world.MenuProvider menu,
            java.util.function.Consumer<net.minecraft.network.RegistryFriendlyByteBuf> data) {
        player.openMenu(menu, data);
    }

    @Override
    public net.minecraft.world.item.CreativeModeTab.Builder creativeTabBuilder(Identifier after) {
        return net.minecraft.world.item.CreativeModeTab.builder().withTabsBefore(after);
    }

    @Override
    public net.minecraft.server.packs.repository.Pack.Metadata packMetadata(
            net.minecraft.network.chat.Component description, boolean hidden) {
        return new net.minecraft.server.packs.repository.Pack.Metadata(description,
                net.minecraft.server.packs.repository.PackCompatibility.COMPATIBLE,
                net.minecraft.world.flag.FeatureFlagSet.of(), java.util.List.of(), hidden);
    }

    @Override
    public net.minecraft.world.item.Item.Properties noCombineRepair(net.minecraft.world.item.Item.Properties properties) {
        return properties.setNoCombineRepair();
    }

    @Override
    public <T extends net.minecraft.world.entity.Entity> net.minecraft.world.entity.EntityType.Builder<T> trackDeltas(
            net.minecraft.world.entity.EntityType.Builder<T> builder) {
        return builder.setShouldReceiveVelocityUpdates(true);
    }

    @Override
    public io.github.mcheliceport.platform.ChunkTickets chunkTickets(Identifier id) {
        var controller = new net.neoforged.neoforge.common.world.chunk.TicketController(id,
                (level, helper) -> helper.getEntityTickets().keySet().forEach(helper::removeAllTickets));
        TICKET_CONTROLLERS.add(controller);
        return controller::forceChunk;
    }

    @Override
    public net.minecraft.nbt.CompoundTag persistentData(net.minecraft.world.entity.Entity entity) {
        return entity.getPersistentData();
    }

    @Override
    public net.minecraft.server.level.ServerPlayer fakePlayer(net.minecraft.server.level.ServerLevel level,
            com.mojang.authlib.GameProfile profile) {
        return net.neoforged.neoforge.common.util.FakePlayerFactory.get(level, profile);
    }

    @Override
    public net.minecraft.world.level.block.state.BlockState fluidPlaceBlock(net.minecraft.world.level.LevelAccessor level,
            net.minecraft.core.BlockPos pos, net.minecraft.core.BlockPos liquidPos,
            net.minecraft.world.level.block.state.BlockState state) {
        return net.neoforged.neoforge.event.EventHooks.fireFluidPlaceBlockEvent(level, pos, liquidPos, state);
    }

    @Override
    public java.util.Collection<net.minecraft.server.level.ServerPlayer> playersTracking(
            net.minecraft.world.entity.Entity entity) {
        return entity.level() instanceof net.minecraft.server.level.ServerLevel level
                ? level.getChunkSource().chunkMap.getPlayersWatching(entity) : java.util.List.of();
    }

    @Override
    @SuppressWarnings("DataFlowIssue") // vanilla blocks ignore the explosion; modded ones get none on the client
    public float explosionResistance(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
            net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.material.FluidState fluid) {
        return Math.max(state.getExplosionResistance(level, pos, null), fluid.getExplosionResistance(level, pos, null));
    }

    @Override
    public net.minecraft.world.InteractionResult useItemFirst(net.minecraft.world.item.ItemStack stack,
            net.minecraft.world.item.context.UseOnContext context) {
        return stack.onItemUseFirst(context);
    }

    @Override
    public boolean explosionCancelled(net.minecraft.world.level.Level level,
            net.minecraft.world.level.ServerExplosion explosion) {
        return net.neoforged.neoforge.event.EventHooks.onExplosionStart(level, explosion);
    }

    @Override
    public void explosionDetonate(net.minecraft.world.level.Level level, net.minecraft.world.level.ServerExplosion explosion,
            java.util.List<net.minecraft.world.entity.Entity> entities, java.util.List<net.minecraft.core.BlockPos> blocks) {
        net.neoforged.neoforge.event.EventHooks.onExplosionDetonate(level, explosion, entities, blocks);
    }

    @Override
    public boolean isFluidContainer(net.minecraft.world.item.ItemStack stack) {
        return NeoForgeTransfer.isFluidContainer(stack);
    }

    @Override
    public @org.jspecify.annotations.Nullable Drained drainContainer(net.minecraft.world.item.ItemStack input,
            java.util.function.Predicate<net.minecraft.world.level.material.Fluid> accepts, int max,
            java.util.function.Predicate<net.minecraft.world.item.ItemStack> canCommit) {
        return NeoForgeTransfer.drainContainer(input, accepts, max, canCommit);
    }

    @Override
    public boolean isBattery(net.minecraft.world.item.ItemStack stack) {
        return NeoForgeTransfer.handler(stack) != null;
    }

    @Override
    public int extractItemEnergy(net.minecraft.world.item.ItemStack stack, int max) {
        var handler = NeoForgeTransfer.handler(stack);
        return handler != null ? NeoForgeTransfer.extract(handler, max) : 0;
    }

    @Override
    public int insertItemEnergy(net.minecraft.world.item.ItemStack stack, int max) {
        var handler = NeoForgeTransfer.handler(stack);
        return handler != null ? NeoForgeTransfer.insert(handler, max) : 0;
    }

    @Override
    public boolean canSend(net.minecraft.server.level.ServerPlayer player,
            net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<?> type) {
        return player.connection.hasChannel(type);
    }

    @Override
    public void sendToPlayer(net.minecraft.server.level.ServerPlayer player,
            net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload);
    }

    @Override
    public void sendToPlayersTrackingEntity(net.minecraft.world.entity.Entity entity,
            net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntity(entity, payload);
    }

    @Override
    public void sendToServer(net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(payload);
    }

    /**
     * Hooks every register made so far, and every later one, to the mod bus. Called once from the
     * mod constructor.
     */
    public static void bind(IEventBus bus) {
        // The fuel's fluid type has to be registered before the fluid registry event.
        NeoForgeFluids.FUEL_TYPE.getKey();
        synchronized (REGISTERS) {
            if (modBus != null) {
                throw new IllegalStateException("NeoForgePlatform is already bound");
            }
            modBus = bus;
            REGISTERS.values().forEach(register -> register.register(bus));
        }
        bus.addListener((net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent event) ->
                TICKET_CONTROLLERS.forEach(event::register));
    }
}
