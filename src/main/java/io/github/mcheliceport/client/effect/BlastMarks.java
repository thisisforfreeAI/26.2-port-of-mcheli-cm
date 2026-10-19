package io.github.mcheliceport.client.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.mcheliceport.client.particle.EffectPerformance;
import io.github.mcheliceport.config.ClientConfig;
import io.github.mcheliceport.config.ClientConfig.EffectBudget;
import io.github.mcheliceport.config.EffectQuality;
import io.github.mcheliceport.effect.BlastStyle;
import io.github.mcheliceport.client.render.GlowGeometry;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

/** Temporary client-only surface textures. No block writes, entities, saved data or per-frame world queries. */
@EventBusSubscriber(modid = "mcheli_ce_port", value = Dist.CLIENT)
public final class BlastMarks {
    private static final int PER_MARK_QUADS = 256;
    private static final RenderType[] TYPES = {type("impact"), type("blast"), type("burn")};
    private static WeakReference<ClientLevel> world = new WeakReference<>(null);
    private static final ArrayDeque<Pending> PENDING = new ArrayDeque<>();
    private static final ArrayDeque<Mark> MARKS = new ArrayDeque<>();
    private static List<Mark> snapshot = List.of();
    private static int textureMask;
    private static int flashQuads;

    private BlastMarks() {}

    private static RenderType type(String name) {
        return RenderTypes.entityTranslucent(Identifier.fromNamespaceAndPath("mcheli_ce_port",
                "textures/effect/m8_" + name + "_mark.png"), false);
    }

    private static boolean enabled() {
        return EffectQuality.enhanced() && (ClientConfig.get(ClientConfig.BLAST_MARKS) || EffectPerformance.optionalGlows());
    }

    private static void world(ClientLevel level) {
        if (world.get() != level) {
            clear();
            world = new WeakReference<>(level);
        }
    }

    public static void clear() {
        PENDING.clear();
        MARKS.clear();
        snapshot = List.of();
        textureMask = 0;
        flashQuads = 0;
    }

