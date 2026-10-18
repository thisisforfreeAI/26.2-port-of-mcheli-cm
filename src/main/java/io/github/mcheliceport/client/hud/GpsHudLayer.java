package io.github.mcheliceport.client.hud;

import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.client.AircraftClientEvents;
import io.github.mcheliceport.weapon.GpsPosition;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import org.joml.Vector4f;

/**
 * Upstream's {@code HudGPS}: while riding a vehicle with a GPS point set, a 24-pixel marker sits on
 * the point with its distance under it, green and brighter the nearer it is to the line of sight,
 * red within 1.5°.
 *
 * <p>Upstream drew the marker with world transforms inside the 2D overlay pass, which put it at a
 * screen position equal to the point's offset in blocks, and its texture ({@code gps_position.png})
 * was not in its assets. Here the marker is projected onto the point, as the code intended, and
 * drawn as a square outline.
 */
public final class GpsHudLayer {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "gps");
    private static final double ICON_SIZE = 24.0;

    private GpsHudLayer() {
    }

    public static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(AircraftHudLayer.ID, ID, GpsHudLayer::render);
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        GpsPosition.Point gps = GpsPosition.client();
        if (player == null || gps == null || mc.gui.hud.isHidden() || AircraftClientEvents.riddenAircraft(player) == null) {
            return;
        }
        float pt = deltaTracker.getGameTimeDeltaPartialTick(false);
        Vec3 point = new Vec3(gps.x(), gps.y(), gps.z());
        Vec3 toTarget = point.subtract(player.getEyePosition(pt));
        if (toTarget.length() < 1e-4) {
            return;
        }
        double dot = Math.clamp(toTarget.normalize().dot(player.getViewVector(pt)), -1.0, 1.0);
        double angleDeg = Math.toDegrees(Math.acos(dot));
        boolean inLock = angleDeg <= 1.5;
        float alpha = angleDeg <= 3.0 ? 1.0F : angleDeg <= 6.0 ? 0.8F : angleDeg <= 9.0 ? 0.6F : 0.4F;
        CameraRenderState camera = mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
        double[] at = project(camera, graphics, point.add(0.0, 0.2, 0.0));
        if (at == null) {
            return;
        }
        int color = inLock ? 0xFFFF0000 : ARGB.colorFromFloat(alpha, 0.0F, 1.0F, 0.0F);
        double half = ICON_SIZE / 2.0;
        new GuiHudCanvas(graphics).line(new double[] {at[0] - half, at[1] - half, at[0] + half, at[1] - half,
                at[0] + half, at[1] + half, at[0] - half, at[1] + half}, color, HudCanvas.LINE_LOOP);
        String text = String.format("[GPS %.1fm]", Math.sqrt(player.distanceToSqr(point)));
        graphics.text(mc.font, text, (int) (at[0] - mc.font.width(text) / 2.0), (int) (at[1] + half + 8.0),
                inLock ? 0xFFFF0000 : 0xFF00FF00, false);
    }

    private static double @org.jspecify.annotations.Nullable [] project(CameraRenderState camera,
            GuiGraphicsExtractor graphics, Vec3 pos) {
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
