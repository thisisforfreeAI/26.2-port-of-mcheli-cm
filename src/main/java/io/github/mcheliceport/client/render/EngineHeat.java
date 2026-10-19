package io.github.mcheliceport.client.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import io.github.mcheliceport.client.particle.EffectPerformance;
import io.github.mcheliceport.config.ClientConfig;
import io.github.mcheliceport.config.EffectQuality;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.OptionalDouble;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** Local exhaust refraction, one shared color copy and one scissored pass before hands/HUD. */
@EventBusSubscriber(modid = "mcheli_ce_port", value = Dist.CLIENT)
public final class EngineHeat {
    private static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("mcheli_ce_port", "pipeline/engine_heat"))
            .withVertexShader("core/screenquad")
            .withFragmentShader(Identifier.fromNamespaceAndPath("mcheli_ce_port", "post/engine_heat"))
            .withBindGroupLayout(BindGroupLayout.builder().withSampler("SceneSampler").withSampler("DepthSampler")
                    .withUniform("HeatConfig", UniformType.UNIFORM_BUFFER).build()).build();
    private record Source(Vec3 position, Vec3 direction, Vec3 motion, float radius, float strength, boolean jet, long born) {}
    private static final LinkedHashMap<Long, Source> sources = new LinkedHashMap<>();
    private static WeakReference<ClientLevel> world = new WeakReference<>(null);
    private static final EngineHeatLook.Screen[] screen = new EngineHeatLook.Screen[8];
    private static final Source[] considered = new Source[16];
    private static final ByteBuffer data = ByteBuffer.allocateDirect(16 + 8 * 32).order(ByteOrder.nativeOrder());
    private static TextureTarget copy;
    private static GpuBuffer uniforms;
    private static int drawn, copies;
    private static final Matrix4f worldProjection = new Matrix4f();
    private static boolean projectionReady;
    private EngineHeat() {}
    public static void registerPipelines(RegisterRenderPipelinesEvent event) { event.registerPipeline(PIPELINE); }
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
        if (sources.isEmpty()) release();
    }
    public static void clear() { sources.clear(); world.clear(); drawn = copies = 0; release(); }
    private static void release() {
        if (copy != null) { copy.destroyBuffers(); copy = null; }
        if (uniforms != null) { uniforms.close(); uniforms = null; }
    }
    public static int sourceCount() { return sources.size(); }
    public static int drawn() { return drawn; }
    public static int copies() { return copies; }
    public static void captureProjection(org.joml.Matrix4fc projection) {
        worldProjection.set(projection); projectionReady = true;
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent.AfterLevel event) {
        drawn = copies = 0;
        var mc = Minecraft.getInstance();
        if (mc.level == null || world.get() != mc.level || !enabled() || sources.isEmpty() || !projectionReady) return;
        var target = mc.gameRenderer.mainRenderTarget();
        if (target.getColorTexture() == null || target.getDepthTextureView() == null) return;
        int width = target.width, height = target.height;
        var camera = event.getLevelRenderState().cameraRenderState;
        Matrix4f projection = new Matrix4f(worldProjection).mul(event.getModelViewMatrix());
        var quality = ClientConfig.get(ClientConfig.EFFECT_BUDGET);
        boolean zeroToOne = RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();
        int cap = EngineHeatLook.cap(quality);
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        long now = mc.level.getGameTime();
        int x0 = width, y0 = height, x1 = 0, y1 = 0;
        // Nearest visible capsules win without sorting or scanning entities.
        for (int i = 0; i < 16 && drawn < cap; i++) {
            Source chosen = null; double nearest = Double.POSITIVE_INFINITY;
            for (Source source : sources.values()) {
                boolean visited = false;
                for (int j = 0; j < i; j++) if (considered[j] == source) { visited = true; break; }
                if (visited) continue;
                double distance = camera.pos.distanceToSqr(source.position);
                if (distance < nearest && distance < 48 * 48 && now >= source.born && now - source.born <= 6) {
                    chosen = source; nearest = distance;
                }
            }
            if (chosen == null) break;
            considered[i] = chosen;
            float fade = (float) Math.clamp((48 - Math.sqrt(nearest)) / 16, 0, 1);
            Vec3 start = chosen.position.add(chosen.motion.scale(now - chosen.born + partial)).subtract(camera.pos);
            Vec3 end = start.add(chosen.direction.scale(EngineHeatLook.length(chosen.jet, chosen.strength)));
            var s = EngineHeatLook.project(projection, start, end, chosen.radius, chosen.strength * fade,
                    width, height, zeroToOne);
            if (s == null) continue;
            screen[drawn++] = s;
            float pad = s.radius() * 1.5F + 4;
            x0 = Math.min(x0, (int) Math.floor(Math.min(s.ax(), s.bx()) * width - pad));
            x1 = Math.max(x1, (int) Math.ceil(Math.max(s.ax(), s.bx()) * width + pad));
            y0 = Math.min(y0, (int) Math.floor(Math.min(s.ay(), s.by()) * height - pad));
            y1 = Math.max(y1, (int) Math.ceil(Math.max(s.ay(), s.by()) * height + pad));
        }
        x0 = Math.max(0, x0); y0 = Math.max(0, y0); x1 = Math.min(width, x1); y1 = Math.min(height, y1);
        if (drawn == 0 || x1 <= x0 || y1 <= y0) return;
        if (copy == null || copy.width != width || copy.height != height || copy.getColorTexture().getFormat() != target.getColorTexture().getFormat()) {
            release(); copy = new TextureTarget("McHeli shared exhaust heat", width, height, false, target.getColorTexture().getFormat());
        }
        if (uniforms == null) uniforms = RenderSystem.getDevice().createBuffer(() -> "McHeli exhaust heat uniforms", 136, data.capacity());
        data.clear();
        data.putFloat(width).putFloat(height).putFloat((now % 4096 + partial) / 20).putFloat(drawn);
        for (int i = 0; i < 8; i++) {
            var s = i < drawn ? screen[i] : null;
            data.putFloat(s == null ? 0 : s.ax()).putFloat(s == null ? 0 : s.ay()).putFloat(s == null ? 0 : s.az()).putFloat(s == null ? 0 : s.radius());
            data.putFloat(s == null ? 0 : s.bx()).putFloat(s == null ? 0 : s.by()).putFloat(s == null ? 0 : s.bz()).putFloat(s == null ? 0 : s.strength());
        }
        data.flip();
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.writeToBuffer(uniforms.slice(), data);
        encoder.copyTextureToTexture(target.getColorTexture(), copy.getColorTexture(), 0, 0, 0, 0, 0, width, height);
        copies = 1;
        try (var pass = encoder.createRenderPass(() -> "McHeli local exhaust refraction", target.getColorTextureView(), Optional.empty(), null, OptionalDouble.empty())) {
            pass.setPipeline(PIPELINE); RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("HeatConfig", uniforms);
            pass.bindTexture("SceneSampler", copy.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.bindTexture("DepthSampler", target.getDepthTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.enableScissor(x0, y0, x1 - x0, y1 - y0);
            pass.draw(3, 1, 0, 0);
        }
    }
}
