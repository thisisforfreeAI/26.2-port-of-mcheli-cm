package io.github.mcheliceport.shim.client;

import io.github.mcheliceport.mixin.client.GameRendererInvoker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import org.jspecify.annotations.Nullable;

/**
 * 26.2's {@code GameRenderer} post effect calls. 26.2 names an effect by id
 * ({@code ns:name} for {@code post_effect/name.json}); 1.21.1 loads {@code shaders/post/name.json}.
 */
public final class PostEffects {
    private PostEffects() {}

    private static ResourceLocation path(ResourceLocation id) {
        return ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "shaders/post/" + id.getPath() + ".json");
    }

    /** The current effect's 26.2 id, or null. */
    public static @Nullable ResourceLocation current(GameRenderer renderer) {
        var chain = renderer.currentEffect();
        if (chain == null) {
            return null;
        }
        ResourceLocation loaded = ResourceLocation.tryParse(chain.getName());
        if (loaded == null || !loaded.getPath().startsWith("shaders/post/") || !loaded.getPath().endsWith(".json")) {
            return loaded;
        }
        String name = loaded.getPath().substring("shaders/post/".length(), loaded.getPath().length() - 5);
        return ResourceLocation.fromNamespaceAndPath(loaded.getNamespace(), name);
    }

    public static void set(GameRenderer renderer, ResourceLocation id) {
        ((GameRendererInvoker) renderer).mcheli$loadEffect(path(id));
    }

    public static void clear(GameRenderer renderer) {
        renderer.shutdownEffect();
    }
}
