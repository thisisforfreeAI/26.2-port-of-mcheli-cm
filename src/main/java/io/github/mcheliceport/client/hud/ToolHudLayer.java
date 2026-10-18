package io.github.mcheliceport.client.hud;

import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.client.input.ModKeyMappings;
import io.github.mcheliceport.client.tool.RangeFinderClient;
import io.github.mcheliceport.client.tool.TargetMarkers;
import io.github.mcheliceport.config.ClientConfig;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.seat.SeatEntity;
import io.github.mcheliceport.item.RangeFinderItem;
import io.github.mcheliceport.item.WrenchItem;
import io.github.mcheliceport.multiplay.Spotting;
import io.github.mcheliceport.multiplay.TargetType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

/**
 * The HUD of the hand tools and of spotting: upstream {@code MCH_GuiTargetMarker} (markers over
 * teammates and spotted entities, and the mark point), {@code MCH_GuiRangeFinder} (the scope) and
 * {@code MCH_GuiWrench} (the health of the vehicle the wrench points at).
 *
 * <p>Upstream projected marker positions while entities rendered; here they are projected with
 * the frame's camera, as the CCIP is.
 */
public final class ToolHudLayer {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "tool_hud");
    /** Upstream {@code COLOR_TABLE}, by {@link TargetType} ordinal. */
    private static final int[] MARKER_COLORS = {0, 0xCFCFCFCF, 0xCFFF0000, 0xCFFFFF00, 0xCF00FFFF, 0xCFFF4F1F,
            0xFFFF0000};
    private static final int GREEN = 0xFF10FF20;
    private static final int RED = 0xFFFF1010;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GREY = 0xFF404040;
    private static final int DIGIT_SIZE = 16;
    private static final int HP_HEALTHY = 0xFF28A745;
    private static final int HP_LOW = 0xFFDF4758;
    private static final int HP_BACKGROUND = 0xFF14161C;

    private ToolHudLayer() {
    }

    public static void register(RegisterGuiLayersEvent event) {
        event.registerBelow(AircraftHudLayer.ID, ID, ToolHudLayer::render);
    }

    /** The scope hides the hand, as the spyglass does. */
    public static void onRenderHand(RenderHandEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && RangeFinderItem.canUse(player) && RangeFinderClient.isUsingScope(player)) {
            event.setCanceled(true);
        }
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gui.hud.isHidden()
                || mc.gui.screen() != null && !(mc.gui.screen() instanceof ChatScreen)) {
            return;
        }
        float partialTicks = deltaTracker.getGameTimeDeltaPartialTick(false);
        GuiHudCanvas canvas = new GuiHudCanvas(graphics);
        if (TargetMarkers.enabled()) {
            drawMarkers(graphics, canvas, mc, player, partialTicks);
        }
        if (!mc.options.getCameraType().isFirstPerson()) {
            return;
        }
        if (player.getMainHandItem().getItem() instanceof WrenchItem) {
            AircraftEntity ac = WrenchItem.aimedAircraft(player);
            if (ac != null && ac.getMaxHP() > 0) {
                drawWrenchHp(graphics, canvas, ac.getHP(), ac.getMaxHP());
            }
        }
        if (RangeFinderItem.canUse(player) && RangeFinderClient.isUsingScope(player)) {
            drawRangeFinder(graphics, canvas, mc, player);
        }
    }

    // ---- markers ----

    private record Mark(TargetType type, double x, double y, boolean front, @Nullable Vec3 point) {
    }

    /** Upstream {@code addMarkEntityPos} for every entity in view, then {@code drawMark}. */
    private static void drawMarkers(GuiGraphicsExtractor graphics, GuiHudCanvas canvas, Minecraft mc,
            LocalPlayer player, float partialTicks) {
        CameraRenderState camera = mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
        List<Mark> marks = new ArrayList<>();
        Vec3 eyes = player.getEyePosition(partialTicks);
        for (Entity entity : mc.level.entitiesForRendering()) {
            TargetType type = markerType(player, eyes, entity);
            if (type == TargetType.NONE) {
                continue;
            }
            Vec3 pos = entity.getPosition(partialTicks);
            double height = entity instanceof AircraftEntity ac
                    ? (ac.getAcInfo() != null ? ac.getAcInfo().markerHeight : 1.0F) : entity.getBbHeight() + 0.5;
            Mark mark = project(camera, graphics, pos.add(0.0, height, 0.0), type, null);
            if (mark != null) {
                marks.add(mark);
            }
        }
        Vec3 point = TargetMarkers.INSTANCE.markPoint();
        if (point != null) {
            Mark mark = project(camera, graphics, point, TargetType.POINT, point);
            if (mark != null) {
                marks.add(mark);
            }
        }
        int w = graphics.guiWidth();
        int h = graphics.guiHeight();
        double size = ClientConfig.get(ClientConfig.ENTITY_MARKER_SIZE);
        double markSize = ClientConfig.get(ClientConfig.BLOCK_MARKER_SIZE);
        for (Mark m : marks) {
            int color = MARKER_COLORS[m.type.ordinal()];
            if (m.type != TargetType.POINT) {
                if (m.front && m.x >= 0 && m.x <= w && m.y >= 0 && m.y <= h) {
                    canvas.triangle(m.x + size / 2.0, m.y - 10.0 - size, m.x - size / 2.0, m.y - 10.0 - size, m.x,
                            m.y - 10.0, color);
                }
                continue;
            }
            if (m.front && m.x >= 0 && m.x <= w - 20 && m.y >= 0 && m.y <= h - 40) {
                double dist = player.position().distanceTo(m.point);
                canvas.text(String.format("%.0fm", dist), (int) m.x, (int) (m.y + markSize * 1.1 + 16.0), color, true);
                if (Math.abs(m.x - w / 2.0) <= 20 && Math.abs(m.y - h / 2.0) <= 20) {
                    int tx = (int) (m.x + markSize + 18.0);
                    canvas.text(String.format("x : %.0f", m.point.x), tx, (int) m.y - 12, color, false);
                    canvas.text(String.format("y : %.0f", m.point.y), tx, (int) m.y - 4, color, false);
                    canvas.text(String.format("z : %.0f", m.point.z), tx, (int) m.y + 4, color, false);
                }
                rhombus(canvas, 15, m.x, m.y, markSize, color);
            } else {
                // Upstream: off screen, arrows at the edges point the way.
                double s = 30.0;
                if (m.x < s) {
                    rhombus(canvas, 1, s, h / 2.0, markSize, color);
                } else if (m.x > w - s) {
                    rhombus(canvas, 4, w - s, h / 2.0, markSize, color);
                }
                if (m.y < s) {
                    rhombus(canvas, 8, w / 2.0, s, markSize, color);
                } else if (m.y > h - s * 2.0) {
                    rhombus(canvas, 2, w / 2.0, h - s * 2.0, markSize, color);
                }
            }
        }
    }

    /**
     * Upstream {@code addMarkEntityPos}'s choice of marker: vehicles and players with a teammate
     * aboard show as teammates; anything spotted shows as the player sees it; the player's own
     * vehicle, the player and riders (their vehicle carries the marker) show nothing.
     */
    static TargetType markerType(LocalPlayer player, Vec3 eyes, Entity entity) {
        TargetType type = TargetType.NONE;
        if (entity instanceof AircraftEntity ac) {
            if (ac.isMountedEntity(player)) {
                return TargetType.NONE;
            }
            if (Spotting.isMountedSameTeam(ac, player)) {
                type = TargetType.SAME_TEAM_PLAYER;
            }
        } else if (entity instanceof LivingEntity) {
            if (entity instanceof Player) {
                if (entity == player || entity.getVehicle() instanceof SeatEntity
                        || entity.getVehicle() instanceof AircraftEntity) {
                    return TargetType.NONE;
                }
                if (player.getTeam() != null && player.isAlliedTo(entity)) {
                    type = TargetType.SAME_TEAM_PLAYER;
                }
            }
        } else {
            return TargetType.NONE;
        }
        if (type == TargetType.NONE && TargetMarkers.INSTANCE.isSpotted(entity)) {
            type = Spotting.canSpotEntity(player, eyes, entity, false);
        }
        return type;
    }

    /**
     * Projects a world position onto the GUI. Behind the camera the position is pushed far to the
     * side it lies on, as upstream's {@code gluProject} handling did, so only the edge arrows show.
     */
    private static @Nullable Mark project(CameraRenderState camera, GuiGraphicsExtractor graphics, Vec3 pos,
            TargetType type, @Nullable Vec3 point) {
        Vector4f p = new Vector4f((float) (pos.x - camera.pos.x), (float) (pos.y - camera.pos.y),
                (float) (pos.z - camera.pos.z), 1.0F);
        camera.viewRotationMatrix.transform(p);
        camera.projectionMatrix.transform(p);
        if (Math.abs(p.w) < 1.0E-6F) {
            return null;
        }
        double x = (p.x / p.w + 1.0) * 0.5 * graphics.guiWidth();
        double y = (1.0 - p.y / p.w) * 0.5 * graphics.guiHeight();
        boolean front = p.w > 0.0F;
        if (!front) {
            x = x < graphics.guiWidth() / 2.0 ? 10000.0 : -10000.0;
        }
        return new Mark(type, x, y, front, point);
    }

    /** Upstream {@code drawRhombus}: arrow heads of the sides in {@code dir} (1 left, 2 down, 4 right, 8 up). */
    private static void rhombus(GuiHudCanvas canvas, int dir, double x, double y, double size, int color) {
        size *= 2.0;
        double m = size / 3.0;
        List<Double> xy = new ArrayList<>();
        if ((dir & 1) != 0) {
            add(xy, x - size, y, x - size + m, y - m, x - size, y, x - size + m, y + m);
        }
        if ((dir & 4) != 0) {
            add(xy, x + size, y, x + size - m, y - m, x + size, y, x + size - m, y + m);
        }
        if ((dir & 8) != 0) {
            add(xy, x, y - size, x + m, y - size + m, x, y - size, x - m, y - size + m);
        }
        if ((dir & 2) != 0) {
            add(xy, x, y + size, x + m, y + size - m, x, y + size, x - m, y + size - m);
        }
        canvas.line(xy.stream().mapToDouble(Double::doubleValue).toArray(), color, HudCanvas.LINES);
    }

    private static void add(List<Double> xy, double... values) {
        for (double v : values) {
            xy.add(v);
        }
    }

    // ---- wrench ----

    /** Upstream {@code MCH_GuiWrench#drawHP}: a bar and percentage under the crosshair. */
    private static void drawWrenchHp(GuiGraphicsExtractor graphics, GuiHudCanvas canvas, int hp, int hpMax) {
        int color = (float) hp / hpMax > 0.3F ? HP_HEALTHY : HP_LOW;
        int posX = graphics.guiWidth() / 2;
        int posY = graphics.guiHeight() / 2 + 20;
        int top = posY + 21;
        int bottom = top + 5;
        int left = posX - 20;
        canvas.rect(left, top, posX + 20, bottom, HP_BACKGROUND);
        hp = Math.min(hp, hpMax);
        float hpp = (float) hp / hpMax;
        canvas.rect(left + 1, top + 1, left + 1 + (int) (38 * hpp), bottom - 1, color);
        int percent = (int) (hpp * 100.0F);
        if (hp < hpMax && percent >= 100) {
            percent = 99;
        }
        canvas.text(String.format("%d %%", percent), posX, posY + 30, color, true);
    }

    // ---- rangefinder ----

    /** Upstream {@code MCH_GuiRangeFinder#drawRF}. */
    private static void drawRangeFinder(GuiGraphicsExtractor graphics, GuiHudCanvas canvas, Minecraft mc,
            LocalPlayer player) {
        int w = graphics.guiWidth();
        int h = graphics.guiHeight();
        double size = 512.0;
        while (size < w || size < h) {
            size *= 2.0;
        }
        // Upstream drew the scope mask in black.
        canvas.tintedTexture("rangefinder", -(size - w) / 2.0, -(size - h) / 2.0, size, size, 0, 0, 1, 1,
                0xFF000000);
        float factor = (float) (size / 512.0);
        graphics.pose().pushMatrix();
        graphics.pose().scale(factor, factor);
        double px = w / (2.0 * factor) - 80.0;
        double py = h / (2.0 * factor) + 55.0;
        ItemStack item = player.getMainHandItem();
        int charge = (int) ((double) (item.getMaxDamage() - item.getDamageValue()) / item.getMaxDamage() * 100.0);
        drawDigits(canvas, String.format("%3d", charge), (int) px, (int) py, 13, charge > 0 ? GREEN : RED);
        if (charge <= 0) {
            canvas.text("Please craft", (int) px + 40, (int) py, 0xFFFF0000, false);
            canvas.text("redstone", (int) px + 40, (int) py + 10, 0xFFFF0000, false);
        }
        px = w / (2.0 * factor) - 20.0;
        if (charge > 0) {
            int range = rangeToView(player);
            if (range >= 0) {
                drawDigits(canvas, String.format("%4d", range), (int) px, (int) py, 13, GREEN);
            } else {
                drawDigits(canvas, "----", (int) px, (int) py, 13, RED);
            }
        }
        py -= 4.0;
        px -= 80.0;
        canvas.rect(px, py, px + 30, py + 2, GREEN);
        canvas.rect(px, py, px + RangeFinderClient.cooldown() / 2, py + 2, RED);
        canvas.text(String.format("x%.1f", RangeFinderClient.zoom()), (int) px, (int) py - 20, WHITE, false);
        px += 130.0;
        int mode = RangeFinderClient.mode();
        canvas.text(">", (int) px, (int) py - 30 + mode * 10, WHITE, false);
        px += 10.0;
        canvas.text("Players/Vehicles", (int) px, (int) py - 30, mode == 0 ? WHITE : GREY, false);
        canvas.text("Monsters/Mobs", (int) px, (int) py - 20, mode == 1 ? WHITE : GREY, false);
        canvas.text("Mark Point", (int) px, (int) py - 10, mode == 2 ? WHITE : GREY, false);
        graphics.pose().popMatrix();
        int hx = (int) Math.max(10.0, w / 2.0 - 160.0 * factor);
        int hy = (int) Math.max(10.0, h / 2.0 - 100.0 * factor);
        float zoom = RangeFinderClient.zoom();
        canvas.text("Spot      : " + keyName(mc.options.keyAttack), hx, hy, WHITE, false);
        canvas.text("Zoom in   : " + keyName(ModKeyMappings.ZOOM), hx, hy + 10, zoom < 10.0F ? WHITE : GREY, false);
        canvas.text("Zoom out : " + keyName(ModKeyMappings.SWITCH_WEAPON_MODE), hx, hy + 20, zoom > 1.2F ? WHITE : GREY,
                false);
        canvas.text("Mode      : " + keyName(ModKeyMappings.FLARE), hx, hy + 30, WHITE, false);
    }

    /**
     * Upstream: the distance from the player's feet to the block (or liquid) in view up to 300
     * blocks away, or -1 if there is none.
     */
    static int rangeToView(Player player) {
        Vec3 eyes = player.getEyePosition();
        Vec3 end = eyes.add(Spotting.lookVector(player.getYRot(), player.getXRot()).scale(300.0));
        BlockHitResult hit = player.level().clip(new ClipContext(eyes, end, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.SOURCE_ONLY, player));
        if (hit.getType() == HitResult.Type.MISS) {
            return -1;
        }
        return (int) player.position().distanceTo(hit.getLocation());
    }

    /** Upstream {@code MCH_Gui#drawDigit}: digits and '-' from {@code digit.png}, 16 pixels each. */
    private static void drawDigits(GuiHudCanvas canvas, String digits, int x, int y, int kerning, int color) {
        for (int i = 0; i < digits.length(); i++) {
            char c = digits.charAt(i);
            int u;
            if (c >= '0' && c <= '9') {
                u = DIGIT_SIZE * (c - '0');
            } else if (c == '-') {
                u = 160;
            } else {
                continue;
            }
            canvas.tintedTexture("digit", x + kerning * i, y, DIGIT_SIZE, DIGIT_SIZE, u / 256.0, 0.0,
                    (u + DIGIT_SIZE) / 256.0, DIGIT_SIZE / 256.0, color);
        }
    }

    private static String keyName(KeyMapping key) {
        return key.getTranslatedKeyMessage().getString();
    }
}
