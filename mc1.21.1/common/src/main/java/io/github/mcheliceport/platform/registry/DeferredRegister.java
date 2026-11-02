package io.github.mcheliceport.platform.registry;

import io.github.mcheliceport.platform.Services;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/**
 * Loader-neutral stand-in for NeoForge's {@code DeferredRegister}: collects entries in shared code;
 * {@link #register()} hands them to the loader (NeoForge's RegisterEvent, or straight into the
 * vanilla registries on Fabric).
 */
public class DeferredRegister<R> {
    private final ResourceKey<? extends Registry<R>> registryKey;
    private final String namespace;
    private final List<DeferredHolder<R, ? extends R>> entries = new ArrayList<>();
    private final List<DeferredHolder<R, ? extends R>> entriesView = Collections.unmodifiableList(entries);
    private boolean registered;

    protected DeferredRegister(ResourceKey<? extends Registry<R>> registryKey, String namespace) {
        this.registryKey = registryKey;
        this.namespace = namespace;
    }

    public static <R> DeferredRegister<R> create(ResourceKey<? extends Registry<R>> key, String namespace) {
        return new DeferredRegister<>(key, namespace);
    }

    public static Items createItems(String namespace) {
        return new Items(namespace);
    }

    public static Blocks createBlocks(String namespace) {
        return new Blocks(namespace);
    }

    public static Entities createEntities(String namespace) {
        return new Entities(namespace);
    }

    public static DataComponents createDataComponents(ResourceKey<Registry<DataComponentType<?>>> key,
            String namespace) {
        return new DataComponents(namespace);
    }

    public ResourceKey<? extends Registry<R>> getRegistryKey() {
        return registryKey;
    }

    public ResourceLocation getRegistryName() {
        return registryKey.location();
    }

    public String getNamespace() {
        return namespace;
    }

    public Collection<DeferredHolder<R, ? extends R>> getEntries() {
        return entriesView;
    }

    @SuppressWarnings("unchecked")
    protected ResourceKey<R> key(String name) {
        return ResourceKey.create((ResourceKey<? extends Registry<R>>) registryKey,
                ResourceLocation.fromNamespaceAndPath(namespace, name));
    }

    protected <H extends DeferredHolder<R, ? extends R>> H add(H holder) {
        if (registered) {
            throw new IllegalStateException("Cannot add " + holder.getId() + " after " + getRegistryName()
                    + " was registered");
        }
        entries.add(holder);
        return holder;
    }

    public <I extends R> DeferredHolder<R, I> register(String name, Supplier<? extends I> factory) {
        return add(new DeferredHolder<>(key(name), factory));
    }

    public <I extends R> DeferredHolder<R, I> register(String name, Function<ResourceLocation, ? extends I> factory) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace, name);
        return register(name, () -> factory.apply(id));
    }

    /** Hands every entry to the loader. Call once, from the loader's entry point. */
    public void register() {
        if (registered) {
            throw new IllegalStateException(getRegistryName() + " is registered twice");
        }
        registered = true;
        Services.REGISTRAR.register(this);
    }

    /** Used by the loader modules: makes the entry's value (once) and returns it. */
    public static <R, T extends R> T makeValue(DeferredHolder<R, T> holder) {
        return holder.create();
    }

    public static final class Items extends DeferredRegister<Item> {
        Items(String namespace) {
            super(Registries.ITEM, namespace);
        }

        public <I extends Item> DeferredItem<I> registerItem(String name,
                Function<Item.Properties, ? extends I> factory, UnaryOperator<Item.Properties> properties) {
            return add(new DeferredItem<>(key(name), () -> factory.apply(properties.apply(new Item.Properties()))));
        }

        public <I extends Item> DeferredItem<I> registerItem(String name,
                Function<Item.Properties, ? extends I> factory) {
            return registerItem(name, factory, UnaryOperator.identity());
        }

        public DeferredItem<Item> registerSimpleItem(String name, UnaryOperator<Item.Properties> properties) {
            return registerItem(name, Item::new, properties);
        }
    }

    public static final class Blocks extends DeferredRegister<Block> {
        Blocks(String namespace) {
            super(Registries.BLOCK, namespace);
        }

        public <B extends Block> DeferredBlock<B> registerBlock(String name,
                Function<Block.Properties, ? extends B> factory, UnaryOperator<Block.Properties> properties) {
            return add(new DeferredBlock<>(key(name), () -> factory.apply(properties.apply(Block.Properties.of()))));
        }
    }

    public static final class Entities extends DeferredRegister<EntityType<?>> {
        Entities(String namespace) {
            super(Registries.ENTITY_TYPE, namespace);
        }

        public <E extends Entity> DeferredHolder<EntityType<?>, EntityType<E>> registerEntityType(String name,
                EntityType.EntityFactory<E> factory, MobCategory category,
                UnaryOperator<EntityType.Builder<E>> builder) {
            return registerEntityType(name, factory, category, builder, false);
        }

        /**
         * {@code velocityUpdates}: send the velocity to clients on every update (NeoForge's
         * {@code setShouldReceiveVelocityUpdates}, Fabric's {@code alwaysUpdateVelocity}).
         */
        public <E extends Entity> DeferredHolder<EntityType<?>, EntityType<E>> registerEntityType(String name,
                EntityType.EntityFactory<E> factory, MobCategory category,
                UnaryOperator<EntityType.Builder<E>> builder, boolean velocityUpdates) {
            String id = getNamespace() + ":" + name;
            return add(new DeferredHolder<>(key(name), () -> {
                EntityType.Builder<E> b = builder.apply(EntityType.Builder.of(factory, category));
                if (velocityUpdates) {
                    Services.REGISTRAR.alwaysUpdateVelocity(b);
                }
                return b.build(id);
            }));
        }
    }

    public static final class DataComponents extends DeferredRegister<DataComponentType<?>> {
        DataComponents(String namespace) {
            super(Registries.DATA_COMPONENT_TYPE, namespace);
        }

        public <D> DeferredHolder<DataComponentType<?>, DataComponentType<D>> registerComponentType(String name,
                UnaryOperator<DataComponentType.Builder<D>> builder) {
            return register(name, () -> builder.apply(DataComponentType.builder()).build());
        }
    }
}
