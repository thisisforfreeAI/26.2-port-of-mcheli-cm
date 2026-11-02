package io.github.mcheliceport.platform.loader;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import org.jspecify.annotations.Nullable;

/** Extra data the mod attaches to an entity's render state (NeoForge: {@code getRenderData}). */
public final class RenderData {

    private RenderData() {
    }

    public static final class Key<T> {
        final ContextKey<T> key;

        private Key(Identifier id) {
            this.key = new ContextKey<>(id);
        }
    }

    public static <T> Key<T> key(Identifier id) {
        return new Key<>(id);
    }

    public static <T> @Nullable T get(EntityRenderState state, Key<T> key) {
        return state.getRenderData(key.key);
    }

    public static <T> void set(EntityRenderState state, Key<T> key, @Nullable T value) {
        state.setRenderData(key.key, value);
    }
}
