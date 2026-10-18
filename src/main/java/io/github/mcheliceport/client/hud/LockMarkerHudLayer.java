package io.github.mcheliceport.client.hud;

import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.client.AircraftClientEvents;
import io.github.mcheliceport.content.weapon.WeaponInfo;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.flare.ChaffEntity;
import io.github.mcheliceport.entity.flare.FlareEntity;
import io.github.mcheliceport.weapon.WeaponBase;
import io.github.mcheliceport.weapon.WeaponSet;
import io.github.mcheliceport.weapon.guidance.WeaponGuidanceSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

/**
 * Upstream's {@code MCH_RenderAircraft.renderEntityMarker}: while the player's current weapon has a
 * seeker, a box hangs over everything it could lock (living things, vehicles and chaff in sight
 * within 1000 blocks), green, or red and thicker for the one it is locking, with the distance (and,
 * for radar seekers, the target's radar name) under it. The box dims past the weapon's lock range
 * or notch angle, hides beyond a BVR weapon's minimum range, and in first person a red line joins
 * the locked target to the vehicle.
 *
 * <p>Upstream drew the box in the world as a billboard with depth testing off; here the same
 * billboard is projected onto the GUI, which keeps its size and position on screen.
 */
public final class LockMarkerHudLayer {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "lock_markers");
    /** Upstream's billboard scale: one model unit in blocks. */
    private static final double UNIT = 0.02666667;

    private LockMarkerHudLayer() {
    }

    public static void register(RegisterGuiLayersEvent event) {
        event.registerBelow(AircraftHudLayer.ID, ID, LockMarkerHudLayer::render);
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        Entity viewer = mc.getCameraEntity();
        if (player == null || mc.level == null || viewer == null) {
            return;
        }
        AircraftEntity ac = AircraftClientEvents.riddenAircraft(player);
        if (ac == null) {
            return;
        }
        WeaponSet ws = ac.getCurrentWeapon(player);
        WeaponBase weapon = ws != null ? ws.getCurrentWeapon() : null;
        if (weapon == null || !(weapon.getGuidanceSystem() instanceof WeaponGuidanceSystem gs)) {
            return;
        }
        float partialTicks = deltaTracker.getGameTimeDeltaPartialTick(false);
        CameraRenderState camera = mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
        GuiHudCanvas canvas = new GuiHudCanvas(graphics);
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity == player || entity == ac
                    || !(entity instanceof LivingEntity || entity instanceof AircraftEntity
                            || entity instanceof ChaffEntity)
                    || !gs.canLockEntity(entity)) {
                continue;
            }
            drawMarker(graphics, canvas, mc, camera, ac, gs, weapon.getInfo(), viewer, entity, partialTicks);
        }
    }

    private static void drawMarker(GuiGraphicsExtractor graphics, GuiHudCanvas canvas, Minecraft mc,
            CameraRenderState camera, AircraftEntity ac, WeaponGuidanceSystem gs, @Nullable WeaponInfo wi,
            Entity viewer, Entity entity, float partialTicks) {
        Vec3 pos = entity.getPosition(partialTicks);
        double distance = entity.distanceTo(viewer);
        if (distance >= 1000.0 || wi != null && wi.enableBVR && distance > wi.minRangeBVR) {
            return;
        }
        if (mc.level.clip(new ClipContext(camera.pos, entity.position(), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.ANY, viewer)).getType() != HitResult.Type.MISS) {
            return;
        }
        Vec3 pv = ac.getDeltaMovement();
        Vec3 tv = entity.getDeltaMovement();
        double angleDeg = 0.0;
        if (pv.length() > 0.001 && tv.length() > 0.001) {
            double angle = Math.acos(Mth.clamp(pv.dot(tv) / (pv.length() * tv.length()), -1.0, 1.0));
            angleDeg = Math.toDegrees(angle > Math.PI / 2 ? Math.PI - angle : angle);
        }
        boolean locking = gs.isLockingEntity(entity);
        float alpha = wi != null && (angleDeg > wi.pdHDNMaxDegree || distance > wi.maxLockOnRange) ? 0.2F : 1.0F;
        float big = Math.max(entity.getBbWidth(), entity.getBbHeight());
        float baseSize = entity instanceof AircraftEntity || entity instanceof FlareEntity
                || entity instanceof ChaffEntity ? big * 40.0F : big * 20.0F;
        float size = Mth.clamp(baseSize + (float) ((distance - 10.0) / 290.0) * (300.0F - baseSize), baseSize, 300.0F);

        Matrix4f view = camera.viewRotationMatrix;
        Vec3 right = new Vec3(view.m00(), view.m10(), view.m20());
        Vec3 up = new Vec3(view.m01(), view.m11(), view.m21());
        Vec3 anchor = pos.add(0.0, entity.getBbHeight() + 2.0, 0.0).subtract(up.scale(9.374999 * UNIT));
        double[] a = project(camera, graphics, anchor);
        double[] r = project(camera, graphics, anchor.add(right));
        if (a == null || r == null) {
            return;
        }
        double ppb = Math.hypot(r[0] - a[0], r[1] - a[1]);
        double half = (size + 1.0F) * UNIT * ppb;
        double height = size * 2.0F * UNIT * ppb;
        double t = locking ? 2.5 : 1.5;
        int color = ARGB.colorFromFloat(alpha, locking ? 1.0F : 0.0F, locking ? 0.0F : 1.0F, 0.0F);
        double x0 = a[0] - half;
        double x1 = a[0] + half;
        double y0 = a[1];
        double y1 = a[1] + height;
        canvas.rect(x0 - t / 2, y0 - t / 2, x1 + t / 2, y0 + t / 2, color);
        canvas.rect(x0 - t / 2, y1 - t / 2, x1 + t / 2, y1 + t / 2, color);
        canvas.rect(x0 - t / 2, y0, x0 + t / 2, y1, color);
        canvas.rect(x1 - t / 2, y0, x1 + t / 2, y1, color);

        String name = "";
        if (gs.isRadarMissile) {
            name = entity instanceof AircraftEntity target ? target.getNameOnOtherRadar(ac) : "?";
        }
        String text = name.isEmpty() ? String.valueOf((int) distance) : name + " " + (int) distance;
        float fontSize = Mth.clamp(5.0F + (float) ((distance - 10.0) / 290.0) * 35.0F, 5.0F, 40.0F);
        float scale = (float) Math.min(fontSize * UNIT * ppb, 4.0);
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate((float) a[0], (float) (y1 + UNIT * ppb));
        pose.scale(scale, scale);
        canvas.text(text, 0, 0, 0xFF00FF00, true);
        pose.popMatrix();

        if (!ac.isUAV() && locking && mc.options.getCameraType().isFirstPerson()) {
            double[] from = project(camera, graphics, pos.add(0.0, entity.getBbHeight() / 2.0, 0.0));
            double[] to = project(camera, graphics, new Vec3(ac.xOld, ac.yOld - 1.0, ac.zOld));
            if (from != null && to != null) {
                canvas.line(new double[] {from[0], from[1], to[0], to[1]}, 0xFFFF0000, HudCanvas.LINES);
            }
        }
    }

    /** A world position on the GUI, or null behind the camera. */
    private static double @Nullable [] project(CameraRenderState camera, GuiGraphicsExtractor graphics, Vec3 pos) {
        Vector4f p = new Vector4f((float) (pos.x - camera.pos.x), (float) (pos.y - camera.pos.y),
                (float) (pos.z - camera.pos.z), 1.0F);
        camera.viewRotationMatrix.transform(p);
        camera.projectionMatrix.transform(p);
        if (p.w <= 1.0E-6F) {
            return null;
        }
        return new double[] {(p.x / p.w + 1.0) * 0.5 * graphics.guiWidth(), (1.0 - p.y / p.w) * 0.5 * graphics.guiHeight()};
    }
}
