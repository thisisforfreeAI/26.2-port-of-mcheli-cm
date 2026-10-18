package io.github.mcheliceport.client.hud;

import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.client.camera.GltdCameraClient;
import io.github.mcheliceport.client.input.ModKeyMappings;
import io.github.mcheliceport.config.ClientConfig;
import io.github.mcheliceport.entity.aircraft.AircraftCamera;
import io.github.mcheliceport.entity.gltd.GltdEntity;
import io.github.mcheliceport.weapon.types.CasWeapon;
import java.util.Random;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * The laser designator's HUD (upstream {@code MCH_GuiGLTD}): zoom, the strike and whether it is
 * ready, the designator's position and the operator's view angles, the camera mode, the sight,
 * the position of and range to the block in view, and the key hints.
 */
public final class GltdHudLayer {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "gltd_hud");
    private static final int GREEN = 0xCF1FFF1F;
    private static final int RED = 0xCFDF0408;
    private static final int WHITE = 0xCFFFFFFF;
    private static final int GREY = 0xCF7F7F7F;
    /** Upstream's HUD range, shorter than the server's 150-block strike check. */
    private static final double RANGE = 80.0;
    private static final Random RAND = new Random();

    private GltdHudLayer() {
    }

    public static void register(RegisterGuiLayersEvent event) {
        event.registerBelow(AircraftHudLayer.ID, ID, GltdHudLayer::render);
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        GltdEntity gltd = GltdCameraClient.riddenGltd();
        if (player == null || gltd == null || mc.gui.hud.isHidden()
                || mc.gui.screen() != null && !(mc.gui.screen() instanceof ChatScreen)) {
            return;
        }
        if (!mc.options.getCameraType().isFirstPerson() && !ClientConfig.get(ClientConfig.HUD_IN_THIRD_PERSON)) {
            return;
        }
        GuiHudCanvas canvas = new GuiHudCanvas(graphics);
        int cx = graphics.guiWidth() / 2;
        int cy = graphics.guiHeight() / 2;
        AircraftCamera camera = gltd.camera;
        if (camera.getMode(0) == AircraftCamera.MODE_NIGHTVISION) {
            AircraftHudLayer.drawNightVisionNoise(graphics, RAND);
        }
        canvas.text(String.format("x%.1f", camera.getCameraZoom()), cx - 70, cy + 10, WHITE, false);
        CasWeapon weapon = gltd.weapon();
        if (weapon != null) {
            canvas.text(weapon.getName(), cx - 200, cy + 65, gltd.countWait == 0 ? GREEN : RED, false);
        }
        canvas.text(String.format("X: %+.1f", gltd.getX()), cx - 145, cy, GREEN, false);
        canvas.text(String.format("Y: %+.1f", gltd.getY()), cx - 145, cy + 10, GREEN, false);
        canvas.text(String.format("Z: %+.1f", gltd.getZ()), cx - 145, cy + 20, GREEN, false);
        canvas.text(String.format("AX: %+.1f", player.getYRot()), cx - 145, cy + 40, GREEN, false);
        canvas.text(String.format("AY: %+.1f", player.getXRot()), cx - 145, cy + 50, GREEN, false);
        canvas.text(camera.getModeName(0), cx + 30, cy - 50, GREEN, false);
        drawSight(canvas, cx, cy);
        drawTargetPosition(canvas, mc, player, gltd, cx, cy);
        if (!ClientConfig.get(ClientConfig.HIDE_KEY_HINTS)) {
            drawKeyBind(canvas, mc, camera, cx, cy);
        }
    }

    /** Upstream {@code drawTargetPosition}: where the view meets a block within 80 blocks, and how far. */
    private static void drawTargetPosition(GuiHudCanvas canvas, Minecraft mc, LocalPlayer player, GltdEntity gltd,
            int cx, int cy) {
        AircraftCamera c = gltd.camera;
        Vec3 src = new Vec3(c.posX, c.posY, c.posZ);
        Vec3 dst = src.add(Vec3.directionFromRotation(player.getXRot(), player.getYRot()).scale(RANGE));
        BlockHitResult hit = mc.level.clip(new ClipContext(src, dst, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE,
                player));
        if (hit.getType() == HitResult.Type.BLOCK) {
            Vec3 at = hit.getLocation();
            canvas.text(String.format("X: %+.2fm", at.x), cx + 50, cy - 20, GREEN, false);
            canvas.text(String.format("Y: %+.2fm", at.y), cx + 50, cy - 5, GREEN, false);
            canvas.text(String.format("Z: %+.2fm", at.z), cx + 50, cy + 10, GREEN, false);
            double len = at.distanceTo(src);
            canvas.text(String.format("[%.2fm]", len), cx, cy + 30, len > 20.0 ? GREEN : RED, true);
        } else {
            canvas.text("X: --.--m", cx + 50, cy - 20, GREEN, false);
            canvas.text("Y: --.--m", cx + 50, cy - 5, GREEN, false);
            canvas.text("Z: --.--m", cx + 50, cy + 10, GREEN, false);
            canvas.text("[--.--m]", cx, cy + 30, RED, true);
        }
    }

    /** Upstream {@code drawSight}: four corner brackets 60 by 40 around the centre. */
    private static void drawSight(GuiHudCanvas canvas, double x, double y) {
        double[] lines = {
                x - 30, y - 10, x - 30, y - 20, x - 30, y - 20, x - 10, y - 20,
                x - 30, y + 10, x - 30, y + 20, x - 30, y + 20, x - 10, y + 20,
                x + 30, y - 10, x + 30, y - 20, x + 30, y - 20, x + 10, y - 20,
                x + 30, y + 10, x + 30, y + 20, x + 30, y + 20, x + 10, y + 20};
        canvas.line(lines, GREEN, HudCanvas.LINES);
    }

    /** Upstream {@code drawKeyBind}. */
    private static void drawKeyBind(GuiHudCanvas canvas, Minecraft mc, AircraftCamera camera, int cx, int cy) {
        int x = cx + 55;
        int y = cy + 40;
        int zoomIn = camera.getCameraZoom() < GltdEntity.MAX_ZOOM ? WHITE : GREY;
        int zoomOut = camera.getCameraZoom() > 1.0F ? WHITE : GREY;
        canvas.text("DISMOUNT :", x, y, WHITE, false);
        canvas.text("CAM MODE :", x, y + 10, WHITE, false);
        canvas.text("ZOOM IN   :", x, y + 20, zoomIn, false);
        canvas.text("ZOOM OUT :", x, y + 30, zoomOut, false);
        x += 60;
        canvas.text(keyName(mc.options.keyShift) + " or " + keyName(ModKeyMappings.UNMOUNT), x, y, WHITE, false);
        canvas.text(keyName(ModKeyMappings.CAMERA_MODE), x, y + 10, WHITE, false);
        canvas.text(keyName(ModKeyMappings.ZOOM), x, y + 20, zoomIn, false);
        canvas.text(keyName(ModKeyMappings.SWITCH_WEAPON_MODE), x, y + 30, zoomOut, false);
    }

    private static String keyName(KeyMapping key) {
        return key.getTranslatedKeyMessage().getString();
    }
}
