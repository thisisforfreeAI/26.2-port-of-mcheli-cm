package io.github.mcheliceport.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.client.particle.EffectPerformance;
import io.github.mcheliceport.config.ClientConfig;
import io.github.mcheliceport.config.EffectQuality;
import io.github.mcheliceport.shim.client.event.ClientTickEvent;
import io.github.mcheliceport.shim.client.event.SubscribeEvent;
import java.lang.ref.WeakReference;
import java.util.LinkedHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL30;

/**
 * Local exhaust refraction: one shared copy of the frame and one scissored pass after the level,
 * before the hand and HUD. 26.2 draws it with its GPU device API; here it is a core shader drawn
 * straight on the main target ({@code GameRendererLevelMixin} calls {@link #render}).
 */
@io.github.mcheliceport.shim.client.event.ClientSubscriber
public final class EngineHeat {
    private record Source(Vec3 position, Vec3 direction, Vec3 motion, float radius, float strength, boolean jet, long born) {}
    private static final LinkedHashMap<Long, Source> sources = new LinkedHashMap<>();
    private static WeakReference<ClientLevel> world = new WeakReference<>(null);
    private static final EngineHeatLook.Screen[] screen = new EngineHeatLook.Screen[8];
    private static final Source[] considered = new Source[16];
    private static final Matrix4f worldProjection = new Matrix4f();
    private static ShaderInstance shader;
    private static TextureTarget copy;
    private static int drawn, copies;

    private EngineHeat() {}

    /** Resource reload: the shader is compiled again (on the render thread). */
    public static void reloadShader(ResourceProvider resources) {
        if (shader != null) {
            shader.close();
            shader = null;
        }
        try {
            shader = new ShaderInstance(resources, "mcheli_ce_port_engine_heat", DefaultVertexFormat.POSITION);
        } catch (Exception e) {
            McheliCePort.LOGGER.warn("Could not load the engine heat shader; heat haze is off", e);
        }
    }

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
    }
    public static int sourceCount() { return sources.size(); }
    public static int drawn() { return drawn; }
    public static int copies() { return copies; }
    public static void captureProjection(Matrix4fc projection) { worldProjection.set(projection); }

    /** After the level is drawn: {@code modelView} is the camera rotation, {@code projection} the world's. */
    public static void render(Matrix4fc modelView, Matrix4fc projection) {
        drawn = copies = 0;
        var mc = Minecraft.getInstance();
        if (mc.level == null || world.get() != mc.level || !enabled() || sources.isEmpty() || shader == null) return;
        RenderTarget target = mc.getMainRenderTarget();
        int width = target.width, height = target.height;
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        Matrix4f viewProjection = new Matrix4f(projection).mul(modelView);
        var quality = ClientConfig.get(ClientConfig.EFFECT_BUDGET);
        int cap = EngineHeatLook.cap(quality);
        float partial = mc.getTimer().getGameTimeDeltaPartialTick(false);
        long now = mc.level.getGameTime();
        int x0 = width, y0 = height, x1 = 0, y1 = 0;
        // Nearest visible capsules win without sorting or scanning entities.
        for (int i = 0; i < 16 && drawn < cap; i++) {
            Source chosen = null; double nearest = Double.POSITIVE_INFINITY;
            for (Source source : sources.values()) {
                boolean visited = false;
                for (int j = 0; j < i; j++) if (considered[j] == source) { visited = true; break; }
                if (visited) continue;
                double distance = cameraPos.distanceToSqr(source.position);
                if (distance < nearest && distance < 48 * 48 && now >= source.born && now - source.born <= 6) {
                    chosen = source; nearest = distance;
                }
            }
            if (chosen == null) break;
            considered[i] = chosen;
            float fade = (float) Math.clamp((48 - Math.sqrt(nearest)) / 16, 0, 1);
            Vec3 start = chosen.position.add(chosen.motion.scale(now - chosen.born + partial)).subtract(cameraPos);
            Vec3 end = start.add(chosen.direction.scale(EngineHeatLook.length(chosen.jet, chosen.strength)));
            var s = EngineHeatLook.project(viewProjection, start, end, chosen.radius, chosen.strength * fade,
                    width, height, false);
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
        if (copy == null || copy.width != width || copy.height != height) {
            release();
            copy = new TextureTarget(width, height, false, Minecraft.ON_OSX);
        }
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, target.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, copy.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, width, height, 0, 0, width, height, GL30.GL_COLOR_BUFFER_BIT, GL30.GL_NEAREST);
        copies = 1;
        target.bindWrite(true);

        shader.setSampler("SceneSampler", copy.getColorTextureId());
        shader.setSampler("DepthSampler", target.getDepthTextureId());
        shader.safeGetUniform("Screen").set((float) width, (float) height, (now % 4096 + partial) / 20, (float) drawn);
        for (int i = 0; i < 8; i++) {
            var s = i < drawn ? screen[i] : null;
            shader.safeGetUniform("C" + (2 * i)).set(s == null ? 0 : s.ax(), s == null ? 0 : s.ay(),
                    s == null ? 0 : s.az(), s == null ? 0 : s.radius());
            shader.safeGetUniform("C" + (2 * i + 1)).set(s == null ? 0 : s.bx(), s == null ? 0 : s.by(),
                    s == null ? 0 : s.bz(), s == null ? 0 : s.strength());
        }
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();
        RenderSystem.enableScissor(x0, y0, x1 - x0, y1 - y0);
        RenderSystem.setShader(() -> shader);
        BufferBuilder quad = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        quad.addVertex(0, 0, 0);
        quad.addVertex(1, 0, 0);
        quad.addVertex(1, 1, 0);
        quad.addVertex(0, 1, 0);
        BufferUploader.drawWithShader(quad.buildOrThrow());
        RenderSystem.disableScissor();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }
}
