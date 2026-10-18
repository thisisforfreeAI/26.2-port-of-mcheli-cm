package io.github.mcheliceport.client.gui;

import io.github.mcheliceport.net.MissionRouteActionPayload;
import io.github.mcheliceport.net.PlannerDataPayload;
import io.github.mcheliceport.wingman.mission.MissionNode;
import io.github.mcheliceport.wingman.mission.MissionPlan;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Upstream's {@code GuiNewRoute}, the mission planner's route editor: a route name, then nodes added
 * one at a time. A type button picks the node; a take-off, landing or parking node takes a marker
 * ID, a fly-to node a point (My Pos fills in where the player stood, a waypoint ID its position),
 * an attack node a radius and a loiter node a time. The markers of the node's type are listed to
 * click, and the only one is filled in. The last five nodes are listed with a delete mark.
 *
 * <p>Upstream laid the screen out for a large window; the port fits it into 240 pixels, with Add,
 * Save and Cancel on one row. Take-off and landing also list the bases, whose name finds both
 * runway ends (KNOWN_ISSUES 100).
 */
public class NewRouteScreen extends Screen {

    private static final int T_TAKEOFF = 0;
    private static final int T_FLYTO = 1;
    private static final int T_ATTACK = 2;
    private static final int T_LOITER = 3;
    private static final int T_LAND = 4;
    private static final int T_PARK = 5;
    private static final String[] TYPE_LABELS = {"Takeoff", "Fly To", "Attack", "Loiter", "Land", "Park"};
    private static final String[] MARKER_TYPE = {"RUNWAY_A", "WAYPOINT", null, null, "RUNWAY_B", "PARKING"};
    private static final int SHOWN_NODES = 5;

    private final Screen parent;
    private final PlannerDataPayload data;
    private final List<String> nodes = new ArrayList<>();
    private int selectedType = -1;
    private String errorMsg = "";
    private String nameText = "";

    private EditBox nameField;
    private EditBox idField;
    private EditBox xField;
    private EditBox yField;
    private EditBox zField;
    private EditBox numField;
    private final Button[] typeBtns = new Button[TYPE_LABELS.length];
    private Button btnMyPos;
    private Button btnAdd;

    private int cx;
    private int top;

    public NewRouteScreen(Screen parent, PlannerDataPayload data) {
        super(Component.literal("New Route"));
        this.parent = parent;
        this.data = data;
    }

    private int formY() {
        return this.top + 90;
    }

    private int hintY() {
        return this.top + 114;
    }

    private int listY() {
        return this.top + 162;
    }

    @Override
    protected void init() {
        this.cx = this.width / 2;
        this.top = Math.max(2, this.height / 2 - 120);
        this.nameField = this.field(this.cx - 120, this.top + 22, 240, MissionPlan.MAX_NAME_LENGTH, this.nameText);
        int gap = 3;
        int bw = Math.min(72, (this.width - 12 - gap * 5) / 6);
        int startX = this.cx - (bw * 6 + gap * 5) / 2;
        for (int i = 0; i < TYPE_LABELS.length; i++) {
            int type = i;
            this.typeBtns[i] = this.addRenderableWidget(Button.builder(Component.literal(TYPE_LABELS[i]),
                    b -> this.selectType(type)).bounds(startX + i * (bw + gap), this.top + 56, bw, 18).build());
        }
        int fy = this.formY();
        this.idField = this.field(this.cx - 120, fy, 200, 64, "");
        this.xField = this.field(this.cx - 120, fy, 55, 8, "");
        this.yField = this.field(this.cx - 58, fy, 55, 8, "");
        this.zField = this.field(this.cx + 4, fy, 55, 8, "");
        this.numField = this.field(this.cx - 60, fy, 120, 10, "");
        this.btnMyPos = this.addRenderableWidget(Button.builder(Component.literal("My Pos"), b -> {
            this.xField.setValue(String.valueOf((int) this.data.playerX()));
            this.yField.setValue(String.valueOf((int) this.data.playerY()));
            this.zField.setValue(String.valueOf((int) this.data.playerZ()));
        }).bounds(this.cx + 68, fy, 55, 20).build());
        int by = this.top + 126;
        this.btnAdd = this.addRenderableWidget(Button.builder(Component.literal("+ Add"), b -> this.addNode())
                .bounds(this.cx - 120, by, 60, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal("Save Route"), b -> this.save())
                .bounds(this.cx - 20, by, 75, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> this.minecraft.gui.setScreen(this.parent))
                .bounds(this.cx + 60, by, 60, 18).build());
        this.refreshVisibility();
    }

