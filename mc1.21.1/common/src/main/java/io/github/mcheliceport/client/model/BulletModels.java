package io.github.mcheliceport.client.model;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

/**
 * Models of projectiles, cartridges and the A-10 ({@code mcheli:models/bullets/<name>} and the
 * models directory itself): upstream's {@code MCH_ModelManager} entries for bullets. Loaded on first
 * use and kept until resources reload; they are a few hundred triangles each.
 */
public final class BulletModels {

    private static final Map<String, Optional<McheliModel>> MODELS = new ConcurrentHashMap<>();

    private BulletModels() {
    }

    /** A bullet model by name ({@code bullets/<name>}), or null when there is none. */
    public static @Nullable McheliModel bullet(String name) {
        return get("bullets/" + name);
    }

    /** A model by path under {@code mcheli:models/}, or null when there is none (logged once). */
    public static @Nullable McheliModel get(String path) {
        return MODELS.computeIfAbsent(path,
                p -> Optional.ofNullable(AircraftModels.loadModel(Minecraft.getInstance().getResourceManager(), p, false)))
                .orElse(null);
    }

    public static void clear() {
        MODELS.clear();
    }
}
