package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.config.ClientConfig;
import io.github.mcheliceport.content.aircraft.HeliInfo;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.util.McheliMath;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Enhanced helicopter wash only. Loaded terrain samples and all particle admissions are bounded. */
public final class RotorWashEmitter {
    private static WeakReference<ClientLevel> world = new WeakReference<>(null);
    private static final LinkedHashMap<Key, Sample> CACHE = new LinkedHashMap<>();
    private static final LinkedHashMap<Integer, Long> EMISSION_TICKS = new LinkedHashMap<>();
    private static long tick = Long.MIN_VALUE;
    private static int lookups, emitted;
    private static final int LOOKUP_LIMIT = 256;

    private RotorWashEmitter() {}
    public static void clear() { CACHE.clear(); EMISSION_TICKS.clear(); tick = Long.MIN_VALUE; lookups = emitted = 0; }
    public static int lookups() { return lookups; }
    public static int emitted() { return emitted; }
    public static int cacheSize() { return CACHE.size(); }

    public static void emit(HeliEntity heli) {
        HeliInfo info = heli.getHeliInfo();
        if (info == null || heli.getCurrentThrottle() <= 0.1 || info.isEnableFoldBlade
                && heli.getFoldBladeStat() != HeliEntity.BLADES_UNFOLDED) return;
        var motion = heli.getDeltaMovement();
        emit((ClientLevel) heli.level(), heli.getId(), liftOrigins(info, heli.position(), heli.getYaw(), heli.getPitch(), heli.getRoll()),
                heli.getCurrentThrottle(), info.particlesScale, motion.x, motion.z);
    }

    /** Main/coaxial/tandem positions come from content; vertical tail rotors do not raise ground dust. */
    public static List<Vec3> liftOrigins(HeliInfo info, Vec3 position, float yaw, float pitch, float roll) {
        List<Vec3> origins = new ArrayList<>(4);
        for (var rotor : info.rotorList) {
            if (rotor.rot.lengthSqr() < 0.01 || rotor.rot.y * rotor.rot.y < rotor.rot.lengthSqr() * 0.5) continue;
            Vec3 origin = position.add(McheliMath.rotVec3(rotor.pos, -yaw, -pitch, -roll));
            if (origins.stream().noneMatch(p -> Math.pow(p.x - origin.x, 2) + Math.pow(p.z - origin.z, 2) < 1)) origins.add(origin);
            if (origins.size() == 4) break;
        }
        if (origins.isEmpty()) origins.add(position.add(0, 2, 0));
        return origins;
    }

    private static void emit(ClientLevel level, int id, List<Vec3> origins, double throttle, float contentScale, double mx, double mz) {
        if (!io.github.mcheliceport.config.EffectQuality.enhanced()) return;
        if (world.get() != level) { clear(); world = new WeakReference<>(level); }
        long now = level.getGameTime();
        if (tick != now) { tick = now; lookups = emitted = 0; }
        float scale = RotorWashLook.scale(contentScale);
        if (scale == 0 || throttle <= 0.1 || origins.isEmpty() || !Double.isFinite(mx) || !Double.isFinite(mz)) return;
        var budget = ClientConfig.get(ClientConfig.EFFECT_BUDGET);
        int period = RotorWashLook.period(budget, EffectPerformance.pressure());
        if (Math.floorMod(now + id, period) != 0) return;
        Long previous = EMISSION_TICKS.get(id);
        if (previous != null && previous == now) return;
        // Clock synchronization can repeat a game time across client ticks; never duplicate a burst.
        if (EMISSION_TICKS.size() >= 128 && previous == null) EMISSION_TICKS.remove(EMISSION_TICKS.keySet().iterator().next());
        EMISSION_TICKS.put(id, now);
        int range = RotorWashLook.range(budget);
        Vec3 camera = Minecraft.getInstance().gameRenderer.mainCamera().position();
        Random random = new Random(now * 0x9E3779B97F4A7C15L ^ id * 0x632BE59BD9B4E019L);
        for (int i = 0; i < RotorWashLook.candidates(budget); i++) {
            int index = (int) Math.floorMod(now / period + i, Math.min(4, origins.size()));
            Vec3 origin = origins.get(index);
            if (!Double.isFinite(origin.lengthSqr()) || camera.distanceToSqr(origin) > range * range) continue;
            Sample centre = sample(level, new Key(id, index * 3), origin, scale, now);
            if (centre == null || centre.state == null) continue;
            double height = origin.y - centre.y;
            float strength = RotorWashLook.strength(height, scale, throttle, Math.hypot(mx, mz));
            RotorWashLook.Surface material = material(centre.state);
            if (random.nextFloat() > strength * material.density) continue;
            double angle = random.nextDouble() * Math.PI * 2;
            double dx = Math.cos(angle), dz = Math.sin(angle);
            double radius = RotorWashLook.radius(scale, height) * (0.25 + random.nextDouble() * 0.65);
            double px = origin.x + dx * radius - mx * 2, pz = origin.z + dz * radius - mz * 2;
            Sample surface = sample(level, new Key(id, index * 3 + 1 + i), new Vec3(px, origin.y, pz), scale, now);
            if (surface == null || surface.state == null) continue;
            material = material(surface.state);
            float localStrength = RotorWashLook.strength(origin.y - surface.y, scale, throttle, Math.hypot(mx, mz));
            if (localStrength <= 0) continue;
            float puffRadius = RotorWashLook.puffRadius(scale);
            if (!EffectAdmission.admit(level, px, surface.y + 0.35, pz, false, puffRadius)) continue;
            float[] tint = tint(surface.state, level, BlockPos.containing(px, surface.y - 0.01, pz), material);
            double speed = 0.08 + 0.18 * localStrength;
            double swirl = ((id & 1) == 0 ? 1 : -1) * 0.22;
            Minecraft.getInstance().particleEngine.add(new RotorWashParticle(level, px, surface.y + 0.35, pz,
                    (dx - dz * swirl) * speed - mx * 0.12, (dz + dx * swirl) * speed - mz * 0.12,
                    puffRadius, material.opacity * localStrength, tint[0], tint[1], tint[2],
                    24 + random.nextInt(11), swirl * 0.08));
            emitted++;
            if (ClientConfig.get(ClientConfig.ROTOR_WASH_DEBRIS)) {
                int count = RotorWashLook.debrisCount(material, localStrength, budget, EffectPerformance.pressure());
                for (int n = 0; n < count; n++) {
                    if (!EffectAdmission.admit(level, px, surface.y + 0.35, pz, false, 0.12F)) continue;
                    float r = tint[0], g = tint[1], b = tint[2];
                    if (material == RotorWashLook.Surface.GRASS) {
                        int color = net.minecraft.client.renderer.BiomeColors.getAverageGrassColor(level,
                                BlockPos.containing(px, surface.y - 0.01, pz));
                        if (color != -1) { r = (color >> 16 & 255) / 255F; g = (color >> 8 & 255) / 255F; b = (color & 255) / 255F; }
                    }
                    Minecraft.getInstance().particleEngine.add(new SurfaceDebrisParticle(level,
                            px, surface.y + 0.35, pz, dx * speed * 1.5, 0.1 + localStrength * 0.14,
                            dz * speed * 1.5, material == RotorWashLook.Surface.WATER ? 0.07F : 0.11F,
                            r, g, b, surface.y, 18 + random.nextInt(7), (float) (swirl * 2)));
                    emitted++;
                }
            }
            if (material == RotorWashLook.Surface.WATER && i == 0 && now % 8 == Math.floorMod(id, 8)
                    && budget != ClientConfig.EffectBudget.LOW && EffectPerformance.pressure() == 0
                    && EffectAdmission.admit(level, px, surface.y, pz, false, 2.5F)) {
                Minecraft.getInstance().particleEngine.add(new RotorRippleParticle(level, px, surface.y + 0.015, pz,
                        2.5F, 0.2F * localStrength, (float) angle));
                emitted++;
            }
        }
    }

