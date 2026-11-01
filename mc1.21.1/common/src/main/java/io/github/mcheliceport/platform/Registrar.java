package io.github.mcheliceport.platform;

import io.github.mcheliceport.platform.registry.DeferredRegister;
import net.minecraft.world.entity.EntityType;

/** How each loader fills its registries from the shared {@link DeferredRegister}s. */
public interface Registrar {

    /** Registers every entry of {@code register} (now, or when the loader's registry event fires). */
    <R> void register(DeferredRegister<R> register);

    /** Makes entities of the built type send their velocity on every tracking update. */
    void alwaysUpdateVelocity(EntityType.Builder<?> builder);

    /** The fuel fluids (NeoForge subclasses them to attach a FluidType). */
    io.github.mcheliceport.registry.FuelFluid.Source fuelSource();

    io.github.mcheliceport.registry.FuelFluid.Flowing fuelFlowing();

    /** A menu type whose client side gets extra data (NeoForge's {@code IMenuTypeExtension.create}). */
    <M extends net.minecraft.world.inventory.AbstractContainerMenu> net.minecraft.world.inventory.MenuType<M> menuType(
            MenuFactory<M> factory);

    @FunctionalInterface
    interface MenuFactory<M extends net.minecraft.world.inventory.AbstractContainerMenu> {
        M create(int containerId, net.minecraft.world.entity.player.Inventory inventory,
                net.minecraft.network.RegistryFriendlyByteBuf data);
    }

    /** Opens {@code provider}'s menu, sending {@code data} to the client's {@link MenuFactory}. */
    void openMenu(net.minecraft.server.level.ServerPlayer player, net.minecraft.world.MenuProvider provider,
            java.util.function.Consumer<net.minecraft.network.RegistryFriendlyByteBuf> data);

    /** A block entity type (vanilla's constructor is not public on 1.21.1). */
    <T extends net.minecraft.world.level.block.entity.BlockEntity> net.minecraft.world.level.block.entity.BlockEntityType<T> blockEntityType(
            java.util.function.BiFunction<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState, T> factory,
            net.minecraft.world.level.block.Block... blocks);

    /** A creative tab placed after {@code after} (NeoForge orders tabs; Fabric appends them). */
    net.minecraft.world.item.CreativeModeTab creativeTab(net.minecraft.network.chat.Component title,
            java.util.function.Supplier<net.minecraft.world.item.ItemStack> icon,
            net.minecraft.world.item.CreativeModeTab.DisplayItemsGenerator items,
            net.minecraft.resources.ResourceLocation after);
}