    public static void add(ClientLevel level, double x, double y, double z, float size, BlastStyle style) {
        world(level);
        EffectBudget budget = ClientConfig.get(ClientConfig.EFFECT_BUDGET);
        if (!enabled() || EffectPerformance.pressure() >= 2 || !Float.isFinite(size) || size <= 0
                || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return;
        int range = BlastMarkTuning.range(budget);
        if (Minecraft.getInstance().gameRenderer.mainCamera().position().distanceToSqr(x, y, z) > range * range) return;
        // Allow server block updates to arrive before fitting the surviving crater surfaces.
        if (PENDING.size() >= 16) PENDING.removeFirst();
        PENDING.addLast(new Pending(x, y, z, size, style, level.getGameTime() + 2,
                level.getGameTime(), ClientConfig.get(ClientConfig.BLAST_MARKS), EffectPerformance.optionalGlows()));
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        world(level);
        if (level == null || !enabled()) { clear(); return; }
        if (mc.isPaused()) return;
        long now = level.getGameTime();
        EffectBudget budget = ClientConfig.get(ClientConfig.EFFECT_BUDGET);
        boolean dirty = MARKS.removeIf(mark -> now < mark.born || now - mark.born >= mark.lifetime);
        for (Mark mark : MARKS) if (now - mark.flashBorn >= mark.flashDuration) mark.flash.clear();
        if (now % 10 == 0) {
            for (Mark mark : MARKS) {
                boolean removed = mark.tiles.removeIf(tile -> !level.hasChunkAt(tile.pos)
                        || level.getBlockState(tile.pos) != tile.state
                        || !level.getBlockState(tile.pos.above()).getCollisionShape(level, tile.pos.above()).isEmpty());
                if (removed) mark.flash.clear();
                dirty |= removed;
                for (Tile tile : mark.tiles) tile.light = LightCoordsUtil.getLightCoords(level, tile.pos.above());
            }
            dirty |= MARKS.removeIf(mark -> mark.tiles.isEmpty());
        }
        for (int i = 0; i < 2 && !PENDING.isEmpty() && PENDING.peekFirst().ready <= now; i++) {
            Pending p = PENDING.removeFirst();
            int range = BlastMarkTuning.range(budget);
            if (EffectPerformance.pressure() >= 2
                    || mc.gameRenderer.mainCamera().position().distanceToSqr(p.x, p.y, p.z) > range * range) continue;
            Mark mark = project(level, p, budget, now);
            if (mark.tiles.isEmpty()) continue;
            MARKS.removeIf(old -> Math.abs(old.y - mark.y) < 1.5
                    && Math.pow(old.x - mark.x, 2) + Math.pow(old.z - mark.z, 2)
                    < Math.pow(Math.min(old.radius, mark.radius) * 0.5, 2));
            MARKS.addLast(mark);
            dirty = true;
        }
        int quads = MARKS.stream().mapToInt(mark -> mark.tiles.size()).sum();
        while (!MARKS.isEmpty() && (quads > BlastMarkTuning.quadLimit(budget) || MARKS.size() > BlastMarkTuning.markLimit(budget))) {
            quads -= MARKS.removeFirst().tiles.size();
            dirty = true;
        }
        if (dirty) {
            snapshot = List.copyOf(MARKS);
            textureMask = 0;
            for (Mark mark : snapshot) if (mark.paint) textureMask |= 1 << mark.texture;
        }
    }

    private static Mark project(ClientLevel level, Pending p, EffectBudget budget, long now) {
        float radius = BlastMarkTuning.radius(p.size, p.style, budget);
        int texture = switch (p.style) { case CANNON, PENETRATOR -> 0; case INCENDIARY -> 2; default -> 1; };
        if (!p.paint) radius = Math.min(radius, 4);
        int duration = p.style == BlastStyle.HEAVY || p.style == BlastStyle.INCENDIARY ? 9 : 6;
        Mark mark = new Mark(p.x, p.y, p.z, radius, texture, now,
                p.paint ? BlastMarkTuning.lifetime(budget) : duration, new ArrayList<>(), new ArrayList<>(),
                p.born, duration, p.paint, p.flash);
        int turn = (int) (Double.doubleToLongBits(p.x) ^ Double.doubleToLongBits(p.z) ^ now) & 3;
        int top = Mth.floor(p.y + Math.min(2, p.size));
        int bottom = Math.max(level.getMinY(), Mth.floor(p.y - Math.min(16, p.size * 1.5)) - 1);
        Vec3 origin = new Vec3(p.x, p.y + 0.08, p.z);
        for (int x = Mth.floor(p.x - radius); x <= Mth.floor(p.x + radius); x++) {
            for (int z = Mth.floor(p.z - radius); z <= Mth.floor(p.z + radius); z++) {
                for (int y = top; y >= bottom; y--) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!level.hasChunkAt(pos)) break;
                    BlockState state = level.getBlockState(pos);
                    if (!state.getFluidState().isEmpty()) break;
                    if (state.isAir()) continue;
                    if (state.is(BlockTags.LEAVES)) break;
                    List<AABB> boxes = state.getShape(level, pos).toAabbs();
                    if (boxes.isEmpty()) continue;
                    double highest = boxes.stream().mapToDouble(box -> box.maxY).max().orElse(1) + y;
                    if (highest > p.y + 0.25) continue;
                    if (boxes.size() > 4) break;
                    for (AABB box : boxes) {
                        if (mark.tiles.size() >= PER_MARK_QUADS) break;
                        if (box.getXsize() < 0.45 || box.getZsize() < 0.45) continue;
                        double x0 = Math.max(x + box.minX, p.x - radius), x1 = Math.min(x + box.maxX, p.x + radius);
                        double z0 = Math.max(z + box.minZ, p.z - radius), z1 = Math.min(z + box.maxZ, p.z + radius);
                        if (x1 - x0 < 0.001 || z1 - z0 < 0.001) continue;
                        double surfaceY = y + box.maxY;
                        Vec3 end = new Vec3((x0 + x1) / 2, surfaceY + 0.015, (z0 + z1) / 2);
                        if (!BlastMarkTuning.reached(p.size, end.x - p.x, end.y - p.y, end.z - p.z)) continue;
                        var hit = level.clip(new ClipContext(origin, end, ClipContext.Block.COLLIDER,
                                ClipContext.Fluid.NONE, CollisionContext.empty()));
                        if (hit.getType() != HitResult.Type.MISS && hit.getLocation().distanceToSqr(end) > 0.01) continue;
                        int rgb = tint(state);
                        mark.tiles.add(new Tile(pos, state, x0, x1, surfaceY + 0.003, z0, z1,
                                uv(x0, z0, p, radius, turn), uv(x0, z1, p, radius, turn),
                                uv(x1, z1, p, radius, turn), uv(x1, z0, p, radius, turn),
                                rgb, LightCoordsUtil.getLightCoords(level, pos.above())));
                    }
                    break;
                }
            }
        }
        // Center-first order lets the global flash face cap retain a complete compact light patch.
        mark.tiles.sort(java.util.Comparator.comparingDouble(tile ->
                Math.pow((tile.x0 + tile.x1) * 0.5 - p.x, 2) + Math.pow((tile.z0 + tile.z1) * 0.5 - p.z, 2)));
        if (p.flash) for (Tile tile : mark.tiles) {
            float r = Math.min(5, radius);
            float dy = (float) (tile.y - p.y);
            float a = BlastMarkTuning.flashFalloff(tile.x0 - p.x, dy, tile.z0 - p.z, r);
            float b = BlastMarkTuning.flashFalloff(tile.x0 - p.x, dy, tile.z1 - p.z, r);
            float c = BlastMarkTuning.flashFalloff(tile.x1 - p.x, dy, tile.z1 - p.z, r);
            float d = BlastMarkTuning.flashFalloff(tile.x1 - p.x, dy, tile.z0 - p.z, r);
            if (a + b + c + d <= 0) continue;
            mark.flash.add(new GlowGeometry.Surface((float) (tile.x0 - p.x), (float) (tile.x1 - p.x), dy + 0.002F,
                    (float) (tile.z0 - p.z), (float) (tile.z1 - p.z), a, b, c, d));
            if (mark.flash.size() >= 64) break;
        }
        return mark;
    }

    private static float[] uv(double x, double z, Pending p, float radius, int turn) {
        return BlastMarkTuning.uv(x - p.x, z - p.z, radius, turn);
    }

    private static int tint(BlockState state) {
        if (state.is(Blocks.SAND) || state.is(Blocks.RED_SAND)) return 0xD7C298;
        if (state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.ICE)
                || state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE)) return 0xCBC8BD;
        if (state.is(BlockTags.DIRT) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.PODZOL)
                || state.is(Blocks.MYCELIUM) || state.is(Blocks.DIRT_PATH) || state.is(Blocks.FARMLAND)
                || state.is(Blocks.MUD) || state.is(BlockTags.LOGS) || state.is(BlockTags.PLANKS)) return 0xD3A577;
        return 0xB8B8B8;
    }

    @SubscribeEvent
    public static void submit(SubmitCustomGeometryEvent event) {
        flashQuads = 0;
        if (snapshot.isEmpty() || !enabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || world.get() != mc.level) return;
        Vec3 cam = event.getLevelRenderState().cameraRenderState.pos;
        BlockPos origin = BlockPos.containing(cam);
        long now = mc.level.getGameTime();
        int range = BlastMarkTuning.range(ClientConfig.get(ClientConfig.EFFECT_BUDGET));
        List<Mark> captured = snapshot;
        if (EffectPerformance.optionalGlows()) {
            int remaining = ClientConfig.get(ClientConfig.EFFECT_BUDGET) == EffectBudget.HIGH ? 128 : 64;
            int patches = 0;
            float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
            for (Mark mark : captured.reversed()) {
                float alpha = BlastMarkTuning.flashAlpha(now - mark.flashBorn + partial, mark.flashDuration);
                if (!mark.glow || alpha <= 0 || mark.flash.isEmpty()
                        || cam.distanceToSqr(mark.x, mark.y, mark.z) > range * range) continue;
                int count = Math.min(remaining, mark.flash.size());
                if (count == 0 || patches >= 4) break;
                var flashPose = event.getPoseStack();
                flashPose.pushPose();
                flashPose.translate(mark.x - cam.x, mark.y - cam.y, mark.z - cam.z);
                GlowGeometry.surfaces(flashPose, event.getSubmitNodeCollector(), mark.flash, alpha, count);
                flashPose.popPose();
                flashQuads += count; remaining -= count; patches++;
            }
        }
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(origin.getX() - cam.x, origin.getY() - cam.y, origin.getZ() - cam.z);
        for (int t = 0; t < TYPES.length; t++) {
            if ((textureMask & (1 << t)) == 0) continue;
            int texture = t;
            event.getSubmitNodeCollector().submitCustomGeometry(pose, TYPES[t], (matrix, buffer) -> {
                for (Mark mark : captured) {
                    if (!mark.paint || !ClientConfig.get(ClientConfig.BLAST_MARKS) || mark.texture != texture || cam.distanceToSqr(mark.x, mark.y, mark.z) > range * range) continue;
                    int alpha = Math.round(255 * BlastMarkTuning.alpha(now - mark.born, mark.lifetime)
                            * Math.clamp((now - mark.flashBorn) / 6.0F, 0, 1));
                    for (Tile tile : mark.tiles) tile.emit(buffer, matrix, origin, alpha);
                }
            });
        }
        pose.popPose();
    }

    /** Development/native-probe counters, also useful in a profiler watch. */
    public static int markCount() { return snapshot.size(); }
    public static int quadCount() { return snapshot.stream().mapToInt(mark -> mark.tiles.size()).sum(); }

    public static int flashQuadCount() { return flashQuads; }

    private record Pending(double x, double y, double z, float size, BlastStyle style, long ready,
            long born, boolean paint, boolean flash) {}
    private record Mark(double x, double y, double z, float radius, int texture, long born, int lifetime,
            List<Tile> tiles, List<GlowGeometry.Surface> flash, long flashBorn, int flashDuration, boolean paint, boolean glow) {}

    private static final class Tile {
        final BlockPos pos;
        final BlockState state;
        final double x0, x1, y, z0, z1;
        final float[][] uv;
        final int rgb;
        int light;

        Tile(BlockPos pos, BlockState state, double x0, double x1, double y, double z0, double z1,
                float[] a, float[] b, float[] c, float[] d, int rgb, int light) {
            this.pos = pos; this.state = state;
            this.x0 = x0; this.x1 = x1; this.y = y; this.z0 = z0; this.z1 = z1;
            this.uv = new float[][]{a, b, c, d}; this.rgb = rgb; this.light = light;
        }

        void emit(VertexConsumer buffer, PoseStack.Pose matrix, BlockPos origin, int alpha) {
            int argb = alpha << 24 | this.rgb;
            vertex(buffer, matrix, origin, this.x0, this.z0, 0, argb);
            vertex(buffer, matrix, origin, this.x0, this.z1, 1, argb);
            vertex(buffer, matrix, origin, this.x1, this.z1, 2, argb);
            vertex(buffer, matrix, origin, this.x1, this.z0, 3, argb);
        }

        private void vertex(VertexConsumer buffer, PoseStack.Pose matrix, BlockPos origin, double x, double z, int corner, int argb) {
            buffer.addVertex(matrix, (float) (x - origin.getX()), (float) (this.y - origin.getY()), (float) (z - origin.getZ()))
                    .setColor(argb).setUv(this.uv[corner][0], this.uv[corner][1]).setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(this.light).setNormal(matrix, 0, 1, 0);
        }
    }
}
