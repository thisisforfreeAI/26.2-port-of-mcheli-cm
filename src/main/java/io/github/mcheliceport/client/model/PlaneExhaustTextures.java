package io.github.mcheliceport.client.model;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

/** Numbered exhaust textures supplied by addon/resource packs, scanned as upstream (at most 100 frames). */
public final class PlaneExhaustTextures {
    private static final Map<String, List<Identifier>> TEXTURES = new ConcurrentHashMap<>();

    private PlaneExhaustTextures() {
    }

    public static List<Identifier> get(ResourceManager resources, String prefix) {
        return TEXTURES.computeIfAbsent(prefix, p -> discover(resources, p));
    }

    public static List<Identifier> discover(ResourceManager resources, String prefix) {
        List<Identifier> frames = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            String path = "textures/exhaustflames/" + prefix.toLowerCase(Locale.ROOT) + i + ".png";
            if (!Identifier.isValidPath(path)) {
                break;
            }
            Identifier id = Identifier.fromNamespaceAndPath("mcheli", path);
            if (resources.getResource(id).isEmpty()) {
                break;
            }
            frames.add(id);
        }
        return List.copyOf(frames);
    }

    public static void clear() {
        TEXTURES.clear();
    }
}
