package io.github.mcheliceport.client.hud;

import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.client.AircraftClientEvents;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.content.aircraft.CrewRole;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.aircraft.CrewRoles;
import io.github.mcheliceport.weapon.WeaponBase;
import io.github.mcheliceport.weapon.WeaponSet;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

/**
 * M10b, a port addition: the crew HUD. On a vehicle with a loader or commander seat, a panel at the
 * left lists the crew seats ({@code P G L C}), green when manned, with the rider's own seat
 * underlined. A loader also sees every gun's magazine, reserve and reload. Every rider sees the
 * commander's mark as a diamond with its range.
 */
public final class CrewHudLayer {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "crew");
    private static final int X = 4;
    private static final int Y = 40;
    private static final int BAR = 40;
    private static final int MANNED = 0xFF55FF55;
    private static final int EMPTY = 0xFF808080;
    private static final int MARK = 0xFFFF5555;

    private CrewHudLayer() {
    }

    public static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(AircraftHudLayer.ID, ID, CrewHudLayer::render);
    }

    /** Whether the vehicle type gets the crew panel. */
    public static boolean hasCrewPanel(AircraftInfo info) {
        return CrewRoles.hasRole(info, CrewRole.LOADER) || CrewRoles.hasRole(info, CrewRole.COMMANDER);
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gui.screen() != null || mc.gui.hud.isHidden()) {
            return;
        }
        AircraftEntity ac = AircraftClientEvents.riddenAircraft(player);
        AircraftInfo info = ac == null ? null : ac.getAcInfo();
        if (info == null) {
            return;
        }
        drawMark(graphics, mc, ac);
        if (!hasCrewPanel(info)) {
            return;
        }
        Font font = mc.font;
        int own = ac.getSeatIdByEntity(player);
        int x = X;
        int y = Y;
        graphics.text(font, "CREW", x, y, 0xFFFFFFFF);
        x += font.width("CREW ");
        for (int sid = 0; sid < info.getNumSeat(); sid++) {
            CrewRole role = CrewRoles.role(info, sid);
            if (role == CrewRole.PASSENGER) {
                continue;
            }
            Entity rider = ac.getEntityBySeatId(sid);
            String s = String.valueOf(role.letter());
            graphics.text(font, s, x, y, rider != null && rider.isAlive() ? MANNED : EMPTY);
            if (sid == own) {
                graphics.fill(x, y + font.lineHeight, x + font.width(s), y + font.lineHeight + 1, 0xFFFFFFFF);
            }
            x += font.width(s) + 4;
        }
        y += font.lineHeight + 3;
        float factor = CrewRoles.reloadFactor(ac);
        if (factor != 1.0F) {
            graphics.text(font, String.format("RELOAD x%.1f", factor), X, y, factor < 1.0F ? MANNED : 0xFFFFAA00);
            y += font.lineHeight + 2;
        }
        if (CrewRoles.roleOf(ac, player) == CrewRole.LOADER) {
            drawGuns(graphics, font, ac, y);
        }
    }

    /** Each gun with a magazine: name, rounds in it / reserve, and a bar that fills as it reloads. */
    private static void drawGuns(GuiGraphicsExtractor graphics, Font font, AircraftEntity ac, int y) {
        int next = CrewRoles.loaderTarget(ac.weapons());
        for (int i = 0; i < ac.getWeaponNum(); i++) {
            WeaponSet set = ac.getWeapon(i);
            WeaponBase weapon = set.getCurrentWeapon();
            if (weapon == null || weapon.getInfo() == null || set.getMagSize() <= 0) {
                continue;
            }
            String line = (i == next ? "> " : "  ") + set.getDisplayName() + " " + set.getAmmo() + "/"
                    + set.getMagSize() + " +" + set.getRestAllAmmoNum();
            graphics.text(font, line, X, y, set.getAmmo() == 0 ? 0xFFFFAA00 : 0xFFFFFFFF);
            int bx = X + font.width(line) + 4;
            int by = y + font.lineHeight / 2 - 1;
            float fill;
            int total = CrewRoles.reloadTicks(ac, weapon.getReloadCount());
            if (set.reloadCooldown > 0 && total > 0) {
                fill = 1.0F - Math.min(1.0F, set.reloadCooldown / (float) total);
            } else {
                fill = set.getAmmo() / (float) set.getMagSize();
            }
            graphics.fill(bx, by, bx + BAR, by + 3, 0x88000000);
            graphics.fill(bx, by, bx + Math.round(BAR * fill), by + 3, set.reloadCooldown > 0 ? 0xFFFFAA00 : MANNED);
            y += font.lineHeight + 2;
        }
    }

    /** The commander's mark: a diamond on the target and its range. */
    private static void drawMark(GuiGraphicsExtractor graphics, Minecraft mc, AircraftEntity ac) {
        Entity mark = ac.getCrewMark();
        if (mark == null) {
            return;
        }
        CameraRenderState camera = mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
        double[] p = project(camera, graphics, mark.position().add(0, mark.getBbHeight() / 2.0, 0));
        if (p == null) {
            return;
        }
        int cx = (int) p[0];
        int cy = (int) p[1];
        for (int i = 0; i <= 6; i++) {
            graphics.fill(cx - 6 + i, cy - i, cx - 5 + i, cy - i + 1, MARK);
            graphics.fill(cx + i, cy - 6 + i, cx + i + 1, cy - 5 + i, MARK);
            graphics.fill(cx + 6 - i, cy + i, cx + 7 - i, cy + i + 1, MARK);
            graphics.fill(cx - i, cy + 6 - i, cx - i + 1, cy + 7 - i, MARK);
        }
        String range = "MARK " + Math.round(mark.distanceTo(ac)) + "m";
        graphics.text(mc.font, range, cx - mc.font.width(range) / 2, cy + 9, MARK);
    }

    /** A world position on the GUI, or null behind the camera. */
    private static double @Nullable [] project(CameraRenderState camera, GuiGraphicsExtractor graphics, Vec3 pos) {
        Vector4f v = new Vector4f((float) (pos.x - camera.pos.x), (float) (pos.y - camera.pos.y),
                (float) (pos.z - camera.pos.z), 1.0F);
        camera.viewRotationMatrix.transform(v);
        camera.projectionMatrix.transform(v);
        if (v.w <= 1.0E-6F) {
            return null;
        }
        return new double[] {(v.x / v.w + 1.0) * 0.5 * graphics.guiWidth(), (1.0 - v.y / v.w) * 0.5 * graphics.guiHeight()};
    }
}