    private EditBox field(int x, int y, int w, int maxLength, String value) {
        EditBox f = new EditBox(this.font, x, y, w, 20, Component.empty());
        f.setMaxLength(maxLength);
        f.setValue(value);
        return this.addRenderableWidget(f);
    }

    @Override
    protected void setInitialFocus() {
        this.setInitialFocus(this.nameField);
    }

    private void refreshVisibility() {
        int t = this.selectedType;
        this.idField.visible = t == T_TAKEOFF || t == T_LAND || t == T_PARK;
        this.xField.visible = t == T_FLYTO;
        this.yField.visible = t == T_FLYTO;
        this.zField.visible = t == T_FLYTO;
        this.numField.visible = t == T_ATTACK || t == T_LOITER;
        this.btnMyPos.visible = t == T_FLYTO;
        this.btnAdd.active = t >= 0;
    }

    /** Upstream: a new type clears the form and fills in the only marker of its type. */
    private void selectType(int type) {
        if (type != this.selectedType) {
            this.selectedType = type;
            this.clearForm();
            String markerType = MARKER_TYPE[type];
            if (markerType != null) {
                List<PlannerDataPayload.Marker> list = this.markersOfType(markerType);
                if (list.size() == 1) {
                    if (type == T_FLYTO) {
                        this.fillPoint(list.getFirst());
                    } else {
                        this.idField.setValue(list.getFirst().id());
                    }
                }
            }
        }
        this.refreshVisibility();
    }

    private void fillPoint(PlannerDataPayload.Marker m) {
        this.xField.setValue(String.valueOf(m.pos().getX()));
        this.yField.setValue(String.valueOf(m.pos().getY()));
        this.zField.setValue(String.valueOf(m.pos().getZ()));
    }

    private void clearForm() {
        this.idField.setValue("");
        this.numField.setValue("");
        this.xField.setValue("");
        this.yField.setValue("");
        this.zField.setValue("");
        this.errorMsg = "";
    }

    private void addNode() {
        String node = this.buildNode();
        if (node == null) {
            return;
        }
        if (this.nodes.size() >= MissionPlan.MAX_NODES) {
            this.errorMsg = "A route takes at most " + MissionPlan.MAX_NODES + " nodes.";
            return;
        }
        this.nodes.add(node);
        this.errorMsg = "";
    }

    /** Upstream {@code buildNode}: the node's text, or null with the error set. */
    private @Nullable String buildNode() {
        String text = switch (this.selectedType) {
            case T_TAKEOFF -> this.idNode("takeoff:", "Runway-A ID required.");
            case T_FLYTO -> "flyto:" + this.xField.getValue().trim() + "," + this.yField.getValue().trim() + ","
                    + this.zField.getValue().trim();
            case T_ATTACK -> "attack:" + (this.numField.getValue().isBlank() ? "200" : this.numField.getValue().trim());
            case T_LOITER -> "loiter:" + (this.numField.getValue().isBlank() ? "200" : this.numField.getValue().trim());
            case T_LAND -> this.idNode("land:", "Runway-B ID required.");
            case T_PARK -> this.idNode("park:", "Parking ID required.");
            default -> null;
        };
        if (text == null) {
            return null;
        }
        try {
            // Upstream wrote fly-to points, radii and times as whole numbers.
            if (this.selectedType == T_FLYTO || this.selectedType == T_ATTACK || this.selectedType == T_LOITER) {
                for (String part : text.substring(text.indexOf(':') + 1).split(",")) {
                    Integer.parseInt(part);
                }
            }
            return MissionNode.parse(text).serialize();
        } catch (IllegalArgumentException e) {
            this.errorMsg = "Invalid number.";
            return null;
        }
    }

    private @Nullable String idNode(String prefix, String missing) {
        String v = this.idField.getValue().trim();
        if (v.isEmpty()) {
            this.errorMsg = missing;
            return null;
        }
        return prefix + v;
    }

