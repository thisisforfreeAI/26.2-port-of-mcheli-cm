package io.github.mcheliceport.client.gui;

import io.github.mcheliceport.net.WingmanPanelActionPayload;
import io.github.mcheliceport.net.WingmanPanelDataPayload;
import io.github.mcheliceport.net.WingmanPanelOpenPayload;
import io.github.mcheliceport.wingman.wingman.WingmanEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Upstream's {@code GuiWingmanPanel}, opened with the wingman panel key while piloting. The Wingmen
 * tab lists the unassigned aircraft nearby, each with Follow, and the player's wingmen, each with
 * Auto, Hold, Stop and a weapon type; the Formation tab changes the formation and attack altitude
 * limits with [-] and [+] buttons, so no key the pilot flies with is taken by a text field.
 */
public class WingmanPanelScreen extends Screen {

    private static final int MAX_NEARBY = 8;
    private static final int MAX_WINGMEN = 8;
    /** One wingman: the name line, a gap, the button row and the row spacing. */
    private static final int WM_ROW_H = 32;
    private static final int WM_TOP = 18;
    private static final int WINGMEN_CONTENT_H = WM_TOP + MAX_WINGMEN * WM_ROW_H + 8;
    private static final String[] WEAPON_TYPES = {"", "gun", "aamissile", "asmissile", "cas", "rocket", "bomb"};
    private static final double STEP_DIST = 5.0;
    private static final double STEP_ALT = 10.0;

    private int currentTab;
    private final List<WingmanPanelDataPayload.Aircraft> nearby;
    private final List<WingmanPanelDataPayload.Wingman> wingmen;
    private final List<Integer> weaponIdx = new ArrayList<>();
    private final List<Integer> attackModes = new ArrayList<>();
    private int scrollWingmen;
    private double fSideDist;
    private double fAltOffset;
    private double fRearDist;
    private int fMaxWings;
    private double fMinAlt;
    private double fMaxAlt;
    private Button wingmenTab;
    private Button formationTab;

    public WingmanPanelScreen(WingmanPanelDataPayload data) {
        super(Component.literal("Wingman Panel"));
        this.nearby = new ArrayList<>(data.nearby());
        this.wingmen = new ArrayList<>(data.wingmen());
        this.fSideDist = data.sideDist();
        this.fAltOffset = data.altOffset();
        this.fRearDist = data.rearDist();
        this.fMaxWings = data.maxWings();
        this.fMinAlt = data.minAlt();
        this.fMaxAlt = data.maxAlt();
        for (WingmanPanelDataPayload.Wingman wm : this.wingmen) {
            this.weaponIdx.add(findWeaponIdx(wm.weaponType()));
            this.attackModes.add(wm.attackMode());
        }
    }

    private static int findWeaponIdx(String wt) {
        for (int i = 0; i < WEAPON_TYPES.length; i++) {
            if (WEAPON_TYPES[i].equals(wt)) {
                return i;
            }
        }
        return 0;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int ty = this.height / 4;
        this.wingmenTab = this.addRenderableWidget(Button.builder(Component.literal("Wingmen"), b -> {
            if (this.currentTab != 0) {
                this.currentTab = 0;
                this.scrollWingmen = 0;
                this.rebuildWidgets();
            }
        }).bounds(cx - 82, ty - 14, 80, 16).build());
        this.formationTab = this.addRenderableWidget(Button.builder(Component.literal("Formation"), b -> {
            if (this.currentTab != 1) {
                this.currentTab = 1;
                this.rebuildWidgets();
            }
        }).bounds(cx + 2, ty - 14, 80, 16).build());
        this.addRenderableWidget(Button.builder(Component.literal("Refresh"),
                b -> ClientPacketDistributor.sendToServer(WingmanPanelOpenPayload.INSTANCE))
                .bounds(cx - 82, this.height - 28, 55, 14).build());
        this.addRenderableWidget(Button.builder(Component.literal("Close"), b -> this.onClose())
                .bounds(cx + 30, this.height - 28, 52, 14).build());
        if (this.currentTab == 0) {
            this.initWingmenTab(cx, ty);
        } else {
            this.initFormationTab(cx, ty);
        }
    }

