package io.github.mcheliceport.client.render;

import io.github.mcheliceport.client.particle.EffectPerformance;
import io.github.mcheliceport.config.ClientConfig;
import io.github.mcheliceport.config.EffectQuality;
import io.github.mcheliceport.shim.client.event.ClientTickEvent;
import io.github.mcheliceport.shim.client.event.SubscribeEvent;
import java.lang.ref.WeakReference;
import java.util.LinkedHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Exhaust heat-haze sources. 26.2 draws a refraction post pass from these with its GPU device API;
 * 1.21.1 has no equivalent hook yet, so this keeps the source tracking (and its debug counters)
 * and draws nothing.
 */
@io.github.mcheliceport.shim.client.event.ClientSubscriber
public final class EngineHeat {
    private record Source(Vec3 position, Vec3 direction, Vec3 motion, float radius, float strength, boolean jet, long born) {}
    private static final LinkedHashMap<Long, Source> sources = new LinkedHashMap<>();
    private static WeakReference<ClientLevel> world = new WeakReference<>(null);
    private static final Matrix4f worldProjection = new Matrix4f();
    private static int drawn, copies;

    private EngineHeat() {}

    private static boolean enabled() {
        return EffectQuality.enhanced() && ClientConfig.get(ClientConfig.ENGINE_HEAT_HAZE)
                && EngineHeatLook.enabled(ClientConfig.get(ClientConfig.EFFECT_BUDGET), EffectPerformance.pressure());
    }
    public static void add(ClientLevel level, long key, Vec3 position, Vec3 direction, Vec3 motion,
            float size, float strength, boolean jet) {
        if (!enabled() || Minecraft.getInstance().level != level || strength < 0.3F
                || !Double.isFinite(position.x + position.y + position.z + direction.lengthSqr() + motion.lengthSqr())
                || !Float.isFinite(size + strength)) return;
        if (world.get() != level) { clear(); world = new WeakReference<>(level); }
        if (!sources.containsKey(key) && sources.size() == 16) sources.remove(sources.keySet().iterator().next());
        sources.put(key, new Source(position, direction.normalize(), motion, Math.clamp(size * 0.22F, 0.12F, 0.55F),
                Math.clamp(strength, 0, 1), jet, level.getGameTime()));
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var level = Minecraft.getInstance().level;
        if (level == null || world.get() != level || !enabled()) { clear(); return; }
        long now = level.getGameTime();
        sources.values().removeIf(s -> now < s.born || now - s.born > 6);
    }
    public static void clear() { sources.clear(); world.clear(); drawn = copies = 0; }
    public static int sourceCount() { return sources.size(); }
    public static int drawn() { return drawn; }
    public static int copies() { return copies; }
    public static void captureProjection(org.joml.Matrix4fc projection) { worldProjection.set(projection); }
}
