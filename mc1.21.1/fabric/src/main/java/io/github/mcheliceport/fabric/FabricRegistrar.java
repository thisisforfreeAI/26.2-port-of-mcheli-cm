package io.github.mcheliceport.fabric;

import io.github.mcheliceport.platform.Registrar;
import io.github.mcheliceport.platform.registry.DeferredHolder;
import io.github.mcheliceport.platform.registry.DeferredRegister;
import io.github.mcheliceport.registry.FuelFluid;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Registers straight into the vanilla registries, which Fabric leaves open during mod init. */
public final class FabricRegistrar implements Registrar {

    private static final List<Runnable> AFTER_REGISTRATION = new ArrayList<>();

    @Override
    @SuppressWarnings("unchecked")
    public <R> void register(DeferredRegister<R> register) {
        Registry<R> registry = (Registry<R>) BuiltInRegistries.REGISTRY.get(register.getRegistryName());
        if (registry == null) {
            throw new IllegalStateException("No built-in registry " + register.getRegistryName());
        }
        for (DeferredHolder<R, ? extends R> holder : register.getEntries()) {
            Registry.register(registry, holder.getId(), DeferredRegister.makeValue(holder));
        }
    }

    @Override
    public void alwaysUpdateVelocity(EntityType.Builder<?> builder) {
        // Vanilla already tracks velocity for every type but a few projectiles.
    }

    @Override
    public FuelFluid.Source fuelSource() {
        return new FuelFluid.Source();
    }

    @Override
    public FuelFluid.Flowing fuelFlowing() {
        return new FuelFluid.Flowing();
    }

    @Override
    public <M extends AbstractContainerMenu> MenuType<M> menuType(MenuFactory<M> factory) {
        return new ExtendedScreenHandlerType<>((int id, Inventory inventory, byte[] data) -> {
            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(data),
                    inventory.player.registryAccess());
            try {
                return factory.create(id, inventory, buf);
            } finally {
                buf.release();
            }
        }, ByteBufCodecs.BYTE_ARRAY);
    }

    @Override
    public void openMenu(ServerPlayer player, MenuProvider provider, Consumer<RegistryFriendlyByteBuf> data) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), player.registryAccess());
        byte[] bytes;
        try {
            data.accept(buf);
            bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
        } finally {
            buf.release();
        }
        player.openMenu(new ExtendedScreenHandlerFactory<byte[]>() {
            @Override
            public byte[] getScreenOpeningData(ServerPlayer p) {
                return bytes;
            }

            @Override
            public Component getDisplayName() {
                return provider.getDisplayName();
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inventory, Player p) {
                return provider.createMenu(id, inventory, p);
            }
        });
    }

    @Override
    public <T extends BlockEntity> BlockEntityType<T> blockEntityType(BiFunction<BlockPos, BlockState, T> factory,
            Block... blocks) {
        return FabricBlockEntityTypeBuilder.create(factory::apply, blocks).build();
    }

    @Override
    public CreativeModeTab creativeTab(Component title, Supplier<ItemStack> icon,
            CreativeModeTab.DisplayItemsGenerator items, ResourceLocation after) {
        return FabricItemGroup.builder().title(title).icon(icon).displayItems(items).build();
    }

    @Override
    public void entityAttributes(Supplier<? extends EntityType<? extends LivingEntity>> type,
            Supplier<AttributeSupplier> attributes) {
        AFTER_REGISTRATION.add(() -> FabricDefaultAttributeRegistry.register(type.get(), attributes.get()));
    }

    @Override
    public void afterRegistration(Runnable task) {
        AFTER_REGISTRATION.add(task);
    }

    static void runAfterRegistration() {
        AFTER_REGISTRATION.forEach(Runnable::run);
        AFTER_REGISTRATION.clear();
    }
}
