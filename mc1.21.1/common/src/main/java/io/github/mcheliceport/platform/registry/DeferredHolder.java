package io.github.mcheliceport.platform.registry;

import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/**
 * A registry entry that is filled in when the loader registers it: the loader-neutral stand-in
 * for NeoForge's {@code DeferredHolder}, with the same methods the 26.2 code calls.
 */
public class DeferredHolder<R, T extends R> implements Supplier<T> {
    private final ResourceKey<R> key;
    private final Supplier<? extends T> factory;
    private T value;

    DeferredHolder(ResourceKey<R> key, Supplier<? extends T> factory) {
        this.key = key;
        this.factory = factory;
    }

    /** Called once by the loader when it registers this entry. */
    T create() {
        if (value == null) {
            value = Objects.requireNonNull(factory.get(), () -> "Registry entry " + key + " made null");
        }
        return value;
    }

    @Override
    public T get() {
        if (value == null) {
            throw new IllegalStateException("Registry entry " + key.location() + " is not registered yet");
        }
        return value;
    }

    public T value() {
        return get();
    }

    public boolean isBound() {
        return value != null;
    }

    public ResourceLocation getId() {
        return key.location();
    }

    public ResourceKey<R> getKey() {
        return key;
    }

    /** The registry holder of the value. */
    @SuppressWarnings("unchecked")
    public Holder<R> getDelegate() {
        Registry<R> registry = (Registry<R>) BuiltInRegistries.REGISTRY.get(key.registry());
        if (registry == null) {
            throw new IllegalStateException("No built-in registry " + key.registry());
        }
        return registry.getHolderOrThrow(key);
    }

    @SuppressWarnings("unchecked")
    public boolean is(net.minecraft.tags.TagKey<R> tag) {
        return getDelegate().is(tag);
    }
}
