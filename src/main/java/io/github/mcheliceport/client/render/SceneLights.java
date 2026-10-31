package io.github.mcheliceport.client.render;

import io.github.mcheliceport.config.ClientConfig;
import io.github.mcheliceport.config.EffectQuality;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import com.google.common.reflect.TypeToken;

/**
 * Brief client-side point lights from explosions, muzzle flashes, burning wrecks and flares. The
 * strongest few near the camera light vehicles through the vehicle shader, nearby mobs and players
 * through their light value, and the ground under fires and flares through cached top-face patches
 * (explosions and gun flashes already light the ground through their own flash patches). The
 * world's block light is never changed, so no chunk is rebuilt.
 */
@EventBusSubscriber(modid = "mcheli_ce_port", value = Dist.CLIENT)
public final class SceneLights {

    private static final class Light {
        double x, y, z;
        float radius, r, g, b, intensity;
        long born;
        int life, seed;
        boolean fire;
        Object key;
        // Ground patch for lasting fires and flares, relative to (patchX, patchY, patchZ).
        List<GlowGeometry.Surface> surfaces = List.of();
        double patchX, patchY, patchZ;
        float patchSky;
        float patchRadius;
        long sampled = Long.MIN_VALUE;

        float strength(long now, float partial) {
            float age = now - born + partial;
            if (key != null) {
                // Lasting lights are refreshed each tick while their source burns; they fade only once it stops.
                float off = age - 1;
                float base = off <= 0 ? 1 : Math.clamp(1 - off / Math.max(1, life - 1), 0, 1);
                return intensity * base * (fire ? SceneLightLook.flicker(now + partial, seed) : 1);
            }
            return intensity * SceneLightLook.fade(age, life);
        }
    }

    /** One frame's chosen lights, camera-relative: (x, y, z, radius) and (r, g, b) times strength. */
    public record Frame(int count, float[] position, float[] color) {
        public static final Frame EMPTY = new Frame(0, new float[0], new float[0]);
    }

    private static final int CAP = 96;
    private static final int PATCHES = 4;
    private static final List<Light> LIGHTS = new ArrayList<>();
    private static final Map<Object, Light> KEYED = new HashMap<>();
    private static ClientLevel world;
    private static int seeds;
    private record FireKey(Object source, int channel) {}
    private record FlameCell(int x, int y, int z) {}

    private SceneLights() {
    }

    public static boolean enabled() {
        return EffectQuality.enhanced() && ClientConfig.get(ClientConfig.REALISTIC_LIGHTING);
    }

    public static int count() {
        return LIGHTS.size();
    }

    public static void clear() {
        LIGHTS.clear();
        KEYED.clear();
        world = null;
    }

    private static Light add(Level level, Object key, double x, double y, double z, float radius,
            float r, float g, float b, float intensity, int life, boolean fire) {
        if (!(level instanceof ClientLevel client) || Minecraft.getInstance().level != client || !enabled()
                || !(radius > 0) || !(intensity > 0) || life <= 0 || !Double.isFinite(x + y + z)) {
            return null;
        }
        if (world != client) {
            clear();
            world = client;
        }
        Light light = key == null ? null : KEYED.get(key);
        if (light == null) {
            if (LIGHTS.size() >= CAP && !evictFor(radius * intensity)) {
                return null;
            }
            light = new Light();
            light.key = key;
            light.seed = seeds++;
            LIGHTS.add(light);
            if (key != null) {
                KEYED.put(key, light);
            }
        }
        light.x = x;
        light.y = y;
        light.z = z;
        light.radius = radius;
        light.r = r;
        light.g = g;
        light.b = b;
        light.intensity = intensity;
        light.life = life;
        light.fire = fire;
        light.born = level.getGameTime();
        return light;
    }

