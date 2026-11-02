package io.github.mcheliceport.neoforge;

import io.github.mcheliceport.platform.Registrar;
import io.github.mcheliceport.platform.registry.DeferredHolder;
import io.github.mcheliceport.platform.registry.DeferredRegister;
import io.github.mcheliceport.registry.FuelFluid;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.RegisterEvent;

public final class NeoForgeRegistrar implements Registrar {

    /** The fuel's NeoForge fluid type; it has no block, as upstream. */
    private static FluidType fuelType;

    static synchronized FluidType fuelType() {
        if (fuelType == null) {
            fuelType = new FluidType(FluidType.Properties.create().descriptionId("fluid_type.mcheli_ce_port.mch_fuel"));
        }
        return fuelType;
    }
    private static boolean fluidTypeListener;

    @Override
    @SuppressWarnings("unchecked")
    public <R> void register(DeferredRegister<R> register) {
        if (!fluidTypeListener) {
            fluidTypeListener = true;
            McheliCePortNeoForge.modBus().addListener((RegisterEvent event) -> event.register(
                    NeoForgeRegistries.Keys.FLUID_TYPES,
                    ResourceLocation.fromNamespaceAndPath(io.github.mcheliceport.McheliCePort.MOD_ID, "mch_fuel"),
                    NeoForgeRegistrar::fuelType));
        }
        ResourceKey<Registry<R>> key = (ResourceKey<Registry<R>>) register.getRegistryKey();
        McheliCePortNeoForge.modBus().addListener((RegisterEvent event) -> {
            if (event.getRegistryKey().equals(key)) {
                for (DeferredHolder<R, ? extends R> holder : register.getEntries()) {
                    event.register(key, holder.getId(), () -> DeferredRegister.makeValue(holder));
                }
            }
        });
    }

    @Override
    public void alwaysUpdateVelocity(EntityType.Builder<?> builder) {
        builder.setShouldReceiveVelocityUpdates(true);
    }

    @Override
    public FuelFluid.Source fuelSource() {
        return new FuelFluid.Source() {
            @Override
            public FluidType getFluidType() {
                return fuelType();
            }
        };
    }

    @Override
    public FuelFluid.Flowing fuelFlowing() {
        return new FuelFluid.Flowing() {
            @Override
            public FluidType getFluidType() {
                return fuelType();
            }
        };
    }

    @Override
    public <M extends AbstractContainerMenu> MenuType<M> menuType(MenuFactory<M> factory) {
        return IMenuTypeExtension.create(factory::create);
    }

    @Override
    public void openMenu(ServerPlayer player, MenuProvider provider, Consumer<RegistryFriendlyByteBuf> data) {
        player.openMenu(provider, data);
    }

    @Override
    public <T extends BlockEntity> BlockEntityType<T> blockEntityType(BiFunction<BlockPos, BlockState, T> factory,
            Block... blocks) {
        return BlockEntityType.Builder.of(factory::apply, blocks).build(null);
    }

    @Override
    public CreativeModeTab creativeTab(Component title, Supplier<ItemStack> icon,
            CreativeModeTab.DisplayItemsGenerator items, ResourceLocation after) {
        return CreativeModeTab.builder().title(title).icon(icon).displayItems(items).withTabsBefore(after).build();
    }

    @Override
    public void entityAttributes(Supplier<? extends EntityType<? extends LivingEntity>> type,
            Supplier<AttributeSupplier> attributes) {
        McheliCePortNeoForge.modBus().addListener(
                (EntityAttributeCreationEvent event) -> event.put(type.get(), attributes.get()));
    }

    @Override
    public void afterRegistration(Runnable task) {
        McheliCePortNeoForge.modBus().addListener((FMLCommonSetupEvent event) -> event.enqueueWork(task));
    }
}