    private void initWingmenTab(int cx, int ty) {
        int sty = ty - this.scrollWingmen;
        int clipTop = ty;
        int clipBot = this.height - 28;
        int lx = cx - 190;
        for (int i = 0; i < Math.min(this.nearby.size(), MAX_NEARBY); i++) {
            int idx = i;
            this.clipped(Button.builder(Component.literal("Follow"), b -> this.follow(idx))
                    .bounds(lx + 90, sty + 8 + i * 18, 52, 14).build(), clipTop, clipBot);
        }
        int rx = cx - 30;
        for (int i = 0; i < Math.min(this.wingmen.size(), MAX_WINGMEN); i++) {
            int idx = i;
            int by = sty + WM_TOP + i * WM_ROW_H + 11;
            boolean isAuto = this.attackModes.get(i) == WingmanEntry.ATK_AUTO;
            this.clipped(Button.builder(Component.literal(isAuto ? "§2Auto" : "Auto"),
                    b -> this.attack(idx, WingmanPanelActionPayload.AUTO, WingmanEntry.ATK_AUTO))
                    .bounds(rx, by, 34, 14).build(), clipTop, clipBot);
            this.clipped(Button.builder(Component.literal("Hold"),
                    b -> this.attack(idx, WingmanPanelActionPayload.HOLD, WingmanEntry.ATK_NONE))
                    .bounds(rx + 36, by, 32, 14).build(), clipTop, clipBot);
            this.clipped(Button.builder(Component.literal("§cStop"), b -> this.stop(idx))
                    .bounds(rx + 70, by, 30, 14).build(), clipTop, clipBot);
            this.clipped(Button.builder(Component.literal("<"), b -> this.cycleWeapon(idx, -1))
                    .bounds(rx + 104, by, 16, 14).build(), clipTop, clipBot);
            this.clipped(Button.builder(Component.literal(">"), b -> this.cycleWeapon(idx, 1))
                    .bounds(rx + 156, by, 16, 14).build(), clipTop, clipBot);
        }
    }

    /** Upstream hid the buttons scrolled out of the list area. */
    private void clipped(Button b, int clipTop, int clipBot) {
        b.visible = b.getY() + b.getHeight() > clipTop && b.getY() < clipBot;
        this.addRenderableWidget(b);
    }

    private void initFormationTab(int cx, int ty0) {
        int ty = ty0 + 10;
        int rowH = 22;
        this.spinner(cx, ty, () -> this.fSideDist = Math.max(0, this.fSideDist - STEP_DIST),
                () -> this.fSideDist += STEP_DIST);
        this.spinner(cx, ty + rowH, () -> this.fAltOffset -= STEP_DIST, () -> this.fAltOffset += STEP_DIST);
        this.spinner(cx, ty + rowH * 2, () -> this.fRearDist = Math.max(0, this.fRearDist - STEP_DIST),
                () -> this.fRearDist += STEP_DIST);
        this.spinner(cx, ty + rowH * 3, () -> this.fMaxWings = Math.max(1, this.fMaxWings - 1),
                () -> this.fMaxWings = Math.min(64, this.fMaxWings + 1));
        this.spinner(cx, ty + rowH * 4 + 8, () -> this.fMinAlt = Math.max(0, this.fMinAlt - STEP_ALT),
                () -> this.fMinAlt += STEP_ALT);
        this.spinner(cx, ty + rowH * 5 + 8, () -> this.fMaxAlt = Math.max(0, this.fMaxAlt - STEP_ALT),
                () -> this.fMaxAlt += STEP_ALT);
        // Upstream's place for Apply falls below the window on short screens; it then joins Refresh and Close.
        int applyY = ty + rowH * 7 + 10;
        Button.Builder apply = Button.builder(Component.literal("Apply"), b -> this.sendFormation());
        this.addRenderableWidget(applyY + 16 <= this.height - 30 ? apply.bounds(cx - 30, applyY, 60, 16).build()
                : apply.bounds(cx - 25, this.height - 28, 53, 14).build());
    }

