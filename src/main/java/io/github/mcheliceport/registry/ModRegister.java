package io.github.mcheliceport.registry;

import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.platform.Platform;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * One registry's entries in the {@code mcheli_ce_port} namespace: a loader-neutral version of
 * NeoForge's {@code DeferredRegister} with the same method names, backed by {@link Platform#register}.
 */
public class ModRegister<R> {
    private final ResourceKey<? extends Registry<R>> registry;
    private final List<RegEntry<R, ?>> entries = new ArrayList<>();

    protected ModRegister(ResourceKey<? extends Registry<R>> registry) {
        this.registry = registry;
    }

    public static <R> ModRegister<R> create(ResourceKey<? extends Registry<R>> registry) {
        return new ModRegister<>(registry);
    }

    public static Items createItems() {
        return new Items();
    }

    public static Blocks createBlocks() {
        return new Blocks();
    }

    public static Entities createEntities() {
        return new Entities();
    }

    public static DataComponents createDataComponents() {
        return new DataComponents();
    }

    public <T extends R> RegEntry<R, T> register(String name, Supplier<? extends T> factory) {
        return register(name, id -> factory.get());
    }

    public <T extends R> RegEntry<R, T> register(String name, Function<Identifier, ? extends T> factory) {
        RegEntry<R, T> entry = Platform.get().register(registry,
                Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, name), factory);
        entries.add(entry);
        return entry;
    }

    /** Every entry added so far, in order. */
    public List<RegEntry<R, ?>> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    public ResourceKey<? extends Registry<R>> getRegistryKey() {
        return registry;
    }

    public Identifier getRegistryName() {
        return registry.identifier();
    }

    public static final class Items extends ModRegister<Item> {
        private Items() {
            super(Registries.ITEM);
        }

        public <I extends Item> ItemEntry<I> registerItem(String name, Function<Item.Properties, ? extends I> factory,
                UnaryOperator<Item.Properties> properties) {
            RegEntry<Item, I> entry = register(name, id -> factory.apply(
                    properties.apply(new Item.Properties()).setId(ResourceKey.create(Registries.ITEM, id))));
            return new ItemEntry<>(entry);
        }

        public <I extends Item> ItemEntry<I> registerItem(String name, Function<Item.Properties, ? extends I> factory) {
            return registerItem(name, factory, UnaryOperator.identity());
        }
    }

    public static final class Blocks extends ModRegister<Block> {
        private Blocks() {
            super(Registries.BLOCK);
        }

        public <B extends Block> BlockEntry<B> registerBlock(String name,
                Function<BlockBehaviour.Properties, ? extends B> factory,
                UnaryOperator<BlockBehaviour.Properties> properties) {
            RegEntry<Block, B> entry = register(name, id -> factory.apply(
                    properties.apply(BlockBehaviour.Properties.of()).setId(ResourceKey.create(Registries.BLOCK, id))));
            return new BlockEntry<>(entry);
        }
    }

    public static final class Entities extends ModRegister<EntityType<?>> {
        private Entities() {
            super(Registries.ENTITY_TYPE);
        }

        public <E extends Entity> RegEntry<EntityType<?>, EntityType<E>> registerEntityType(String name,
                EntityType.EntityFactory<E> factory, MobCategory category,
                UnaryOperator<EntityType.Builder<E>> builder) {
            return register(name, id -> builder.apply(EntityType.Builder.of(factory, category))
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id)));
        }
    }

    public static final class DataComponents extends ModRegister<DataComponentType<?>> {
        private DataComponents() {
            super(Registries.DATA_COMPONENT_TYPE);
        }

        public <D> RegEntry<DataComponentType<?>, DataComponentType<D>> registerComponentType(String name,
                UnaryOperator<DataComponentType.Builder<D>> builder) {
            return register(name, () -> builder.apply(DataComponentType.<D>builder()).build());
        }
    }
}