    /** Frees a weaker brief light or flame cell before refusing a stronger main fire. */
    private static boolean evictFor(float weight) {
        int weakest = -1;
        float least = weight;
        for (int i = 0; i < LIGHTS.size(); i++) {
            Light light = LIGHTS.get(i);
            float w = light.radius * light.intensity;
            if ((light.key == null || light.key instanceof FlameCell) && w < least) {
                least = w;
                weakest = i;
            }
        }
        if (weakest < 0) {
            return false;
        }
        Light removed=LIGHTS.remove(weakest);
        if(removed.key!=null)KEYED.remove(removed.key);
        return true;
    }

    /** An explosion's fireball: a strong orange light over its first ticks. */
    public static void explosion(Level level, double x, double y, double z, float power) {
        add(level, null, x, y + 0.5, z, SceneLightLook.explosionRadius(power), 1.0F, 0.62F, 0.3F,
                Math.min(1.6F, 0.9F + power * 0.08F), SceneLightLook.explosionLife(power), false);
    }

    /** A muzzle flash: a short light in the flash's own colour, warmed. */
    public static void muzzle(Level level, double x, double y, double z, float size, float r, float g, float b) {
        add(level, null, x, y, z, SceneLightLook.muzzleRadius(size), Math.min(1, r * 1.05F), g * 0.8F, b * 0.55F,
                0.9F, 3, false);
    }

    /** A burning wreck, refreshed every tick while it burns; {@code fire} from 0 to 1. */
    public static void fire(Level level, Object key, Vec3 at, float fire) {
        fire(level, key, 0, at, fire, SceneLightLook.fireRadius(fire));
    }

    /** One stable light per burning region, rather than one light per flame particle. */
    public static void fire(Level level, Object key, int channel, Vec3 at, float fire, float radius) {
        add(level, new FireKey(key, channel), at.x, at.y + 0.6, at.z, radius, 1.0F, 0.58F, 0.25F,
                SceneLightLook.fireIntensity(fire), 8, true);
    }

    /** Small detached fires share two-block cells; larger nearby fire sources take precedence. */
    public static void flame(Level level, Vec3 at, float radius, float opacity) {
        if(!enabled() || opacity <= .1F || radius <= .15F)return;
        if(world!=null && world!=level)clear();
        for(Light light:LIGHTS)if(light.key instanceof FireKey
                && at.distanceToSqr(light.x,light.y,light.z)<light.radius*light.radius*.64F
                && light.strength(level.getGameTime(),0)>.1F)return;
        var key=new FlameCell((int)Math.floor(at.x/2),(int)Math.floor(at.y/2),(int)Math.floor(at.z/2));
        add(level,key,at.x,at.y+.35,at.z,Math.min(5,2+radius*3),1,.58F,.25F,
                .5F*Math.clamp(opacity,0,1)*Math.min(1,radius),24,true);
    }

    /** A burning decoy flare: a small white-hot light. */
    public static void flare(Level level, Object key, double x, double y, double z) {
        add(level, key, x, y, z, 7, 1.0F, 0.9F, 0.75F, 0.85F, 3, true);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (world != mc.level || !enabled()) {
            clear();
            return;
        }
        if (world == null || mc.isPaused()) {
            return;
        }
        long now = world.getGameTime();
        LIGHTS.removeIf(light -> {
            boolean gone = now < light.born || now - light.born >= light.life;
            if (gone && light.key != null) {
                KEYED.remove(light.key);
            }
            return gone;
        });
        samplePatch(now);
    }

