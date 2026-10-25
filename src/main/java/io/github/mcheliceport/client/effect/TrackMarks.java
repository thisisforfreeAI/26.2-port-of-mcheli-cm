package io.github.mcheliceport.client.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.mcheliceport.client.particle.EffectPerformance;
import io.github.mcheliceport.config.ClientConfig;
import io.github.mcheliceport.config.EffectQuality;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

/** Client-only clipped ground imprints. Terrain/light sampling happens only on ticks. */
@EventBusSubscriber(modid = "mcheli_ce_port", value = Dist.CLIENT)
public final class TrackMarks {
    public enum Kind { TREAD, TIRE }
    private static final RenderType TYPE = RenderTypes.entityTranslucent(
            Identifier.withDefaultNamespace("textures/block/white_concrete.png"), false);
    private static WeakReference<ClientLevel> world = new WeakReference<>(null);
    private static final ArrayDeque<Request> pending = new ArrayDeque<>();
    private static final TrackCache<Tile> cache = new TrackCache<>();
    private static List<Tile> snapshot = List.of();
    private static final Set<BlockPos> invalidSurfaces = new HashSet<>();
    private static int cursor, reads, drawn, fitted;
    private static long workTick = Long.MIN_VALUE;

    private TrackMarks() {}
    private static boolean enabled() { return EffectQuality.enhanced() && ClientConfig.get(ClientConfig.VEHICLE_TRACKS); }
    public static void clear() {
        pending.clear(); cache.clear(); snapshot = List.of(); invalidSurfaces.clear(); world.clear();
        cursor = reads = drawn = fitted = 0; workTick = Long.MIN_VALUE;
    }

    public static void add(ClientLevel level, Vec3 contact, Vec3 direction, float width, Kind kind, float strength) {
        if (!enabled() || Minecraft.getInstance().level != level || !Double.isFinite(contact.x + contact.y + contact.z)
                || Math.abs(contact.x) > 30_000_000 || Math.abs(contact.z) > 30_000_000
                || contact.y < level.getMinY() - 2 || contact.y > level.getMaxY() + 2 || kind == null
                || !Float.isFinite(width) || !Float.isFinite(strength) || strength <= 0) return;
        if (world.get() != level) { clear(); world = new WeakReference<>(level); }
        int range = TrackTuning.range(ClientConfig.get(ClientConfig.EFFECT_BUDGET));
        if (Minecraft.getInstance().gameRenderer.mainCamera().position().distanceToSqr(contact) > range * range) return;
        List<List<TrackClip.Point>> patterns = TrackLook.pattern(contact.x, contact.z, direction.x, direction.z, width, kind);
        if (patterns.isEmpty()) return;
        if (pending.size() == 16) pending.removeFirst();
        pending.addLast(new Request(contact, patterns, kind, Math.min(1, strength), level.getGameTime()));
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance(); ClientLevel level = mc.level;
        if (level == null || !enabled()) { clear(); return; }
        if (world.get() != level) { clear(); world = new WeakReference<>(level); }
        if (mc.isPaused()) return;
        long now = level.getGameTime();
        if (workTick != now) { workTick = now; reads = fitted = 0; }
        var quality = ClientConfig.get(ClientConfig.EFFECT_BUDGET);
        int allowance = Math.max(16, TrackTuning.reads(quality) / (1 + EffectPerformance.pressure()));
        invalidSurfaces.clear();
        for (int i = 0; i < Math.min(TrackTuning.checks(quality), allowance / 4) && !snapshot.isEmpty() && reads + 2 <= allowance; i++) {
            if (cursor >= snapshot.size()) cursor = 0;
            Tile tile = snapshot.get(cursor++);
            if (!tile.valid || now - tile.checked < 8) continue;
            tile.checked = now;
            if (!level.hasChunkAt(tile.pos)) { tile.valid = false; invalidSurfaces.add(tile.pos); continue; }
            reads += 2;
            tile.valid = level.getBlockState(tile.pos) == tile.state && level.getBlockState(tile.pos.above()) == tile.above;
            if (tile.valid) tile.light = LightCoordsUtil.getLightCoords(level, tile.pos.above());
            else invalidSurfaces.add(tile.pos);
        }
        boolean dirty = cache.trim(now, TrackTuning.lifetime(quality), TrackTuning.quads(quality), t -> t.valid && !invalidSurfaces.contains(t.pos));
        pending.removeIf(p -> now < p.born || now - p.born > 8);
        int fitLimit = Math.max(8, TrackTuning.fits(quality) / (1 + EffectPerformance.pressure()));
        // At most four shape boxes times two imprint pieces. Reserve that much headroom so
        // a column is never half-fitted or replayed after hitting its light-sampling allowance.
        while (!pending.isEmpty() && reads + 4 <= allowance && fitted + 8 <= fitLimit) {
            Request request = pending.getFirst();
            dirty |= project(level, request, TrackTuning.quads(quality));
            if (++request.x > request.maxX) { request.x = request.minX; request.z++; }
            if (request.z > request.maxZ) pending.removeFirst();
        }
        if (dirty) snapshot = cache.snapshot();
    }

