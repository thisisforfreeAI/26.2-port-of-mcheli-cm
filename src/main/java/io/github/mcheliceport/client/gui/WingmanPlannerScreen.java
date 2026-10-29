package io.github.mcheliceport.client.gui;

import io.github.mcheliceport.net.MissionActionPayload;
import io.github.mcheliceport.net.MissionRouteActionPayload;
import io.github.mcheliceport.net.PlannerDataPayload;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Upstream's {@code GuiWingmanPlanner}, opened by {@code /wingman gui}: the aircraft on the left
 * and the saved planner routes on the right, each list scrolled with the mouse wheel over its half.
 * Assign sends the selected aircraft on the selected route, Abort ends its planner mission, New
 * opens the route editor and Delete removes the route. The port adds Return to Base, which sends the
 * aircraft's mission or base order home to land and park. Upstream's two 230-pixel panels and eight
 * rows did not fit a small window; the port narrows the panels and shows fewer rows there.
 */
public class WingmanPlannerScreen extends Screen {

    private static final int ROW_H = 22;
    private static final int MAX_VISIBLE = 8;
    private static final int MAX_PANEL_W = 230;
    private static final int GAP = 20;

    private final List<PlannerDataPayload.Aircraft> uavs;
    private final List<PlannerDataPayload.Route> routes;
    private final PlannerDataPayload data;

    private int selUav = -1;
    private int selRoute = -1;
    private int uavScroll;
    private int routeScroll;

    private int panelW;
    private int visible;
    private int panelTop;
    private int uavX;
    private int routeX;
    private Button btnAssign;
    private Button btnAbort;
    private Button btnRecall;
    private Button btnDelete;

    public WingmanPlannerScreen(PlannerDataPayload data) {
        super(Component.literal("Wingman Mission Planner"));
        this.data = data;
        this.uavs = data.aircraft();
        this.routes = data.routes();
    }

