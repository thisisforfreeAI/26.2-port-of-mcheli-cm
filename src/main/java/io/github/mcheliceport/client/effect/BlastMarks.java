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
    private static final ArrayDeque<Projection> ACTIVE = new ArrayDeque<>();
    private static final BlastMarkWork WORK = new BlastMarkWork();
    private static List<Tile> maintenance = List.of();
    private static int maintenanceCursor;
    private static long tickNanos;
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
        ACTIVE.clear();
        WORK.clear();
        maintenance = List.of(); maintenanceCursor = 0; tickNanos = 0;
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
        // Repeated hits at the same spot need one fresh fit, not a queue of obsolete copies.
        PENDING.removeIf(old -> old.style == style && old.size <= size && Math.abs(old.y - y) < 0.5
                && Math.pow(old.x - x, 2) + Math.pow(old.z - z, 2) < 0.25);
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
        long started = System.nanoTime();
        long now = level.getGameTime();
        EffectBudget budget = ClientConfig.get(ClientConfig.EFFECT_BUDGET);
        WORK.begin(now, budget);
        boolean paint = ClientConfig.get(ClientConfig.BLAST_MARKS);
        boolean dirty = MARKS.removeIf(mark -> now < mark.born || now - mark.born >= mark.lifetime
                || !paint && now - mark.flashBorn >= mark.flashDuration);
        for (Mark mark : MARKS) if (now - mark.flashBorn >= mark.flashDuration) mark.flash.clear();

        // Spread validation and light refresh over ticks rather than touching every tile together.
        for (int i = 0, count = maintenance.size(); i < count && WORK.check(); i++) {
            if (maintenanceCursor >= count) maintenanceCursor = 0;
            Tile tile = maintenance.get(maintenanceCursor++);
            if (!tile.valid || now - tile.checked < 8) continue;
            tile.checked = now;
            tile.valid = level.hasChunkAt(tile.pos) && level.getBlockState(tile.pos) == tile.state
                    && level.getBlockState(tile.pos.above()).getCollisionShape(level, tile.pos.above()).isEmpty();
            if (tile.valid) tile.light = LightCoordsUtil.getLightCoords(level, tile.pos.above());
        }
        for (Mark mark : MARKS) {
            boolean removed = mark.tiles.removeIf(tile -> !tile.valid);
            if (removed) mark.flash.clear();
            dirty |= removed;
        }
        dirty |= MARKS.removeIf(mark -> mark.tiles.isEmpty());
        ACTIVE.removeIf(job -> job.expired(now, paint) || job.published && !MARKS.contains(job.mark));
        PENDING.removeIf(p -> now < p.born || now - p.born > 40 || (!p.paint || !paint) && now - p.born >= duration(p));
        int range = BlastMarkTuning.range(budget);
        while (ACTIVE.size() < 2 && !PENDING.isEmpty() && PENDING.peekFirst().ready <= now) {
            Pending p = PENDING.removeFirst();
            if (EffectPerformance.pressure() >= 2
                    || mc.gameRenderer.mainCamera().position().distanceToSqr(p.x, p.y, p.z) > range * range) continue;
            ACTIVE.addLast(new Projection(level, p, budget, now, paint));
        }
        // Rotate unfinished jobs so one deep column cannot indefinitely starve the other impact.
        int jobs = ACTIVE.size();
        for (int i = 0; i < jobs; i++) {
            Projection job = ACTIVE.removeFirst();
            if (EffectPerformance.pressure() >= 2
                    || mc.gameRenderer.mainCamera().position().distanceToSqr(job.p.x, job.p.y, job.p.z) > range * range) continue;
            boolean changed = job.advance(level, now);
            if (changed && !job.published) {
                Mark mark = job.mark;
                MARKS.removeIf(old -> Math.abs(old.y - mark.y) < 1.5
                        && Math.pow(old.x - mark.x, 2) + Math.pow(old.z - mark.z, 2)
                        < Math.pow(Math.min(old.radius, mark.radius) * 0.5, 2));
                MARKS.addLast(mark); job.published = true;
            }
            dirty |= changed;
            if (!job.done) { ACTIVE.addLast(job); break; }
        }
        int quads = 0, charged = 0;
        for (Mark mark : MARKS) { quads += mark.tiles.size(); charged += storageCharge(mark); }
        while (!MARKS.isEmpty() && (charged > BlastMarkTuning.quadLimit(budget) || MARKS.size() > BlastMarkTuning.markLimit(budget))) {
            Mark removed = MARKS.removeFirst();
            quads -= removed.tiles.size(); charged -= storageCharge(removed);
            dirty = true;
        }
        ACTIVE.removeIf(job -> job.published && !MARKS.contains(job.mark));
        if (dirty) {
            snapshot = List.copyOf(MARKS);
            var tiles = new ArrayList<Tile>(quads);
            textureMask = 0;
            for (Mark mark : snapshot) { tiles.addAll(mark.tiles); if (mark.paint) textureMask |= 1 << mark.texture; }
            maintenance = List.copyOf(tiles);
        }
        tickNanos = System.nanoTime() - started;
    }

    private static int storageCharge(Mark mark) {
        // Partial fits reserve their flat footprint so staging cannot sneak extra patches into
        // the draw cache. Completed/abandoned fits pay only for their actual surviving faces.
        for (Projection job : ACTIVE) if (job.mark == mark) return Math.max(mark.tiles.size(), job.flatFaces);
        return mark.tiles.size();
    }

    private static int duration(Pending p) { return p.style == BlastStyle.HEAVY || p.style == BlastStyle.INCENDIARY ? 9 : 6; }

    private static final class Projection {
        final Pending p;
        final Mark mark;
        final BlastMarkColumns columns;
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        final int top, bottom, turn, flatFaces;
        final Vec3 origin;
        boolean inColumn, published, done;
        BlockState state;
        List<AABB> boxes = List.of();
        int box;
        long validated;

        Projection(ClientLevel level, Pending p, EffectBudget budget, long now, boolean paint) {
            this.p = p;
            float radius = BlastMarkTuning.radius(p.size, p.style, budget);
            boolean drawPaint = p.paint && paint;
            if (!drawPaint) radius = Math.min(radius, 4);
            int texture = switch (p.style) { case CANNON, PENETRATOR -> 0; case INCENDIARY -> 2; default -> 1; };
            mark = new Mark(p.x, p.y, p.z, radius, texture, now, drawPaint ? BlastMarkTuning.lifetime(budget) : duration(p),
                    new ArrayList<>(), new ArrayList<>(), p.born, duration(p), drawPaint, p.flash);
            columns = new BlastMarkColumns(p.x, p.z, radius);
            flatFaces = Math.min(PER_MARK_QUADS, ((int) Math.ceil(p.x + radius) - Mth.floor(p.x - radius))
                    * ((int) Math.ceil(p.z + radius) - Mth.floor(p.z - radius)));
            top = Mth.floor(p.y + Math.min(2, p.size));
            bottom = Math.max(level.getMinY(), Mth.floor(p.y - Math.min(16, p.size * 1.5)) - 1);
            turn = (int) (Double.doubleToLongBits(p.x) ^ Double.doubleToLongBits(p.z) ^ now) & 3;
            origin = new Vec3(p.x, p.y + 0.08, p.z);
        }

        boolean expired(long now, boolean paint) {
            return now < p.born || now - p.born > 40 || (!mark.paint || !paint) && now - p.born >= mark.flashDuration;
        }

        boolean advance(ClientLevel level, long now) {
            boolean changed = false;
            while (!done) {
                if (mark.tiles.size() >= PER_MARK_QUADS) { done = true; break; }
                if (box < boxes.size()) {
                    // A shape held across ticks must still belong to the same loaded block.
                    if (validated != now) {
                        if (!WORK.read()) break;
                        if (!level.hasChunkAt(pos) || level.getBlockState(pos) != state) {
                            boxes = List.of(); inColumn = false; continue;
                        }
                        validated = now;
                    }
                    AABB shape = boxes.get(box);
                    double x0 = Math.max(pos.getX() + shape.minX, p.x - mark.radius), x1 = Math.min(pos.getX() + shape.maxX, p.x + mark.radius);
                    double z0 = Math.max(pos.getZ() + shape.minZ, p.z - mark.radius), z1 = Math.min(pos.getZ() + shape.maxZ, p.z + mark.radius);
                    double surfaceY = pos.getY() + shape.maxY;
                    Vec3 end = new Vec3((x0 + x1) / 2, surfaceY + 0.015, (z0 + z1) / 2);
                    boolean candidate = shape.getXsize() >= 0.45 && shape.getZsize() >= 0.45 && x1 - x0 >= 0.001 && z1 - z0 >= 0.001
                            && BlastMarkTuning.reached(p.size, end.x - p.x, end.y - p.y, end.z - p.z);
                    if (candidate) {
                        if (!WORK.ray()) break;
                        var hit = level.clip(new ClipContext(origin, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
                        if (hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(end) <= 0.01) {
                            Tile tile = new Tile(pos.immutable(), state, x0, x1, surfaceY + 0.003, z0, z1,
                                    uv(x0, z0, p, mark.radius, turn), uv(x0, z1, p, mark.radius, turn),
                                    uv(x1, z1, p, mark.radius, turn), uv(x1, z0, p, mark.radius, turn),
                                    tint(state), LightCoordsUtil.getLightCoords(level, pos.above()), Double.hashCode(p.x)^Double.hashCode(p.z)*31^(int)p.born);
                            mark.tiles.add(tile); changed = true;
                            if (p.flash && now - p.born < mark.flashDuration && mark.flash.size() < 64) flash(tile);
                        }
                    }
                    if (++box >= boxes.size()) { boxes = List.of(); inColumn = false; }
                    continue;
                }
                if (!inColumn) {
                    if (!WORK.column()) break;
                    if (!columns.next()) { done = true; break; }
                    pos.set(columns.x(), top, columns.z()); inColumn = true;
                }
                if (pos.getY() < bottom || !level.hasChunkAt(pos)) { inColumn = false; continue; }
                if (!WORK.read()) break;
                state = level.getBlockState(pos);
                if (!state.getFluidState().isEmpty() || state.is(BlockTags.LEAVES)) { inColumn = false; continue; }
                if (state.isAir()) { pos.move(0, -1, 0); continue; }
                List<AABB> found = state.getShape(level, pos).toAabbs();
                if (found.isEmpty()) { pos.move(0, -1, 0); continue; }
                double highest = found.stream().mapToDouble(shape -> shape.maxY).max().orElse(1) + pos.getY();
                if (highest > p.y + 0.25) { pos.move(0, -1, 0); continue; }
                if (found.size() > 4) { inColumn = false; continue; }
                boxes = found; box = 0; validated = now;
            }
            return changed;
        }

        private void flash(Tile tile) {
            float r = Math.min(5, mark.radius), dy = (float) (tile.y - p.y);
            float a = BlastMarkTuning.flashFalloff(tile.x0 - p.x, dy, tile.z0 - p.z, r);
            float b = BlastMarkTuning.flashFalloff(tile.x0 - p.x, dy, tile.z1 - p.z, r);
            float c = BlastMarkTuning.flashFalloff(tile.x1 - p.x, dy, tile.z1 - p.z, r);
            float d = BlastMarkTuning.flashFalloff(tile.x1 - p.x, dy, tile.z0 - p.z, r);
            if (a + b + c + d > 0) mark.flash.add(new GlowGeometry.Surface((float) (tile.x0 - p.x), (float) (tile.x1 - p.x), dy + 0.002F,
                    (float) (tile.z0 - p.z), (float) (tile.z1 - p.z), a, b, c, d));
        }
    }

    public static int projectionReads() { return WORK.reads; }
    public static int projectionRays() { return WORK.rays; }
    public static int maintenanceChecks() { return WORK.checks; }
    public static long tickNanos() { return tickNanos; }
    public static int pendingCount() { return PENDING.size(); }
    public static int activeCount() { return ACTIVE.size(); }

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
        final int[] colors = new int[4];
        final float[] coverage = new float[4];
        int light;
        boolean valid = true;
        long checked = Long.MIN_VALUE / 2;

        Tile(BlockPos pos, BlockState state, double x0, double x1, double y, double z0, double z1,
                float[] a, float[] b, float[] c, float[] d, int rgb, int light, int seed) {
            this.pos = pos; this.state = state;
            this.x0 = x0; this.x1 = x1; this.y = y; this.z0 = z0; this.z1 = z1;
            this.uv = new float[][]{a, b, c, d}; this.light = light;
            for(int i=0;i<4;i++) {
                coverage[i]=BlastMarkTuning.scarCoverage(uv[i][0],uv[i][1],seed);
                colors[i]=BlastMarkTuning.scarColor(rgb,uv[i][0],uv[i][1]);
            }
        }

        void emit(VertexConsumer buffer, PoseStack.Pose matrix, BlockPos origin, int alpha) {
            vertex(buffer, matrix, origin, this.x0, this.z0, 0, alpha);
            vertex(buffer, matrix, origin, this.x0, this.z1, 1, alpha);
            vertex(buffer, matrix, origin, this.x1, this.z1, 2, alpha);
            vertex(buffer, matrix, origin, this.x1, this.z0, 3, alpha);
        }

        private void vertex(VertexConsumer buffer, PoseStack.Pose matrix, BlockPos origin, double x, double z, int corner, int alpha) {
            int argb=Math.round(alpha*coverage[corner])<<24 | colors[corner];
            buffer.addVertex(matrix, (float) (x - origin.getX()), (float) (this.y - origin.getY()), (float) (z - origin.getZ()))
                    .setColor(argb).setUv(this.uv[corner][0], this.uv[corner][1]).setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(this.light).setNormal(matrix, 0, 1, 0);
        }
    }
}
