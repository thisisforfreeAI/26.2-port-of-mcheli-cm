package io.github.mcheliceport.registry;

import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * A registered (or about to be registered) entry, the loader-neutral stand-in for NeoForge's
 * {@code DeferredHolder}. {@link #get()} throws until the loader has registered the entry.
 *
 * @param <R> the registry's type, e.g. {@code Item}
 * @param <T> the entry's own type, e.g. {@code BlockItem}
 */
public class RegEntry<R, T extends R> implements Supplier<T> {
    private final ResourceKey<R> key;
    private final Supplier<? extends Holder<R>> holder;

    public RegEntry(ResourceKey<R> key, Supplier<? extends Holder<R>> holder) {
        this.key = Objects.requireNonNull(key);
        this.holder = Objects.requireNonNull(holder);
    }

    /** Copies {@code other}, for the typed subclasses. */
    protected RegEntry(RegEntry<R, ? extends T> other) {
        this(other.key, other.holder);
    }

    public static <R, T extends R> RegEntry<R, T> of(ResourceKey<? extends Registry<R>> registry, Identifier id,
            Supplier<? extends Holder<R>> holder) {
        return new RegEntry<>(ResourceKey.create(registry, id), holder);
    }

    @Override
    @SuppressWarnings("unchecked")
    public T get() {
        return (T) holder.get().value();
    }

    /** The registry holder. */
    public Holder<R> holder() {
        return holder.get();
    }

    public ResourceKey<R> getKey() {
        return key;
    }

    public Identifier getId() {
        return key.identifier();
    }

    @Override
    public String toString() {
        return "RegEntry[" + key + "]";
    }
}