    /**
     * Chooses this frame's lights for the vehicle shader: the {@link SceneLightLook#MAX_LIGHTS}
     * that matter most, camera-relative.
     */
    public static Frame frame(Vec3 camera) {
        var mc = Minecraft.getInstance();
        if (world == null || world != mc.level || LIGHTS.isEmpty() || !enabled()) {
            return Frame.EMPTY;
        }
        long now = world.getGameTime();
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        int max = SceneLightLook.MAX_LIGHTS;
        Light[] chosen = new Light[max];
        float[] scores = new float[max];
        float[] strengths = new float[max];
        int count = 0;
        for (Light light : LIGHTS) {
            float strength = light.strength(now, partial);
            float score = SceneLightLook.score(strength, light.radius,
                    camera.distanceToSqr(light.x, light.y, light.z));
            if (score <= 0) {
                continue;
            }
            int slot;
            if (count < max) {
                slot = count++;
            } else {
                slot = 0;
                for (int i = 1; i < max; i++) {
                    if (scores[i] < scores[slot]) {
                        slot = i;
                    }
                }
                if (scores[slot] >= score) {
                    continue;
                }
            }
            chosen[slot] = light;
            scores[slot] = score;
            strengths[slot] = strength;
        }
        float[] position = new float[count * 4];
        float[] color = new float[count * 3];
        for (int i = 0; i < count; i++) {
            Light light = chosen[i];
            position[i * 4] = (float) (light.x - camera.x);
            position[i * 4 + 1] = (float) (light.y - camera.y);
            position[i * 4 + 2] = (float) (light.z - camera.z);
            position[i * 4 + 3] = light.radius;
            color[i * 3] = light.r * strengths[i];
            color[i * 3 + 1] = light.g * strengths[i];
            color[i * 3 + 2] = light.b * strengths[i];
        }
        return new Frame(count, position, color);
    }

    /** Block light, 0 to 15, that the current lights give a point. */
    public static int blockLight(Vec3 at, float partial) {
        if (world == null || LIGHTS.isEmpty() || !enabled()) {
            return 0;
        }
        long now = world.getGameTime();
        float best = 0;
        for (Light light : LIGHTS) {
            double d = Math.sqrt(at.distanceToSqr(light.x, light.y, light.z));
            if (d >= light.radius) {
                continue;
            }
            float falloff = (float) (1 - d / light.radius);
            best = Math.max(best, light.strength(now, partial) * falloff * falloff * 1.4F);
        }
        return Math.round(15 * Math.min(1, best));
    }

    public static void registerModifiers(RegisterRenderStateModifiersEvent event) {
        event.registerEntityModifier(new TypeToken<LivingEntityRenderer<LivingEntity, LivingEntityRenderState, ?>>() { },
                (LivingEntity entity, LivingEntityRenderState state) -> {
                    if (world == null || LIGHTS.isEmpty() || entity.level() != world || !enabled()) {
                        return;
                    }
                    int lit = blockLight(entity.getPosition(state.partialTick).add(0, entity.getBbHeight() * 0.5, 0),
                            state.partialTick);
                    int block = LightCoordsUtil.block(state.lightCoords);
                    if (lit > block) {
                        state.lightCoords = LightCoordsUtil.pack(lit, LightCoordsUtil.sky(state.lightCoords));
                    }
                });
    }

    /** Refreshes the ground patch of one lasting fire or flare per tick, nearest first. */
    private static void samplePatch(long now) {
        var mc = Minecraft.getInstance();
        Vec3 eye = mc.gameRenderer.mainCamera().position();
        Light next = null;
        int ranked = 0;
        for (Light light : LIGHTS) {
            if (light.key == null || eye.distanceToSqr(light.x, light.y, light.z) > 96 * 96) {
                continue;
            }
            boolean moved = light.sampled == Long.MIN_VALUE
                    || Math.abs(light.x - light.patchX) + Math.abs(light.y - light.patchY) + Math.abs(light.z - light.patchZ) > 1
                    || Math.abs(light.radius-light.patchRadius)>.75F || now - light.sampled > 60;
            if (moved && (next == null || eye.distanceToSqr(light.x, light.y, light.z)
                    < eye.distanceToSqr(next.x, next.y, next.z))) {
                next = light;
            }
            ranked++;
        }
        if (next != null && ranked > 0) {
            sample(next, now);
        }
    }

