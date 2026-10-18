package io.github.mcheliceport.client.hud;

import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.client.input.ModKeyMappings;
import io.github.mcheliceport.client.tool.LightWeaponClient;
import io.github.mcheliceport.config.ClientConfig;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.item.LightWeaponItem;
import io.github.mcheliceport.weapon.WeaponBase;
import io.github.mcheliceport.weapon.guidance.WeaponGuidanceSystem;
import java.util.Random;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

/**
 * The sights of the hand-held Stinger and Javelin (upstream {@code MCH_GuiLightWeapon}): the
 * Stinger's scope or the Javelin's command launch unit with its indicators, the lock bar, the
 * range to the target, the key hints and, with night vision on, green noise.
 *
 * <p>Upstream projected the Javelin's target while entities rendered; here it is projected with
 * the frame's camera, as the entity markers are.
 */
public final class LightWeaponHudLayer {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "light_weapon_hud");
    private static final int KEY_COLOR = 0xCFFFFFFF;
    private static final int GREEN = 0xFF28D448;
    private static final int RED = 0xFFDF0408;
    private static final int TRACK = 0xA0FFFFFF;
    private static final int BLACK = 0xFF000000;
    private static final int WHITE = 0xFFFFFFFF;
    private static final double JAVELIN2 = 1024.0;
    private static final Random RAND = new Random();

    private LightWeaponHudLayer() {
    }

    public static void register(RegisterGuiLayersEvent event) {
        event.registerBelow(AircraftHudLayer.ID, ID, LightWeaponHudLayer::render);
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.gui.hud.isHidden() || !mc.options.getCameraType().isFirstPerson()
                || mc.gui.screen() != null && !(mc.gui.screen() instanceof ChatScreen)
                || !LightWeaponClient.isAiming(player)) {
            return;
        }
        WeaponGuidanceSystem gs = LightWeaponClient.guidance();
        WeaponBase weapon = LightWeaponClient.weapon();
        if (weapon == null) {
            return;
        }
        GuiHudCanvas canvas = new GuiHudCanvas(graphics);
        if (player.getEffect(MobEffects.NIGHT_VISION) != null) {
            AircraftHudLayer.drawNightVisionNoise(graphics, RAND);
        }
        double dist = 0.0;
        if (gs.getTargetEntity() != null) {
            dist = LightWeaponClient.horizontalDistance(player, gs.getTargetEntity());
        }
        boolean canFire = LightWeaponClient.weaponMode() == 0 || dist >= LightWeaponClient.TOP_ATTACK_MIN_DIST
                || gs.getLockCount() <= 0;
        ItemStack stack = player.getMainHandItem();
        int w = graphics.guiWidth();
        int h = graphics.guiHeight();
        if ("fgm148".equals(LightWeaponItem.weaponName(stack))) {
            drawJavelin(graphics, canvas, player, gs, weapon, canFire, stack,
                    deltaTracker.getGameTimeDeltaPartialTick(false));
            drawKeyBind(canvas, w, h, true);
        } else {
            double size = 512.0;
            while (size < w || size < h) {
                size *= 2.0;
            }
            canvas.tintedTexture("stinger", -(size - w) / 2.0, -(size - h) / 2.0 - 20.0, size, size, 0, 0, 1, 1,
                    WHITE);
            drawKeyBind(canvas, w, h, false);
        }
        drawLock(canvas, w, h, gs.getLockCount(), gs.getLockCountMax());
        drawRange(canvas, mc, player, gs, canFire, w, h);
    }

    /** Upstream {@code drawLock}: a bar under the sight filling as the lock builds. */
    private static void drawLock(GuiHudCanvas canvas, int w, int h, int count, int max) {
        int x = w / 2;
        int y = h / 2 + 20;
        canvas.rect(x - 20, y + 21, x + 20, y + 26, GREEN);
        float lock = (float) count / max;
        canvas.rect(x - 19, y + 22, x - 19 + (int) (38.0 * lock), y + 25, RED);
    }

    /** Upstream {@code drawRange}: the distance across the ground to the locking target. */
    private static void drawRange(GuiHudCanvas canvas, Minecraft mc, LocalPlayer player, WeaponGuidanceSystem gs,
            boolean canFire, int w, int h) {
        String msg = "[--.--]";
        int color = RED;
        if (gs.getLockCount() > 0) {
            Entity target = gs.getLockingEntity();
            if (target != null) {
                msg = String.format("[%.2f]", LightWeaponClient.horizontalDistance(player, target));
                color = canFire ? GREEN : RED;
                if (!ClientConfig.get(ClientConfig.HIDE_KEY_HINTS) && gs.isLockComplete()) {
                    canvas.text("Shot : " + keyName(ModKeyMappings.USE_WEAPON), w / 2, h / 2 + 65, KEY_COLOR, true);
                }
            }
        }
        canvas.text(msg, w / 2, h / 2 + 50, color, true);
    }

    /** Upstream {@code drawGuiFGM148}: the command launch unit, its target box and indicators. */
    private static void drawJavelin(GuiGraphicsExtractor graphics, GuiHudCanvas canvas, LocalPlayer player,
            WeaponGuidanceSystem gs, WeaponBase weapon, boolean canFire, ItemStack stack, float partialTicks) {
        int w = graphics.guiWidth();
        int h = graphics.guiHeight();
        double fac = Math.min(w / 800.0, h / 700.0);
        int size = (int) (1024.0 * fac);
        size = size / 64 * 64;
        fac = size / 1024.0;
        double left = -(size - w) / 2.0;
        double top = -(size - h) / 2.0 - 20;
        double right = left + size;
        double bottom = top + size;
        if (gs.getLockCount() > 0) {
            Entity target = gs.getLockingEntity();
            CameraRenderState camera = Minecraft.getInstance().gameRenderer.gameRenderState().levelRenderState
                    .cameraRenderState;
            double[] pos = target != null ? project(camera, graphics, target.getPosition(partialTicks)) : null;
            double cx = pos != null ? pos[0] : w / 2.0;
            double cy = pos != null ? pos[1] : h / 2.0;
            double ix = 280.0 * fac;
            double iy = 370.0 * fac;
            double sx = Mth.clamp(cx, left + ix, right - ix);
            double sy = Mth.clamp(cy, top + iy, bottom - iy);
            if (gs.getLockCount() >= gs.getLockCountMax() / 2) {
                canvas.line(new double[] {-1.0, sy, w + 1, sy, sx, -1.0, sx, h + 1}, TRACK, HudCanvas.LINES);
            }
            if (player.tickCount % 6 >= 3) {
                double[] bb = target != null ? project(camera, graphics, boxCorner(target, partialTicks)) : null;
                double bx = bb != null ? bb[0] : w / 2.0 - 65;
                double by = bb != null ? bb[1] : h / 2.0 - 50;
                double dx = Math.abs(cx - bx);
                double dy = Math.abs(cy - by);
                double p = 1.0 - (double) gs.getLockCount() / gs.getLockCountMax();
                dx = Mth.clamp(dx, 25.0, 70.0);
                dy = Mth.clamp(dy, 15.0, 70.0);
                dx += (70.0 - dx) * p;
                dy += (70.0 - dy) * p;
                int lx = 10;
                int ly = 6;
                canvas.line(new double[] {sx - dx, sy - dy + ly, sx - dx, sy - dy, sx - dx + lx, sy - dy}, TRACK,
                        HudCanvas.LINE_STRIP);
                canvas.line(new double[] {sx + dx, sy - dy + ly, sx + dx, sy - dy, sx + dx - lx, sy - dy}, TRACK,
                        HudCanvas.LINE_STRIP);
                dy /= 6.0;
                canvas.line(new double[] {sx - dx, sy + dy - ly, sx - dx, sy + dy, sx - dx + lx, sy + dy}, TRACK,
                        HudCanvas.LINE_STRIP);
                canvas.line(new double[] {sx + dx, sy + dy - ly, sx + dx, sy + dy, sx + dx - lx, sy + dy}, TRACK,
                        HudCanvas.LINE_STRIP);
            }
        }
        canvas.rect(-1, -1, (int) left + 1, h + 1, BLACK);
        canvas.rect((int) right - 1, -1, w + 1, h + 1, BLACK);
        canvas.rect(-1, -1, w + 1, (int) top + 1, BLACK);
        canvas.rect(-1, (int) bottom - 1, w + 1, h + 1, BLACK);
        canvas.tintedTexture("javelin", left, top, size, size, 0, 0, 1, 1, WHITE);
        boolean nightVision = player.getEffect(MobEffects.NIGHT_VISION) != null;
        // The indicators of javelin2.png: day sight, the night-vision cool-down, wide and narrow
        // field of view, seek, top or direct attack, and the fire-inhibit lights.
        if (!nightVision) {
            indicator(canvas, left, top, fac, 247, 211, 380, 350);
        }
        if (player.getTicksUsingItem() <= LightWeaponItem.NIGHT_VISION_TICKS) {
            indicator(canvas, left, top, fac, 130, 334, 257, 455);
        }
        if (LightWeaponClient.selectedZoom() == 0) {
            indicator(canvas, left, top, fac, 387, 211, 510, 350);
        }
        if (LightWeaponClient.selectedZoom() == weapon.getInfo().zoom.length - 1) {
            indicator(canvas, left, top, fac, 511, 211, 645, 350);
        }
        if (gs.getLockCount() > 0) {
            indicator(canvas, left, top, fac, 643, 211, 775, 350);
        }
        if (LightWeaponClient.weaponMode() == 1) {
            indicator(canvas, left, top, fac, 768, 340, 890, 455);
        } else {
            indicator(canvas, left, top, fac, 768, 456, 890, 565);
        }
        if (!canFire) {
            indicator(canvas, left, top, fac, 379, 670, 511, 810);
        }
        if (!LightWeaponItem.isLoaded(stack)) {
            indicator(canvas, left, top, fac, 512, 670, 645, 810);
        }
        if (gs.getLockCount() < gs.getLockCountMax()) {
            indicator(canvas, left, top, fac, 646, 670, 776, 810);
        }
        if (nightVision) {
            indicator(canvas, left, top, fac, 768, 562, 890, 694);
        }
    }

    private static void indicator(GuiHudCanvas canvas, double left, double top, double fac, double x, double y,
            double x1, double y1) {
        canvas.tintedTexture("javelin2", left + x * fac, top + y * fac, (x1 - x) * fac, (y1 - y) * fac,
                x / JAVELIN2, y / JAVELIN2, x1 / JAVELIN2, y1 / JAVELIN2, WHITE);
    }

    /** Upstream {@code markEntity}'s box corner: up by the marker height and out by its width. */
    private static Vec3 boxCorner(Entity entity, float partialTicks) {
        var info = entity instanceof AircraftEntity ac ? ac.getAcInfo() : null;
        float bw = entity.getBbWidth() > entity.getBbHeight() ? entity.getBbWidth()
                : info != null ? info.markerWidth : entity.getBbHeight();
        float bh = info != null ? info.markerHeight : entity.getBbHeight();
        return entity.getPosition(partialTicks).add(bw, bh, bw);
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
        return new double[] {(p.x / p.w + 1.0) * 0.5 * graphics.guiWidth(),
                (1.0 - p.y / p.w) * 0.5 * graphics.guiHeight()};
    }

    /** Upstream {@code drawKeyBind}. */
    private static void drawKeyBind(GuiHudCanvas canvas, int w, int h, boolean canSwitchMode) {
        if (ClientConfig.get(ClientConfig.HIDE_KEY_HINTS)) {
            return;
        }
        int x = w / 2 + 55;
        int y = h / 2 + 40;
        canvas.text("CAM MODE :", x, y + 10, KEY_COLOR, false);
        canvas.text("ZOOM      :", x, y + 20, KEY_COLOR, false);
        if (canSwitchMode) {
            canvas.text("MODE      :", x, y + 30, KEY_COLOR, false);
        }
        x += 60;
        canvas.text(keyName(ModKeyMappings.CAMERA_MODE), x, y + 10, KEY_COLOR, false);
        canvas.text(keyName(ModKeyMappings.ZOOM), x, y + 20, KEY_COLOR, false);
        if (canSwitchMode) {
            canvas.text(keyName(ModKeyMappings.SWITCH_WEAPON_MODE), x, y + 30, KEY_COLOR, false);
        }
    }

    private static String keyName(KeyMapping key) {
        return key.getTranslatedKeyMessage().getString();
    }
}