    @Override
    protected void init() {
        this.panelW = Math.min(MAX_PANEL_W, (this.width - GAP - 24) / 2);
        // Title and labels above, the buttons and three node lines below.
        this.visible = Math.clamp((this.height - 110) / ROW_H, 3, MAX_VISIBLE);
        int listH = this.visible * ROW_H;
        this.uavX = this.width / 2 - (this.panelW * 2 + GAP) / 2;
        this.routeX = this.uavX + this.panelW + GAP;
        this.panelTop = Math.max(26, this.height / 2 - (listH + 50) / 2);
        this.uavScroll = Math.clamp(this.uavScroll, 0, Math.max(0, this.uavs.size() - this.visible));
        this.routeScroll = Math.clamp(this.routeScroll, 0, Math.max(0, this.routes.size() - this.visible));

        int btnY = this.panelTop + listH + 36;
        int btnW = (this.panelW - 5) / 2;
        this.btnAssign = this.addRenderableWidget(Button.builder(Component.literal("Assign Mission"), b -> this.assign())
                .bounds(this.uavX, btnY, btnW, 20).build());
        this.btnAbort = this.addRenderableWidget(Button.builder(Component.literal("Abort Mission"), b -> this.abort())
                .bounds(this.uavX + btnW + 5, btnY, btnW, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("New Route"),
                b -> this.minecraft.gui.setScreen(new NewRouteScreen(this, this.data)))
                .bounds(this.routeX, btnY, btnW, 20).build());
        this.btnDelete = this.addRenderableWidget(Button.builder(Component.literal("Delete Route"), b -> this.delete())
                .bounds(this.routeX + btnW + 5, btnY, btnW, 20).build());
        int closeY = Math.min(btnY + 25, this.height - 22);
        this.btnRecall = this.addRenderableWidget(Button.builder(Component.literal("Return to Base"), b -> this.recall())
                .bounds(this.uavX, closeY, btnW, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Close"), b -> this.onClose())
                .bounds(this.width / 2 - 40, closeY, 80, 20).build());
        this.refreshButtons();
    }

    private void refreshButtons() {
        this.btnAssign.active = this.selUav >= 0 && this.selRoute >= 0;
        this.btnAbort.active = this.selUav >= 0;
        this.btnRecall.active = this.selUav >= 0;
        this.btnDelete.active = this.selRoute >= 0;
    }

    private void assign() {
        if (this.selUav >= 0 && this.selRoute >= 0) {
            ClientPacketDistributor.sendToServer(new MissionActionPayload(MissionActionPayload.ASSIGN,
                    Optional.of(this.uavs.get(this.selUav).uuid()), this.routes.get(this.selRoute).name()));
            this.onClose();
        }
    }

    private void abort() {
        if (this.selUav >= 0) {
            ClientPacketDistributor.sendToServer(new MissionActionPayload(MissionActionPayload.ABORT,
                    Optional.of(this.uavs.get(this.selUav).uuid()), ""));
            this.onClose();
        }
    }

    private void recall() {
        if (this.selUav >= 0) {
            ClientPacketDistributor.sendToServer(new MissionActionPayload(MissionActionPayload.RECALL,
                    Optional.of(this.uavs.get(this.selUav).uuid()), ""));
            this.onClose();
        }
    }

    private void delete() {
        if (this.selRoute >= 0) {
            ClientPacketDistributor.sendToServer(new MissionRouteActionPayload(MissionRouteActionPayload.DELETE,
                    this.routes.get(this.selRoute).name(), List.of()));
            this.onClose();
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (event.button() != 0) {
            return false;
        }
        int hitUav = this.rowAt(event.x(), event.y(), this.uavX, this.uavs.size(), this.uavScroll);
        if (hitUav >= 0) {
            this.selUav = hitUav;
            this.refreshButtons();
            return true;
        }
        int hitRoute = this.rowAt(event.x(), event.y(), this.routeX, this.routes.size(), this.routeScroll);
        if (hitRoute >= 0) {
            this.selRoute = hitRoute;
            this.refreshButtons();
            return true;
        }
        return false;
    }

    private int rowAt(double mx, double my, int x, int size, int scroll) {
        int shown = Math.min(this.visible, size - scroll);
        for (int i = 0; i < shown; i++) {
            int ry = this.panelTop + i * ROW_H;
            if (mx >= x && mx < x + this.panelW && my >= ry && my < ry + ROW_H - 1) {
                return i + scroll;
            }
        }
        return -1;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (scrollY == 0.0) {
            return false;
        }
        int delta = scrollY > 0 ? -1 : 1;
        if (x < this.width / 2.0) {
            this.uavScroll = Math.clamp(this.uavScroll + delta, 0, Math.max(0, this.uavs.size() - this.visible));
        } else {
            this.routeScroll = Math.clamp(this.routeScroll + delta, 0, Math.max(0, this.routes.size() - this.visible));
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.centeredText(this.font, "§eWingman Mission Planner", this.width / 2, this.panelTop - 22,
                0xFFFFFFFF);
        graphics.text(this.font, "§bAircraft (" + this.uavs.size() + ")", this.uavX, this.panelTop - 11,
                0xFFFFFFFF);
        this.drawPanel(graphics, this.uavX, this.uavScroll, this.selUav, true, mouseX, mouseY);
        graphics.text(this.font, "§aRoutes (" + this.routes.size() + ")", this.routeX, this.panelTop - 11,
                0xFFFFFFFF);
        this.drawPanel(graphics, this.routeX, this.routeScroll, this.selRoute, false, mouseX, mouseY);
        if (this.selRoute >= 0 && this.selRoute < this.routes.size()) {
            List<String> nodes = this.routes.get(this.selRoute).nodes();
            int dy = this.panelTop + this.visible * ROW_H + 4;
            for (int i = 0; i < Math.min(2, nodes.size()); i++) {
                graphics.text(this.font, this.fit("§8[" + i + "] " + nodes.get(i)), this.routeX, dy + i * 10,
                        0xFFAAAAAA);
            }
            if (nodes.size() > 2) {
                graphics.text(this.font, "§8... (" + nodes.size() + " total)", this.routeX, dy + 20, 0xFF888888);
            }
        }
        super.extractRenderState(graphics, mouseX, mouseY, a);
    }

    private void drawPanel(GuiGraphicsExtractor graphics, int x, int scroll, int sel, boolean isUav, int mx, int my) {
        int size = isUav ? this.uavs.size() : this.routes.size();
        int top = this.panelTop;
        graphics.fill(x - 2, top - 2, x + this.panelW + 2, top + this.visible * ROW_H + 2, 0x88000000);
        int shown = Math.min(this.visible, size - scroll);
        for (int i = 0; i < shown; i++) {
            int idx = i + scroll;
            int ry = top + i * ROW_H;
            boolean hov = mx >= x && mx < x + this.panelW && my >= ry && my < ry + ROW_H - 1;
            boolean isSel = idx == sel;
            int bg = isSel ? 0xAA005599 : hov ? 0x44AAAAAA : 0;
            if (bg != 0) {
                graphics.fill(x, ry, x + this.panelW, ry + ROW_H - 1, bg);
            }
            if (isUav) {
                PlannerDataPayload.Aircraft d = this.uavs.get(idx);
                String label = (isSel ? "§e" : "§f") + d.name() + " §7[" + d.state() + "]";
                if (d.nodeCount() > 0) {
                    label += " §a" + d.nodeIdx() + "/" + d.nodeCount();
                }
                graphics.text(this.font, this.fit(label), x + 4, ry + 3, 0xFFFFFFFF);
                graphics.text(this.font, "§8" + d.uuid().toString().substring(0, 8) + "...", x + 4, ry + 12,
                        0xFF777777);
            } else {
                PlannerDataPayload.Route d = this.routes.get(idx);
                graphics.text(this.font, this.fit((isSel ? "§e" : "§f") + d.name()), x + 4, ry + 3,
                        0xFFFFFFFF);
                graphics.text(this.font, "§8" + d.nodes().size() + " nodes", x + 4, ry + 12, 0xFF777777);
            }
        }
        if (size > this.visible) {
            int trackH = this.visible * ROW_H;
            int thumbH = Math.max(10, trackH * this.visible / size);
            int thumbY = top + trackH * scroll / size;
            graphics.fill(x + this.panelW + 3, top, x + this.panelW + 7, top + trackH, 0x44FFFFFF);
            graphics.fill(x + this.panelW + 3, thumbY, x + this.panelW + 7, thumbY + thumbH, 0xAAFFFFFF);
        }
    }

    /** The text cut to the panel's width. */
    private String fit(String s) {
        int max = this.panelW - 8;
        if (this.font.width(s) <= max) {
            return s;
        }
        return this.font.plainSubstrByWidth(s, max - this.font.width("...")) + "...";
    }
}