    private static Sample sample(ClientLevel level, Key key, Vec3 origin, float scale, long now) {
        BlockPos top = BlockPos.containing(origin);
        Sample cached = CACHE.get(key);
        if (cached != null && cached.x == top.getX() && cached.z == top.getZ() && cached.top == top.getY()
                && now >= cached.tick && now - cached.tick < 4) return cached;
        BlockPos.MutableBlockPos pos = top.mutable();
        Sample found = null;
        for (int n = 0; n <= RotorWashLook.reach(scale) && pos.getY() >= level.getMinY(); n++, pos.move(0, -1, 0)) {
            if (lookups >= LOOKUP_LIMIT || !level.hasChunkAt(pos)) return null;
            lookups++;
            BlockState state = level.getBlockState(pos);
            if (state.is(BlockTags.LEAVES)) break;
            var fluid = state.getFluidState();
            if (!fluid.isEmpty()) {
                if (fluid.is(FluidTags.WATER)) found = new Sample(top.getX(), top.getZ(), top.getY(),
                        pos.getY() + fluid.getHeight(level, pos), state, now);
                break;
            }
            // Loose snow has a visible top even when it offers no collision support.
            if (state.is(Blocks.POWDER_SNOW)) {
                found = new Sample(top.getX(), top.getZ(), top.getY(), pos.getY() + 1, state, now);
                break;
            }
            var shape = state.is(Blocks.SNOW) ? state.getShape(level, pos) : state.getCollisionShape(level, pos);
            if (!shape.isEmpty()) {
                found = new Sample(top.getX(), top.getZ(), top.getY(), pos.getY() + shape.bounds().maxY, state, now);
                break;
            }
        }
        if (found == null) found = new Sample(top.getX(), top.getZ(), top.getY(), 0, null, now);
        if (CACHE.size() >= 128 && !CACHE.containsKey(key)) CACHE.remove(CACHE.keySet().iterator().next());
        CACHE.put(key, found);
        return found;
    }

    private static RotorWashLook.Surface material(BlockState state) {
        if (state.getFluidState().is(FluidTags.WATER)) return RotorWashLook.Surface.WATER;
        if (state.is(Blocks.SAND) || state.is(Blocks.RED_SAND)) return RotorWashLook.Surface.SAND;
        if (state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW)) return RotorWashLook.Surface.SNOW;
        if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.MYCELIUM) || state.is(Blocks.PODZOL)) return RotorWashLook.Surface.GRASS;
        if (state.is(BlockTags.DIRT) || state.is(Blocks.MUD) || state.is(Blocks.FARMLAND) || state.is(Blocks.DIRT_PATH)) return RotorWashLook.Surface.SOIL;
        return RotorWashLook.Surface.SOLID;
    }

    private static float[] tint(BlockState state, ClientLevel level, BlockPos pos, RotorWashLook.Surface material) {
        return switch (material) {
            case WATER -> new float[]{0.82F, 0.91F, 0.97F};
            case SNOW -> new float[]{0.93F, 0.96F, 1};
            case GRASS -> new float[]{0.6F, 0.56F, 0.42F};
            case SAND -> new float[]{0.86F, 0.73F, 0.5F};
            default -> ClientEffects.groundTint(level, pos, state, 0.66F, 0.62F, 0.54F);
        };
    }

    private record Key(int aircraft, int slot) {}
    private record Sample(int x, int z, int top, double y, BlockState state, long tick) {}
}
