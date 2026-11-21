package io.github.mcheliceport.client.hud;

import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.client.AircraftClientEvents;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.entity.plane.PlaneEntity;
import io.github.mcheliceport.entity.tank.TankEntity;
import io.github.mcheliceport.entity.vehicle.VehicleEntity;
import io.github.mcheliceport.radar.RwrClientTracker;
import io.github.mcheliceport.radar.RwrThreat;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * Upstream's {@code HudRWR}: a vehicle with a radar warning receiver (an {@code RWR} section, or
 * one the server fits under {@code rwr.autoFit}) shows its scope whenever someone rides it, with
 * each threat's symbol in its direction around the ring, nearer threats closer to the centre.
 * Search radars are green (aircraft) or amber (ground), locks red and missiles flashing red, as
 * upstream; a LOCK or LAUNCH caption under the scope is a port addition. The direction is worked
 * out each frame from the emitter's position and the vehicle's heading.
 */
public final class RwrHudLayer {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "rwr");
    private static final Identifier RWR = texture("rwr");
    private static final Identifier RWR_HELI = texture("rwr_heli");
    private static final Identifier RWR_TANK = texture("rwr_tank");
    private static final Identifier RWR_FAC = texture("rwr_fac");
    /** Upstream's layout is given for a screen 520 units high and scaled to the GUI's height. */
    private static final double SCREEN_HEIGHT = 520.0;
    private static final int MSL_BLINK_TICKS = 3;

    private RwrHudLayer() {
    }

    private static Identifier texture(String name) {
        return Identifier.fromNamespaceAndPath("mcheli", "textures/" + name + ".png");
    }

    public static void register(io.github.mcheliceport.platform.HudLayers event) {
        event.registerAbove(AircraftHudLayer.ID, ID, RwrHudLayer::render);
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.gui.hud.isHidden()) {
            return;
        }
        AircraftEntity ac = AircraftClientEvents.riddenAircraft(mc.player);
        AircraftInfo info = ac != null ? ac.getAcInfo() : null;
        if (info == null || !info.hasRWR && !RwrClientTracker.isFitted(ac.getId())) {
            return;
        }
        Identifier texture = RWR;
        int size = 180;
        int centerX = 100;
        int centerY = 280;
        double minDistance = 50.0;
        double maxDistance = 4096.0;
        if (ac instanceof PlaneEntity) {
            if (info.isFloat) {
                texture = RWR_FAC;
                size = 160;
                centerX = 220;
                centerY = 370;
                minDistance = 15.0;
                maxDistance = 800.0;
            }
        } else if (ac instanceof HeliEntity) {
            texture = RWR_HELI;
        } else if (ac instanceof TankEntity || ac instanceof VehicleEntity) {
            texture = ac instanceof TankEntity ? RWR_TANK : RWR_FAC;
            size = 160;
            centerX = 220;
            centerY = 370;
            minDistance = 15.0;
            maxDistance = 800.0;
        }
        if (info.radarMaxTargetRange > 0.0F) {
            maxDistance = Math.min(maxDistance, info.radarMaxTargetRange);
        }
        double height = graphics.guiHeight();
        double x = height * (centerX / SCREEN_HEIGHT);
        double y = height * (centerY / SCREEN_HEIGHT);
        double half = height * (size / SCREEN_HEIGHT) / 2.0;
        GuiHudCanvas canvas = new GuiHudCanvas(graphics);
        // Upstream's HUD list held the scope twice for an RWR vehicle, so its translucent face drew twice.
        for (int i = 0; i < 2; i++) {
            canvas.texture(texture, x - half, y - half, half * 2.0, half * 2.0);
        }
        renderThreats(graphics, mc, ac, deltaTracker.getGameTimeDeltaPartialTick(false), x, y, half, minDistance,
                maxDistance);
    }

    /** Upstream {@code renderThreatRing}. */
    private static void renderThreats(GuiGraphicsExtractor graphics, Minecraft mc, AircraftEntity ac, float partial,
            double cx, double cy, double half, double minDistance, double maxDistance) {
        List<RwrThreat> threats = RwrClientTracker.threats(ac.getId());
        if (threats.isEmpty()) {
            return;
        }
        double outer = half * 0.88;
        double inner = Math.max(10.0, outer * 0.22);
        Vec3 pos = ac.getPosition(partial);
        float yaw = ac.getViewYRot(partial);
        long now = mc.level.getGameTime();
        boolean strong = (now / MSL_BLINK_TICKS & 1L) == 0L;
        boolean locked = false;
        boolean launch = false;
        for (RwrThreat t : threats) {
            if (t.emitterId() == ac.getId()) {
                continue;
            }
            double dx = t.x() - pos.x;
            double dy = t.y() - pos.y;
            double dz = t.z() - pos.z;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double clamped = Math.max(minDistance, Math.min(maxDistance, distance));
            double norm = (clamped - minDistance) / Math.max(1.0, maxDistance - minDistance);
            double ring = inner + (outer - inner) * norm;
            double bearing = RwrThreat.relativeBearing(dx, dz, yaw);
            double ang = Math.toRadians(bearing - 90.0);
            double px = cx + Math.cos(ang) * ring;
            double py = cy + Math.sin(ang) * ring;
            int color;
            if (t.isLaunch()) {
                launch = true;
                color = strong ? 0xFFFF2D2D : 0xFFCC3030;
            } else if (t.isLock()) {
                locked = true;
                color = 0xFFFF4A4A;
            } else {
                color = t.kind() == RwrThreat.AIRCRAFT ? 0xFF00FF00 : 0xFFFFE400;
            }
            int w = mc.font.width(t.name());
            graphics.text(mc.font, t.name(), (int) (px - w / 2.0), (int) (py - mc.font.lineHeight / 2.0), color, false);
        }
        String caption = launch ? (strong ? "LAUNCH" : null) : locked ? "LOCK" : null;
        if (caption != null) {
            int w = mc.font.width(caption);
            graphics.text(mc.font, caption, (int) (cx - w / 2.0), (int) (cy + half + 2.0), 0xFFFF3030, true);
        }
    }
}