    private static boolean project(ClientLevel level, Request r, int limit) {
        int top = Mth.floor(r.contact.y + 0.5);
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos(r.x, top, r.z);
        if (!level.hasChunkAt(at)) return false;
        for (int depth = 0; depth < 3 && at.getY() >= level.getMinY(); depth++, at.move(0, -1, 0)) {
            reads++; BlockState state = level.getBlockState(at);
            if (!state.getFluidState().isEmpty()) return false;
            if (state.is(net.minecraft.tags.BlockTags.LEAVES)) return false;
            // Thin snow uses its visible top; powder snow has no collision support and is omitted.
            var shape = state.is(net.minecraft.world.level.block.Blocks.SNOW) ? state.getShape(level, at)
                    : state.getCollisionShape(level, at);
            if (shape.isEmpty()) continue;
            var boxes = shape.toAabbs();
            if (boxes.size() > 4) return false;
            reads++; BlockState above = level.getBlockState(at.above());
            boolean changed = false;
            TrackLook.Surface material = TrackLook.surface(state);
            for (var box : boxes) for (var polygon : r.patterns) {
                double y = at.getY() + box.maxY;
                if (Math.abs(y - r.contact.y) > 0.65) continue;
                List<TrackClip.Point> clipped = TrackClip.clip(polygon, r.x + box.minX, r.z + box.minZ,
                        r.x + box.maxX, r.z + box.maxZ);
                if (clipped.isEmpty()) continue;
                fitted++;
                Tile tile = new Tile(at.immutable(), state, above, clipped, y + 0.003, r.born, TrackLook.color(material), TrackLook.opacity(material, r.kind, r.strength),
                        LightCoordsUtil.getLightCoords(level, at.above()));
                changed |= cache.add(r.born, tile.cost, tile, limit);
            }
            return changed;
        }
        return false;
    }

    @SubscribeEvent public static void submit(SubmitCustomGeometryEvent event) {
        drawn = 0;
        var mc = Minecraft.getInstance();
        if (!enabled() || snapshot.isEmpty() || mc.level == null || world.get() != mc.level) return;
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        BlockPos origin = BlockPos.containing(camera);
        long now = mc.level.getGameTime();
        var quality = ClientConfig.get(ClientConfig.EFFECT_BUDGET);
        int range = TrackTuning.range(quality), life = TrackTuning.lifetime(quality);
        int limit = Math.max(16, TrackTuning.quads(quality) / (1 + EffectPerformance.pressure()));
        List<Tile> captured = snapshot;
        for (Tile tile : captured.reversed()) {
            if (drawn + tile.cost <= limit && visible(tile, camera, range, now, life)) drawn += tile.cost;
        }
        if (drawn == 0) return;
        PoseStack pose = event.getPoseStack(); pose.pushPose();
        pose.translate(origin.getX() - camera.x, origin.getY() - camera.y, origin.getZ() - camera.z);
        event.getSubmitNodeCollector().submitCustomGeometry(pose, TYPE, (matrix, buffer) -> {
            int remaining = limit;
            for (Tile tile : captured.reversed()) {
                if (tile.cost > remaining || !visible(tile, camera, range, now, life)) continue;
                tile.emit(buffer, matrix, origin, TrackTuning.alpha(now - tile.born, life)); remaining -= tile.cost;
            }
        });
        pose.popPose();
    }