    private void spinner(int cx, int y, Runnable dec, Runnable inc) {
        this.addRenderableWidget(Button.builder(Component.literal("-"), b -> dec.run()).bounds(cx - 50, y, 18, 14)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal("+"), b -> inc.run()).bounds(cx + 32, y, 18, 14)
                .build());
    }

    // ---- actions ----

    private void follow(int idx) {
        if (idx >= this.nearby.size()) {
            return;
        }
        ClientPacketDistributor.sendToServer(WingmanPanelActionPayload.of(WingmanPanelActionPayload.FOLLOW,
                this.nearby.get(idx).uuid(), ""));
        this.nearby.remove(idx);
        this.rebuildWidgets();
    }

    private void attack(int idx, int action, int mode) {
        ClientPacketDistributor.sendToServer(WingmanPanelActionPayload.of(action, this.wingmen.get(idx).uuid(), ""));
        this.attackModes.set(idx, mode);
        this.rebuildWidgets();
    }

    private void stop(int idx) {
        ClientPacketDistributor.sendToServer(WingmanPanelActionPayload.of(WingmanPanelActionPayload.STOP,
                this.wingmen.get(idx).uuid(), ""));
        this.wingmen.remove(idx);
        this.weaponIdx.remove(idx);
        this.attackModes.remove(idx);
        this.rebuildWidgets();
    }

    private void cycleWeapon(int idx, int dir) {
        int next = Math.floorMod(this.weaponIdx.get(idx) + dir, WEAPON_TYPES.length);
        this.weaponIdx.set(idx, next);
        ClientPacketDistributor.sendToServer(WingmanPanelActionPayload.of(WingmanPanelActionPayload.WEAPON,
                this.wingmen.get(idx).uuid(), WEAPON_TYPES[next]));
    }

    private void sendFormation() {
        ClientPacketDistributor.sendToServer(new WingmanPanelActionPayload(WingmanPanelActionPayload.FORMATION,
                Optional.empty(), "", this.fSideDist, this.fAltOffset, this.fRearDist, this.fMaxWings, this.fMinAlt,
                this.fMaxAlt));
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (scrollY == 0.0 || this.currentTab != 0) {
            return false;
        }
        int visH = this.height - 28 - this.height / 4;
        int maxScroll = Math.max(0, WINGMEN_CONTENT_H - visH);
        this.scrollWingmen = Math.clamp(this.scrollWingmen + (scrollY > 0.0 ? -14 : 14), 0, maxScroll);
        this.rebuildWidgets();
        return true;
    }

    /** Upstream passed no keys on but Escape, which closes the panel. */
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---- drawing ----

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int cx = this.width / 2;
        int ty = this.height / 4;
        graphics.centeredText(this.font, "§eWingman Panel", cx, ty - 26, 0xFFFFFFFF);
        Button tab = this.currentTab == 0 ? this.wingmenTab : this.formationTab;
        graphics.fill(tab.getX(), tab.getY() + tab.getHeight(), tab.getX() + tab.getWidth(),
                tab.getY() + tab.getHeight() + 2, 0xFFFFAA00);
        if (this.currentTab == 0) {
            this.drawWingmenTab(graphics, cx, ty);
        } else {
            this.drawFormationTab(graphics, cx, ty);
        }
        super.extractRenderState(graphics, mouseX, mouseY, a);
    }

    private void label(GuiGraphicsExtractor graphics, String text, int x, int y, int rgb) {
        graphics.text(this.font, text, x, y, 0xFF000000 | rgb);
    }

    private void drawWingmenTab(GuiGraphicsExtractor graphics, int cx, int ty) {
        int clipTop = ty;
        int clipBot = this.height - 28;
        graphics.enableScissor(0, clipTop, this.width, clipBot);
        int sty = ty - this.scrollWingmen;
        int lx = cx - 190;
        int ry = sty + 8;
        this.label(graphics, "§7─ Nearby (unassigned) ─", lx, sty + 2, 0x888888);
        for (int i = 0; i < Math.min(this.nearby.size(), MAX_NEARBY); i++) {
            WingmanPanelDataPayload.Aircraft ac = this.nearby.get(i);
            this.label(graphics, ac.name() + " §7" + shortUuid(ac.uuid().toString()), lx, ry + i * 18 + 3, 0xFFFFFF);
        }
        if (this.nearby.isEmpty()) {
            this.label(graphics, "§7(none nearby)", lx, ry + 3, 0x888888);
        }
        int rx = cx - 30;
        this.label(graphics, "§7─ Your Wingmen ─", rx, sty + 2, 0x888888);
        for (int i = 0; i < Math.min(this.wingmen.size(), MAX_WINGMEN); i++) {
            WingmanPanelDataPayload.Wingman wm = this.wingmen.get(i);
            int rowY = sty + WM_TOP + i * WM_ROW_H;
            this.label(graphics, "§f" + wm.name() + " §7#" + wm.slot() + " §8[" + wm.state() + "]", rx, rowY, 0xFFFFFF);
            int wpnLabelX = rx + 104 + 16 + (156 - (104 + 16)) / 2;
            graphics.centeredText(this.font, "§e" + weaponLabel(this.weaponIdx.get(i)), wpnLabelX, rowY + 14,
                    0xFFFFFFFF);
        }
        if (this.wingmen.isEmpty()) {
            this.label(graphics, "§7(no wingmen assigned)", rx, ry + 3, 0x888888);
        }
        graphics.disableScissor();
        this.drawScrollbar(graphics, clipTop, clipBot);
    }

    private static String weaponLabel(int idx) {
        String t = WEAPON_TYPES[idx];
        return t.isEmpty() ? "Any" : t;
    }

    private static String shortUuid(String uuid) {
        return uuid.length() >= 8 ? uuid.substring(0, 8) + "..." : uuid;
    }

    private void drawFormationTab(GuiGraphicsExtractor graphics, int cx, int ty) {
        int labelX = cx - 140;
        int valueX = cx - 1;
        int rowH = 22;
        int baseY = ty + 10;
        this.label(graphics, "§7─ Formation ─", labelX, baseY - 8, 0x888888);
        this.row(graphics, labelX, valueX, baseY, "Side dist:", fmt(this.fSideDist));
        this.row(graphics, labelX, valueX, baseY + rowH, "Alt offset:", fmt(this.fAltOffset));
        this.row(graphics, labelX, valueX, baseY + rowH * 2, "Rear dist:", fmt(this.fRearDist));
        this.row(graphics, labelX, valueX, baseY + rowH * 3, "Max wingmen:", Integer.toString(this.fMaxWings));
        this.label(graphics, "§7─ Attack Altitude ─", labelX, baseY + rowH * 4 + 2, 0x888888);
        this.row(graphics, labelX, valueX, baseY + rowH * 4 + 8, "Min alt:", this.fMinAlt == 0 ? "off" : fmt(this.fMinAlt));
        this.row(graphics, labelX, valueX, baseY + rowH * 5 + 8, "Max alt:", this.fMaxAlt == 0 ? "off" : fmt(this.fMaxAlt));
        this.label(graphics, "§7(0 = no limit)", labelX, baseY + rowH * 6 + 4, 0x666666);
    }

    private void row(GuiGraphicsExtractor graphics, int lx, int vx, int y, String label, String value) {
        this.label(graphics, "§7" + label, lx, y + 3, 0xAAAAAA);
        graphics.centeredText(this.font, "§f" + value, vx, y + 3, 0xFFFFFFFF);
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    private void drawScrollbar(GuiGraphicsExtractor graphics, int top, int bot) {
        int visH = bot - top;
        int maxScroll = Math.max(0, WINGMEN_CONTENT_H - visH);
        if (maxScroll <= 0) {
            return;
        }
        int bx = this.width - 7;
        int barH = Math.max(16, visH * visH / WINGMEN_CONTENT_H);
        int barY = top + (int) ((long) this.scrollWingmen * (visH - barH) / maxScroll);
        graphics.fill(bx, top, bx + 5, bot, 0x44FFFFFF);
        graphics.fill(bx, barY, bx + 5, barY + barH, 0xAAFFFFFF);
    }
}