    private void save() {
        String name = this.nameField.getValue().trim();
        if (name.isEmpty()) {
            this.errorMsg = "Route name required.";
            return;
        }
        if (!MissionPlan.isValidName(name)) {
            this.errorMsg = "Route names have no spaces.";
            return;
        }
        if (this.nodes.isEmpty()) {
            this.errorMsg = "Add at least one node.";
            return;
        }
        for (PlannerDataPayload.Route r : this.data.routes()) {
            if (r.name().equals(name)) {
                this.errorMsg = "Route '" + name + "' already exists.";
                return;
            }
        }
        ClientPacketDistributor.sendToServer(new MissionRouteActionPayload(MissionRouteActionPayload.CREATE, name,
                List.copyOf(this.nodes)));
        this.onClose();
    }

    /** Upstream's hint markers of a type; take-off and landing also offer the bases. */
    private List<PlannerDataPayload.Marker> hintMarkers() {
        String type = this.selectedType >= 0 ? MARKER_TYPE[this.selectedType] : null;
        if (type == null) {
            return List.of();
        }
        List<PlannerDataPayload.Marker> list = new ArrayList<>(this.markersOfType(type));
        if (this.selectedType == T_TAKEOFF || this.selectedType == T_LAND) {
            list.addAll(this.markersOfType("BASE"));
        }
        return list;
    }

    private List<PlannerDataPayload.Marker> markersOfType(String type) {
        List<PlannerDataPayload.Marker> result = new ArrayList<>();
        for (PlannerDataPayload.Marker m : this.data.markers()) {
            if (type.equals(m.type())) {
                result.add(m);
            }
        }
        return result;
    }

    private static String hintLabel(PlannerDataPayload.Marker m) {
        return m.id().isEmpty() ? "(no-id)" : m.id();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (event.button() != 0) {
            return false;
        }
        double mx = event.x();
        double my = event.y();
        int hy = this.hintY();
        if (my >= hy && my < hy + 9) {
            int rx = this.cx - 120 + this.font.width("Quick: ");
            for (PlannerDataPayload.Marker m : this.hintMarkers()) {
                String label = hintLabel(m);
                int w = this.font.width(label);
                if (mx >= rx && mx < rx + w) {
                    if (this.selectedType == T_FLYTO) {
                        this.fillPoint(m);
                    } else {
                        this.idField.setValue(label);
                        this.setFocused(this.idField);
                    }
                    return true;
                }
                rx += w + this.font.width(" | ");
            }
        }
        int listY = this.listY();
        int shown = Math.min(this.nodes.size(), SHOWN_NODES);
        int start = this.nodes.size() - shown;
        for (int i = 0; i < shown; i++) {
            int ry = listY + i * 12;
            if (mx >= this.cx + 110 && mx < this.cx + 125 && my >= ry && my < ry + 9) {
                this.nodes.remove(start + i);
                return true;
            }
        }
        return false;
    }

