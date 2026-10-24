package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.client.render.GlowGeometry;
import io.github.mcheliceport.config.ClientConfig;
import io.github.mcheliceport.config.EffectQuality;
import io.github.mcheliceport.content.weapon.WeaponInfo;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

/** Short terrain illumination and cannon dust, triggered once by the existing replicated shot bit. */
@EventBusSubscriber(modid = "mcheli_ce_port", value = Dist.CLIENT)
public final class WeaponGroundEffects {
    private record Flash(Vec3 origin, List<GlowGeometry.Surface> surfaces, long born) {}
    private static final List<Flash> FLASHES = new ArrayList<>();
    private static ClientLevel world;
    private static long tick = Long.MIN_VALUE;
    private static int rays;
    private WeaponGroundEffects() {}
    public static int flashes() { return FLASHES.size(); }
    public static int rays() { return rays; }
    public static void clear() { FLASHES.clear(); world = null; tick = Long.MIN_VALUE; rays = 0; }
    private static boolean enabled() { return EffectQuality.enhanced() && ClientConfig.get(ClientConfig.WEAPON_GROUND_EFFECTS); }

    public static boolean gun(String type) {
        return "machinegun1".equals(type) || "machinegun2".equals(type) || "buckshot".equals(type) || "railgun".equals(type);
    }

    public static void shot(Level level, Vec3 mount, WeaponInfo info, float yaw, float pitch) {
        if (!(level instanceof ClientLevel client) || Minecraft.getInstance().level != client || !enabled() || !gun(info.type)
                || !Float.isFinite(yaw) || !Float.isFinite(pitch)) return;
        if (world != client) { clear(); world = client; }
        long now = level.getGameTime();
        if (now != tick) { tick = now; rays = 0; }
        if (FLASHES.size() >= 12 || rays >= 24 || !EffectAdmission.inRange(client, mount.x, mount.y, mount.z, 4)) return;
        float distance = 0;
        if (info.listMuzzleFlash != null) for (var flash : info.listMuzzleFlash) {
            if (Float.isFinite(flash.dist)) distance = Math.max(distance, Math.clamp(flash.dist, 0, 8));
        }
        Vec3 muzzle = mount.add(Vec3.directionFromRotation(pitch, yaw).scale(distance));
        BlockPos start = BlockPos.containing(muzzle);
        if (!client.hasChunkAt(start) || !client.getFluidState(start).isEmpty()) return;
        float radius = Math.clamp(1.8F + Math.max(0, info.explosion) * 0.3F, 1.8F, 4);
        List<GlowGeometry.Surface> surfaces = new ArrayList<>(9);
        for (int x = -1; x <= 1 && rays < 24; x++) for (int z = -1; z <= 1 && rays < 24; z++) {
            double px = start.getX() + x + 0.5, pz = start.getZ() + z + 0.5;
            BlockPos column = BlockPos.containing(px, muzzle.y, pz);
            if (!client.hasChunkAt(column)) continue;
            rays++;
            var hit = client.clip(new ClipContext(new Vec3(px, muzzle.y, pz), new Vec3(px, muzzle.y - 4, pz),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, net.minecraft.world.phys.shapes.CollisionContext.empty()));
            if (hit.getType() == HitResult.Type.MISS || hit.getDirection() != net.minecraft.core.Direction.UP) continue;
            var block = hit.getBlockPos();
            var state = client.getBlockState(block);
            if (!state.getFluidState().isEmpty()) continue;
            var shape = state.getCollisionShape(client, block);
            // Do not project broad rectangular light across gaps beside fences or stair steps.
            if (shape.isEmpty() || shape.toAabbs().size() != 1) continue;
            var bounds = shape.bounds().move(block);
            if (rays >= 24) continue;
            rays++;
            var obstruction = client.clip(new ClipContext(muzzle, hit.getLocation().add(0, 0.04, 0),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, net.minecraft.world.phys.shapes.CollisionContext.empty()));
            if (obstruction.getType() != HitResult.Type.MISS) continue;
            float y = (float) (bounds.maxY + 0.012 - muzzle.y);
            float x0 = (float) (bounds.minX - muzzle.x), x1 = (float) (bounds.maxX - muzzle.x);
            float z0 = (float) (bounds.minZ - muzzle.z), z1 = (float) (bounds.maxZ - muzzle.z);
            float a = falloff(x0, y, z0, radius), b = falloff(x0, y, z1, radius);
            float c = falloff(x1, y, z1, radius), d = falloff(x1, y, z0, radius);
            if (a + b + c + d == 0) continue;
            surfaces.add(new GlowGeometry.Surface(x0, x1, y, z0, z1, a, b, c, d));
            if (x == 0 && z == 0 && info.explosion >= 3) dust(client, hit.getLocation(), state, info.explosion);
        }
        if (!surfaces.isEmpty()) FLASHES.add(new Flash(muzzle, List.copyOf(surfaces), now));
    }

    static float falloff(float x, float y, float z, float radius) {
        return Math.clamp(1 - (x * x + y * y + z * z) / (radius * radius), 0, 1);
    }

    private static void dust(ClientLevel level, Vec3 point, net.minecraft.world.level.block.state.BlockState ground, int power) {
        float[] color = ClientEffects.groundTint(level, BlockPos.containing(point.add(0, -0.1, 0)), ground, 0.7F, 0.65F, 0.55F);
        int count = Math.min(6, 2 + power) / (1 + EffectPerformance.pressure());
        if (ClientConfig.get(ClientConfig.EFFECT_BUDGET) == ClientConfig.EffectBudget.LOW) count = Math.min(2, count);
        for (int i = 0; i < count; i++) {
            double angle = i * Math.PI * 2 / count + level.getGameTime() * 0.13;
            if (!EffectAdmission.admit(level, point.x, point.y + 0.15, point.z, false, 0.75F)) continue;
            Minecraft.getInstance().particleEngine.add(new RotorWashParticle(level, point.x, point.y + 0.15, point.z,
                    Math.cos(angle) * 0.25, Math.sin(angle) * 0.25, 0.75F, 0.28F,
                    color[0], color[1], color[2], 18, 0));
        }
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var level = Minecraft.getInstance().level;
        if (level != world || !enabled()) { clear(); return; }
        if (level == null) return;
        long now = level.getGameTime();
        FLASHES.removeIf(f -> now < f.born || now - f.born > 3);
    }

    @SubscribeEvent public static void submit(SubmitCustomGeometryEvent event) {
        if (!enabled() || FLASHES.isEmpty() || world != Minecraft.getInstance().level) return;
        var pose = event.getPoseStack();
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        long now = world.getGameTime();
        float partial = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        for (Flash flash : List.copyOf(FLASHES)) {
            float alpha = Math.clamp(1 - (now - flash.born + partial) / 3F, 0, 1) * 0.45F;
            pose.pushPose();
            pose.translate(flash.origin.x - camera.x, flash.origin.y - camera.y, flash.origin.z - camera.z);
            GlowGeometry.surfaces(pose, event.getSubmitNodeCollector(), flash.surfaces, alpha, flash.surfaces.size());
            pose.popPose();
        }
    }
}