    private static boolean visible(Tile tile, Vec3 camera, int range, long now, int life) {
        return tile.valid && TrackTuning.alpha(now - tile.born, life) > 0
                && camera.distanceToSqr(tile.pos.getX() + 0.5, tile.y, tile.pos.getZ() + 0.5) <= range * range;
    }
    public static int quadCount() { return cache.weight(); }
    public static int pendingCount() { return pending.size(); }
    public static int reads() { return reads; }
    public static int fittedSurfaces() { return fitted; }
    public static int drawnQuads() { return drawn; }

    private static final class Request {
        final Vec3 contact;
        final List<List<TrackClip.Point>> patterns;
        final Kind kind;
        final float strength;
        final long born;
        final int minX, maxX, maxZ;
        int x, z;
        Request(Vec3 contact, List<List<TrackClip.Point>> patterns, Kind kind, float strength, long born) {
            this.contact = contact; this.patterns = patterns; this.kind = kind;
            List<TrackClip.Point> polygon = patterns.stream().flatMap(List::stream).toList(); this.strength = strength; this.born = born;
            minX = Mth.floor(polygon.stream().mapToDouble(TrackClip.Point::x).min().orElseThrow());
            maxX = Mth.floor(polygon.stream().mapToDouble(TrackClip.Point::x).max().orElseThrow());
            x = minX; z = Mth.floor(polygon.stream().mapToDouble(TrackClip.Point::z).min().orElseThrow());
            maxZ = Mth.floor(polygon.stream().mapToDouble(TrackClip.Point::z).max().orElseThrow());
        }
    }

    private static final class Tile {
        final BlockPos pos;
        final BlockState state, above;
        final List<TrackClip.Point> polygon;
        final double y;
        final long born;
        final int rgb, cost;
        final float opacity;
        int light;
        boolean valid = true;
        long checked = Long.MIN_VALUE / 2;
        Tile(BlockPos pos, BlockState state, BlockState above, List<TrackClip.Point> polygon, double y, long born,
                int rgb, float opacity, int light) {
            this.pos = pos; this.state = state; this.above = above; this.polygon = polygon; this.y = y;
            this.born = born; this.rgb = rgb; this.opacity = opacity * TrackLook.grain(pos.getX()+.5,pos.getZ()+.5); this.light = light; this.cost = TrackClip.quads(polygon);
        }
        void emit(VertexConsumer buffer, PoseStack.Pose pose, BlockPos origin, float fade) {
            int color = Math.round(255 * opacity * fade) << 24 | rgb;
            if (polygon.size() <= 4) {
                for (int i = 0; i < 4; i++) vertex(buffer, pose, origin, polygon.get(Math.min(i, polygon.size() - 1)), color);
            } else for (int i = 1; i + 1 < polygon.size(); i++) {
                vertex(buffer, pose, origin, polygon.getFirst(), color); vertex(buffer, pose, origin, polygon.get(i), color);
                vertex(buffer, pose, origin, polygon.get(i + 1), color); vertex(buffer, pose, origin, polygon.get(i + 1), color);
            }
        }
        private void vertex(VertexConsumer b, PoseStack.Pose pose, BlockPos origin, TrackClip.Point p, int color) {
            b.addVertex(pose, (float) (p.x() - origin.getX()), (float) (y - origin.getY()), (float) (p.z() - origin.getZ()))
                    .setColor(color).setUv(p.u(), p.v()).setLight(light).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(pose, 0, 1, 0);
        }
    }
}