    /** Upstream: Escape returns to the planner, Tab moves through X, Y and Z. */
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            this.minecraft.gui.setScreen(this.parent);
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_TAB && this.selectedType == T_FLYTO) {
            EditBox next = this.xField.isFocused() ? this.yField : this.yField.isFocused() ? this.zField : this.xField;
            this.setFocused(next);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void resize(int width, int height) {
        this.nameText = this.nameField.getValue();
        String id = this.idField.getValue();
        String x = this.xField.getValue();
        String y = this.yField.getValue();
        String z = this.zField.getValue();
        String num = this.numField.getValue();
        super.resize(width, height);
        this.idField.setValue(id);
        this.xField.setValue(x);
        this.yField.setValue(y);
        this.zField.setValue(z);
        this.numField.setValue(num);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---- drawing ----

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.centeredText(this.font, "§eNew Route", this.cx, this.top, 0xFFFFFFFF);
        this.label(graphics, "Route name:", this.cx - 120, this.top + 12);
        this.label(graphics, "Add node:", this.cx - 120, this.top + 46);
        if (this.selectedType >= 0) {
            Button sel = this.typeBtns[this.selectedType];
            graphics.fill(sel.getX() - 1, sel.getY() - 1, sel.getX() + sel.getWidth() + 1,
                    sel.getY() + sel.getHeight() + 1, 0xFFFFAA00);
            this.drawForm(graphics, mouseX, mouseY);
        }
        this.drawNodeList(graphics, mouseX, mouseY);
        if (!this.errorMsg.isEmpty()) {
            graphics.centeredText(this.font, "§c" + this.errorMsg, this.cx, this.listY() + SHOWN_NODES * 12 + 2,
                    0xFFFFFFFF);
        }
        super.extractRenderState(graphics, mouseX, mouseY, a);
    }

    private void label(GuiGraphicsExtractor graphics, String text, int x, int y) {
        graphics.text(this.font, text, x, y, 0xFFAAAAAA);
    }

    private void drawForm(GuiGraphicsExtractor graphics, int mx, int my) {
        int labelY = this.formY() - 10;
        switch (this.selectedType) {
            case T_TAKEOFF -> this.label(graphics, "Runway-A marker ID (or base):", this.cx - 120, labelY);
            case T_FLYTO -> {
                this.label(graphics, "X:", this.cx - 122, labelY);
                this.label(graphics, "Y:", this.cx - 60, labelY);
                this.label(graphics, "Z:", this.cx + 2, labelY);
            }
            case T_ATTACK -> this.label(graphics, "Attack radius (blocks):", this.cx - 120, labelY);
            case T_LOITER -> this.label(graphics, "Duration (ticks, 20=1s):", this.cx - 120, labelY);
            case T_LAND -> this.label(graphics, "Runway-B marker ID (or base):", this.cx - 120, labelY);
            case T_PARK -> this.label(graphics, "Parking marker ID:", this.cx - 120, labelY);
            default -> {
            }
        }
        String type = MARKER_TYPE[this.selectedType];
        if (type != null) {
            this.drawMarkerHints(graphics, type, mx, my);
        }
    }

    /** Upstream {@code drawMarkerHints}: the marker IDs to click, as far as the line reaches. */
    private void drawMarkerHints(GuiGraphicsExtractor graphics, String type, int mx, int my) {
        int hy = this.hintY();
        List<PlannerDataPayload.Marker> list = this.hintMarkers();
        if (list.isEmpty()) {
            graphics.text(this.font, "§8No " + type + " markers placed yet", this.cx - 120, hy, 0xFF666666);
            return;
        }
        int rx = this.cx - 120;
        int right = Math.min(this.width - 4, this.cx + 200);
        graphics.text(this.font, "§8Quick: ", rx, hy, 0xFF666666);
        rx += this.font.width("Quick: ");
        boolean first = true;
        for (PlannerDataPayload.Marker m : list) {
            String label = hintLabel(m);
            int w = this.font.width(label);
            if (!first) {
                graphics.text(this.font, "§8|", rx - this.font.width("| "), hy, 0xFFFFFFFF);
            }
            if (rx + w > right) {
                graphics.text(this.font, "§8...", rx, hy, 0xFFFFFFFF);
                break;
            }
            boolean hov = mx >= rx && mx < rx + w && my >= hy && my < hy + 9;
            graphics.text(this.font, (hov ? "§e" : "§b") + label, rx, hy, 0xFFFFFFFF);
            rx += w + this.font.width(" | ");
            first = false;
        }
    }

    private void drawNodeList(GuiGraphicsExtractor graphics, int mx, int my) {
        int listY = this.listY();
        this.label(graphics, "§7Nodes (" + this.nodes.size() + "):", this.cx - 120, listY - 12);
        if (this.nodes.isEmpty()) {
            graphics.text(this.font, "§8(none yet)", this.cx - 120, listY, 0xFF666666);
            return;
        }
        int shown = Math.min(this.nodes.size(), SHOWN_NODES);
        int start = this.nodes.size() - shown;
        for (int i = 0; i < shown; i++) {
            int ry = listY + i * 12;
            graphics.text(this.font, "§7[" + (start + i) + "] §f" + this.nodes.get(start + i), this.cx - 120, ry,
                    0xFFFFFFFF);
            boolean hov = mx >= this.cx + 110 && mx < this.cx + 125 && my >= ry && my < ry + 9;
            graphics.text(this.font, hov ? "§c[×]" : "§8[×]", this.cx + 110, ry, 0xFFFFFFFF);
        }
    }
}
