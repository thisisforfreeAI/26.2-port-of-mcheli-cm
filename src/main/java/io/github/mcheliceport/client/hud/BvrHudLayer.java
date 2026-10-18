package io.github.mcheliceport.client.hud;

import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.client.AircraftClientEvents;
import io.github.mcheliceport.content.weapon.WeaponInfo;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.net.BvrLockPayload;
import io.github.mcheliceport.radar.EntityInfo;
import io.github.mcheliceport.radar.EntityInfoClientTracker;
import io.github.mcheliceport.weapon.WeaponSet;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * Upstream's {@code HudBVRLock}: in first person, a weapon with {@code EnableBVR} marks the vehicles
 * (and, for a radar seeker, the chaff) the server reports beyond its minimum BVR range, and incoming
 * air-to-air missiles beyond 100 blocks. Markers brighten towards the screen centre; one held at the
 * centre turns red and becomes the seeker's target on the server. Markers far off centre are pulled
 * in to a ring.
 *
 * <p>Upstream's marker textures were not in its assets, so upstream drew the missing-texture square;
 * here a vehicle gets a square outline and a missile a diamond, in upstream's colours.
 */
public final class BvrHudLayer {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "bvr_lock");
    private static final double BOX_SIZE = 24.0;

    private BvrHudLayer() {
    }

    public static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(AircraftHudLayer.ID, ID, BvrHudLayer::render);
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gui.hud.isHidden() || !mc.options.getCameraType().isFirstPerson()) {
            return;
        }
        AircraftEntity ac = AircraftClientEvents.riddenAircraft(player);
        WeaponSet ws = ac != null ? ac.getCurrentWeapon(player) : null;
        if (ws == null || ws.getCurrentWeapon() == null) {
            return;
        }
        WeaponInfo wi = ws.getCurrentWeapon().getInfo();
        if (wi == null || !wi.enableBVR) {
            return;
        }
        float partialTicks = deltaTracker.getGameTimeDeltaPartialTick(false);
        GuiHudCanvas canvas = new GuiHudCanvas(graphics);
        double w = graphics.guiWidth();
        double h = graphics.guiHeight();
        for (EntityInfo entity : EntityInfoClientTracker.all()) {
            if (!canRender(entity, player, wi)) {
                continue;
            }
            Vec3 pos = entity.position(partialTicks).add(0.0, 1.0, 0.0);
            double[] screen = worldToScreen(mc, player, pos, partialTicks, w, h);
            double sx = screen[0];
            double sy = screen[1];
            if (sx <= 0 || sy <= 0) {
                continue;
            }
            boolean lock = false;
            float alpha = 0.1F;
            double ox = screen[2];
            double oy = screen[3];
            double distScreen = ox * ox + oy * oy;
            if (distScreen < sq(0.038 * h)) {
                alpha = 1.0F;
                lock = true;
                if (mc.level.getGameTime() % 10 == 0) {
                    ClientPacketDistributor.sendToServer(new BvrLockPayload(entity.entityId()));
                }
            } else if (distScreen < sq(0.076 * h)) {
                alpha = 1.0F;
            } else if (distScreen < sq(0.152 * h)) {
                alpha = 0.8F;
            } else if (distScreen < sq(0.228 * h)) {
                alpha = 0.6F;
            } else if (distScreen < sq(0.288 * h)) {
                alpha = 0.4F;
            } else if (distScreen > sq(0.384 * h)) {
                double ratio = 200.0 / Math.sqrt(distScreen);
                sx = w / 2.0 + ox * ratio;
                sy = h / 2.0 + oy * ratio;
                alpha = 0.2F;
            }
            double distance = Math.sqrt(player.distanceToSqr(pos));
            if (entity.kind() == EntityInfo.Kind.AA_MISSILE) {
                if (distance < 1000.0 && alpha > 0.4F) {
                    diamond(canvas, sx, sy, ARGB.colorFromFloat(alpha, 1.0F, 0.0F, 0.0F));
                    graphics.text(mc.font, String.format("[MSL %.1fm]", distance), (int) (sx - 20), (int) (sy + 12),
                            0xFFFF0000, false);
                }
            } else {
                square(canvas, sx, sy, ARGB.colorFromFloat(alpha, lock ? 1.0F : 0.0F, lock ? 0.0F : 1.0F, 0.0F));
                if (alpha >= 0.6F) {
                    graphics.text(mc.font, String.format("[%s %.1fm]", ac.getNameOnMyRadar(entity), distance),
                            (int) (sx - 20), (int) (sy + 12), lock ? 0xFFFF0000 : 0xFF00FF00, false);
                }
            }
        }
    }

    /**
     * Upstream {@code canRenderEntity}. Upstream looked for planes under their pre-CE class name and
     * so never showed them; they are shown here like helicopters.
     */
    private static boolean canRender(EntityInfo entity, LocalPlayer player, WeaponInfo wi) {
        double distSq = entity.distanceToSqr(player);
        double minSq = (double) wi.minRangeBVR * wi.minRangeBVR;
        return switch (entity.kind()) {
            case PLANE, HELI -> distSq > minSq;
            case CHAFF -> wi.isRadarMissile && distSq > minSq;
            case AA_MISSILE -> distSq > 100.0 * 100.0;
            default -> false;
        };
    }

    /**
     * Upstream {@code worldToScreen}: a projection along the player's look with the FOV setting,
     * hiding anything more than 45° off it. Returns the screen position and its offset from the centre.
     */
    private static double[] worldToScreen(Minecraft mc, LocalPlayer player, Vec3 pos, float partialTicks, double w,
            double h) {
        double[] none = {-1, -1, -1, -1};
        Vec3 rel = pos.subtract(mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState.pos);
        Vec3 look = player.getViewVector(partialTicks);
        double angle = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, rel.normalize().dot(look)))));
        if (angle > 45) {
            return none;
        }
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 1e-5) {
            double yawRad = Math.toRadians(player.getYRot() + 90);
            right = new Vec3(Math.cos(yawRad), 0, -Math.sin(yawRad));
        }
        right = right.normalize();
        Vec3 up = right.cross(look).normalize();
        double dx = rel.dot(right);
        double dy = rel.dot(up);
        double dz = rel.dot(look);
        if (dz <= 0) {
            return none;
        }
        double tanHalfFov = Math.tan(Math.toRadians(mc.options.fov().get()) * 0.5);
        double xProj = dx / dz / (w / h * tanHalfFov);
        double yProj = dy / dz / tanHalfFov;
        double screenX = w / 2.0 + xProj * (w / 2.0);
        double screenY = h / 2.0 - yProj * (h / 2.0);
        return new double[] {screenX, screenY, screenX - w / 2.0, screenY - h / 2.0};
    }

    private static void square(GuiHudCanvas canvas, double x, double y, int color) {
        double half = BOX_SIZE / 2.0;
        canvas.line(new double[] {x - half, y - half, x + half, y - half, x + half, y + half, x - half, y + half},
                color, HudCanvas.LINE_LOOP);
    }

    private static void diamond(GuiHudCanvas canvas, double x, double y, int color) {
        double half = BOX_SIZE / 2.0;
        canvas.line(new double[] {x, y - half, x + half, y, x, y + half, x - half, y}, color, HudCanvas.LINE_LOOP);
    }

    private static double sq(double v) {
        return v * v;
    }
}
