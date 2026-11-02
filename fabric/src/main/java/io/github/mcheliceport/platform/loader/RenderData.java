package io.github.mcheliceport.platform.loader;

import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/** Extra data the mod attaches to an entity's render state (Fabric: {@link FabricRenderState}). */
public final class RenderData {

    private RenderData() {
    }

    public static final class Key<T> {
        final RenderStateDataKey<T> key;

        private Key(Identifier id) {
            this.key = RenderStateDataKey.create(id::toString);
        }
    }

    public static <T> Key<T> key(Identifier id) {
        return new Key<>(id);
    }

    public static <T> @Nullable T get(EntityRenderState state, Key<T> key) {
        return ((FabricRenderState) state).getData(key.key);
    }

    public static <T> void set(EntityRenderState state, Key<T> key, @Nullable T value) {
        ((FabricRenderState) state).setData(key.key, value);
    }
}