    private static void sample(Light light, long now) {
        light.sampled = now;
        light.patchX = light.x;
        light.patchY = light.y;
        light.patchZ = light.z;
        light.patchRadius = light.radius;
        light.surfaces = List.of();
        BlockPos center = BlockPos.containing(light.x, light.y, light.z);
        if (!world.hasChunkAt(center)) {
            return;
        }
        light.patchSky = world.getBrightness(LightLayer.SKY, center) / 15F;
        float reach = Math.min(7, light.radius * 0.75F);
        int span = (int) Math.ceil(reach);
        var surfaces = new ArrayList<GlowGeometry.Surface>();
        var pos = new BlockPos.MutableBlockPos();
        for (int dx = -span; dx <= span; dx++) {
            for (int dz = -span; dz <= span; dz++) {
                if (dx * dx + dz * dz > (reach + 0.75F) * (reach + 0.75F)) {
                    continue;
                }
                pos.set(center.getX() + dx, center.getY() + 1, center.getZ() + dz);
                if (!world.hasChunkAt(pos)) {
                    continue;
                }
                for (int step = 0; step < Math.min(14, (int) Math.ceil(light.radius) + 2); step++, pos.move(0, -1, 0)) {
                    var state = world.getBlockState(pos);
                    if (state.isAir()) {
                        continue;
                    }
                    if (!state.getFluidState().isEmpty()) {
                        break;
                    }
                    var shape = state.getCollisionShape(world, pos);
                    if (shape.isEmpty()) {
                        continue;
                    }
                    if (shape.toAabbs().size() != 1) {
                        break;
                    }
                    var bounds = shape.bounds().move(pos);
                    if (bounds.maxY > light.y + 0.25) {
                        break;
                    }
                    float y = (float) (bounds.maxY + 0.014 - light.y);
                    float x0 = (float) (bounds.minX - light.x), x1 = (float) (bounds.maxX - light.x);
                    float z0 = (float) (bounds.minZ - light.z), z1 = (float) (bounds.maxZ - light.z);
                    float range = reach + 1;
                    float a = falloff(x0, y, z0, range), b = falloff(x0, y, z1, range);
                    float c = falloff(x1, y, z1, range), d = falloff(x1, y, z0, range);
                    if (a + b + c + d > 0) {
                        surfaces.add(new GlowGeometry.Surface(x0, x1, y, z0, z1, a, b, c, d));
                    }
                    break;
                }
            }
        }
        light.surfaces = List.copyOf(surfaces);
    }

    static float falloff(float x, float y, float z, float radius) {
        float t = Math.clamp(1 - (x * x + y * y + z * z) / (radius * radius), 0, 1);
        return t * t;
    }

    @SubscribeEvent
    public static void submit(SubmitCustomGeometryEvent event) {
        var mc = Minecraft.getInstance();
        if (world == null || world != mc.level || LIGHTS.isEmpty() || !enabled()) {
            return;
        }
        var pose = event.getPoseStack();
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        long now = world.getGameTime();
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float night = FlightLightLook.night(world.getSkyDarken());
        int patches = 0;
        // A distant, old fire must not take one of the four visible patches from a nearer one.
        var visible = LIGHTS.stream().filter(l -> l.key != null && !l.surfaces.isEmpty()
                && camera.distanceToSqr(l.x, l.y, l.z) <= 96 * 96)
                .sorted(java.util.Comparator.comparingDouble(l -> camera.distanceToSqr(l.x, l.y, l.z))).toList();
        for (Light light : visible) {
            if (patches >= PATCHES) {
                break;
            }
            // Daylight drowns a fire's glow; it shows at night and under cover.
            float dark = Math.max(night, 1 - light.patchSky);
            float alpha = light.strength(now, partial) * dark * 0.62F;
            if (alpha <= 0.01F) {
                continue;
            }
            int color = (int) (light.r * 255) << 16 | (int) (light.g * 255) << 8 | (int) (light.b * 255);
            pose.pushPose();
            pose.translate(light.patchX - camera.x, light.patchY - camera.y, light.patchZ - camera.z);
            GlowGeometry.surfaces(pose, event.getSubmitNodeCollector(), light.surfaces, Math.min(1, alpha),
                    light.surfaces.size(), color);
            pose.popPose();
            patches++;
        }
    }
}
