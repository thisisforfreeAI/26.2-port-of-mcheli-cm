package io.github.mcheliceport.content;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/**
 * All loaded definitions of one content type, by content name.
 *
 * <p>Ported from upstream {@code com.norwood.mcheli.helper.info.ContentRegistry}. Entries keep
 * the order in which they were loaded (upstream used a hash map, so its order was arbitrary). A
 * definition loaded later under the same name replaces the earlier one; that is how addon packs
 * override stock content. The replacing definition inherits the author of the one it replaces if
 * it names none and comes from another pack.
 *
 * <p>Upstream's in-place reload ({@code /mcheli reconfig}) is not ported yet.
 */
public final class ContentRegistry<T extends BaseInfo> {

    private final Class<T> contentClass;
    private final String dir;
    private final Map<String, T> registry;

    private ContentRegistry(Class<T> contentClass, String dir, Map<String, T> table) {
        this.contentClass = contentClass;
        this.dir = dir;
        this.registry = Collections.unmodifiableMap(new LinkedHashMap<>(table));
    }

    public static <T extends BaseInfo> Builder<T> builder(Class<T> type, String dir) {
        return new Builder<>(type, dir);
    }

    /** An empty registry, for before content has loaded. */
    public static <T extends BaseInfo> ContentRegistry<T> empty(Class<T> type, String dir) {
        return new ContentRegistry<>(type, dir, Map.of());
    }

    public @Nullable T get(@Nullable String key) {
        return key == null ? null : this.registry.get(key);
    }

    public @Nullable T findFirst(Predicate<? super T> filter) {
        return this.registry.values().stream().filter(filter).findFirst().orElse(null);
    }

    public List<T> values() {
        return List.copyOf(this.registry.values());
    }

    public Collection<Map.Entry<String, T>> entries() {
        return this.registry.entrySet();
    }

    public void forEachValue(Consumer<? super T> action) {
        this.registry.values().forEach(action);
    }

    public boolean contains(String key) {
        return this.registry.containsKey(key);
    }

    public int size() {
        return this.registry.size();
    }

    public Class<T> getType() {
        return this.contentClass;
    }

    public String getDirectoryName() {
        return this.dir;
    }

    public static final class Builder<E extends BaseInfo> {

        private final Class<E> clazz;
        private final String dirName;
        private final Map<String, E> map = new LinkedHashMap<>();

        Builder(Class<E> clazz, String dir) {
            this.clazz = clazz;
            this.dirName = dir;
        }

        /** Adds a definition, replacing any earlier one with the same name in its place. */
        public void put(E content) {
            String key = content.getLocation().path();
            E previous = this.map.get(key);
            if (previous != null
                    && !previous.getLocation().addonDomain().equals(content.getLocation().addonDomain())
                    && content.author.isBlank() && !previous.author.isBlank()) {
                content.author = previous.author;
            }
            this.map.put(key, content);
        }

        public ContentRegistry<E> build() {
            return new ContentRegistry<>(this.clazz, this.dirName, this.map);
        }
    }
}
