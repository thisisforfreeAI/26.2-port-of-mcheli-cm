package io.github.mcheliceport.client.gui;

import io.github.mcheliceport.net.OpenBaseScreenPayload;
import io.github.mcheliceport.net.TaxiRouteActionPayload;
import io.github.mcheliceport.wingman.mission.TaxiRoute;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Upstream's {@code GuiBaseConfig}, opened by right-clicking a base marker, on its Taxi Routes tab:
 * the base's routes on the left (New, Save, Delete), and on the right the route ID and buttons
 * picking its parking spot, runway ends or helipad, then its departure or arrival waypoints in
 * order. The form scrolls with the mouse wheel. Upstream's labels, including its Japanese ones,
 * are kept.
 *
 * <p>Upstream's Mission tab dispatches wingmen, which are not ported yet; its tab button is shown
 * but cannot be selected.
 */
public class BaseConfigScreen extends Screen {

    private static final int MAX_ROUTE_LIST = 8;
    private static final int MAX_PICKER = 99;
    private static final int MAX_ARR_RUNWAY_SEL = 8;
    /** Upstream's height of the Taxi Routes content, for scrolling. */
    private static final int ROUTES_CONTENT_H = 480;
    private static final int HIGHLIGHT = 0xAAFFAA00;

    private final OpenBaseScreenPayload base;
    private final List<TaxiRoute> routes;

    private int selectedRouteIdx = -1;
    private @Nullable EditBox routeIdField;
    private String editingRouteId = "";
    private String selectedParkingId = "";
    private String selectedRunwayId = "";
    private String selectedRunwayBId = "";
    private String selectedArrivalRunwayId = "";
    private final List<String> routeWaypoints = new ArrayList<>();
    private final List<String> routeArrivalWaypoints = new ArrayList<>();
    private boolean editingArrivalWps;
    private int scrollRoutes;

    private Button routesTab;
    private final List<Button> routeButtons = new ArrayList<>();
    private final List<Button> parkingButtons = new ArrayList<>();
    private final List<Button> runwayAButtons = new ArrayList<>();
    private final List<Button> runwayBButtons = new ArrayList<>();
    private final List<Button> helipadButtons = new ArrayList<>();
    private final List<Button> endpointButtons = new ArrayList<>();
    private @Nullable Button toggleButton;

    public BaseConfigScreen(OpenBaseScreenPayload base) {
        super(Component.literal("Base: " + base.baseId()));
        this.base = base;
        this.routes = new ArrayList<>(base.routes());
    }

    // Layout, as upstream's initRoutesTab.

    private int clipTop() {
        return this.height / 4 + 12;
    }

    private int clipBottom() {
        return this.height - 30;
    }

    private int helipadRows() {
        return this.base.helipads().isEmpty() ? 0 : (this.base.helipads().size() > 4 ? 2 : 1);
    }

    /** The Y of the first runway end A button. */
    private int runwayAY(int sty) {
        int rows = this.base.runwayA().isEmpty() ? 0 : (this.base.parking().size() > 4 ? 2 : 1);
        return sty + 56 + rows * 20;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        this.routesTab = this.addRenderableWidget(Button.builder(Component.literal("Taxi Routes"), b -> {
        }).bounds(cx - 82, this.height / 4 - 12, 80, 18).build());
        Button missionTab = this.addRenderableWidget(Button.builder(Component.literal("Mission"), b -> {
        }).bounds(cx + 2, this.height / 4 - 12, 80, 18).build());
        missionTab.active = false;
        this.initRoutesTab();
        this.addRenderableWidget(Button.builder(Component.literal("Close"), b -> this.onClose())
                .bounds(cx - 25, this.height - 26, 50, 16).build());
    }

    private void initRoutesTab() {
        int cx = this.width / 2;
        int lx = cx - 195;
        int rx = cx - 65;
        int sty = this.clipTop() - this.scrollRoutes;
        List<AbstractWidget> clipped = new ArrayList<>();
        this.routeButtons.clear();
        this.parkingButtons.clear();
        this.runwayAButtons.clear();
        this.runwayBButtons.clear();
        this.helipadButtons.clear();
        this.endpointButtons.clear();

        for (int i = 0; i < this.routes.size() && i < MAX_ROUTE_LIST; i++) {
            int index = i;
            this.routeButtons.add(this.button(clipped, this.routes.get(i).routeId(), lx, sty + i * 22, 110, 18,
                    () -> {
                        this.selectedRouteIdx = index;
                        this.loadRoute(this.routes.get(index));
                    }));
        }
        int listBottom = sty + MAX_ROUTE_LIST * 22;
        this.button(clipped, "New", lx, listBottom + 4, 52, 16, () -> {
            this.selectedRouteIdx = -1;
            this.clearRouteForm();
            this.rebuildWidgets();
        });
        this.button(clipped, "Save", lx + 58, listBottom + 4, 52, 16, this::saveRoute);
        this.button(clipped, "Delete", lx + 58, listBottom + 24, 52, 16, this::deleteRoute);

        // Upstream kept what was typed into a new route's ID across layouts.
        if (this.routeIdField != null && this.selectedRouteIdx < 0) {
            this.editingRouteId = this.routeIdField.getValue();
        }
        EditBox field = new EditBox(this.font, rx, sty, 200, 16, Component.literal("Route ID"));
        field.setMaxLength(64);
        field.setValue(this.selectedRouteIdx >= 0 && this.selectedRouteIdx < this.routes.size()
                ? this.routes.get(this.selectedRouteIdx).routeId() : this.editingRouteId);
        this.routeIdField = this.addRenderableWidget(field);
        clipped.add(field);

        int btnW = 58;
        int gap = 4;
        int py = sty + 28;
        List<OpenBaseScreenPayload.Marker> parking = this.base.parking();
        for (int i = 0; i < parking.size() && i < MAX_PICKER; i++) {
            String id = parking.get(i).id();
            this.parkingButtons.add(this.button(clipped, id, rx + (i % 4) * (btnW + gap), py + (i / 4) * 20, btnW, 16,
                    () -> this.selectedParkingId = id));
        }

        int ry = this.runwayAY(sty);
        List<OpenBaseScreenPayload.Marker> runwayA = this.base.runwayA();
        for (int i = 0; i < runwayA.size() && i < 4; i++) {
            String id = runwayA.get(i).id();
            this.runwayAButtons.add(this.button(clipped, id, rx + i * (btnW + gap), ry, btnW, 16,
                    () -> this.selectedRunwayId = id));
        }

        // Upstream: without an end A, end B is the route's end (a return-only route after a
        // landing that ends at B); with one, it is where take-off ends.
        int rbyY = ry + 24;
        List<OpenBaseScreenPayload.Marker> runwayB = this.base.runwayB();
        for (int i = 0; i < runwayB.size() && i < 4; i++) {
            String id = runwayB.get(i).id();
            this.runwayBButtons.add(this.button(clipped, id, rx + i * (btnW + gap), rbyY, btnW, 16, () -> {
                if (this.selectedRunwayId.isEmpty()) {
                    this.selectedRunwayId = id;
                } else {
                    this.selectedRunwayBId = id;
                }
            }));
        }

        // Upstream: a helipad ends the route, and is its parking spot too when none is chosen.
        int hpy = rbyY + 24;
        List<OpenBaseScreenPayload.Marker> helipads = this.base.helipads();
        for (int i = 0; i < helipads.size() && i < MAX_PICKER; i++) {
            String id = helipads.get(i).id();
            this.helipadButtons.add(this.button(clipped, id, rx + (i % 4) * (btnW + gap), hpy + (i / 4) * 20, btnW, 16,
                    () -> {
                        this.selectedRunwayId = id;
                        if (this.selectedParkingId.isEmpty()) {
                            this.selectedParkingId = id;
                        }
                    }));
        }

        int wy = this.waypointY(sty);
        int colW = 100;
        this.toggleButton = this.button(clipped, this.editingArrivalWps ? "▼ 着陸 WP" : "▶ 出発 WP",
                rx, wy - 16, 90, 13, () -> {
                    this.editingArrivalWps = !this.editingArrivalWps;
                    this.rebuildWidgets();
                });
        if (this.editingArrivalWps) {
            List<OpenBaseScreenPayload.Marker> endpoints = this.arrivalEndpoints();
            for (int i = 0; i < endpoints.size() && i < MAX_ARR_RUNWAY_SEL; i++) {
                String id = endpoints.get(i).id();
                this.endpointButtons.add(this.button(clipped, id, rx + colW + 8 + i * 54, wy, 52, 14,
                        () -> this.selectedArrivalRunwayId = this.selectedArrivalRunwayId.equals(id) ? "" : id));
            }
        }

        int arrOffset = this.editingArrivalWps ? 18 : 0;
        List<String> active = this.activeWaypoints();
        for (int i = 0; i < active.size() && i < MAX_PICKER; i++) {
            int index = i;
            int bx = rx + colW - 52;
            int y = wy + arrOffset + i * 18;
            if (i > 0) {
                this.button(clipped, "^", bx, y, 14, 14, () -> this.editWaypoints(
                        () -> Collections.swap(active, index, index - 1)));
            }
            if (i < active.size() - 1) {
                this.button(clipped, "v", bx + 16, y, 14, 14, () -> this.editWaypoints(
                        () -> Collections.swap(active, index, index + 1)));
            }
            this.button(clipped, "x", bx + 34, y, 14, 14, () -> this.editWaypoints(() -> active.remove(index)));
        }
        List<String> available = this.availableWaypoints(active);
        for (int i = 0; i < available.size() && i < MAX_PICKER; i++) {
            String id = available.get(i);
            this.button(clipped, id, rx + colW + 8, wy + arrOffset + i * 18, colW - 10, 14,
                    () -> this.editWaypoints(() -> active.add(id)));
        }

        // Upstream hid the tab's widgets outside the scrolling area.
        int clipTop = this.clipTop();
        int clipBot = this.clipBottom();
        for (AbstractWidget w : clipped) {
            w.visible = w.getY() + w.getHeight() > clipTop && w.getY() < clipBot;
        }
        if (!this.routeIdField.visible) {
            this.routeIdField.setFocused(false);
        }
    }

    /** The Y of the waypoint lists: below the helipads and upstream's 72-pixel Helipad-B and sequence section. */
    private int waypointY(int sty) {
        return this.runwayAY(sty) + 48 + this.helipadRows() * 20 + 6 + 72;
    }

    private Button button(List<AbstractWidget> clipped, String label, int x, int y, int w, int h, Runnable action) {
        Button button = this.addRenderableWidget(Button.builder(Component.literal(label), b -> action.run())
                .bounds(x, y, w, h).build());
        clipped.add(button);
        return button;
    }

    private void editWaypoints(Runnable edit) {
        edit.run();
        this.rebuildWidgets();
    }

    private List<String> activeWaypoints() {
        return this.editingArrivalWps ? this.routeArrivalWaypoints : this.routeWaypoints;
    }

    /** Upstream's landing ends: runway ends A, then B, then helipads. */
    private List<OpenBaseScreenPayload.Marker> arrivalEndpoints() {
        List<OpenBaseScreenPayload.Marker> endpoints = new ArrayList<>(this.base.runwayA());
        endpoints.addAll(this.base.runwayB());
        endpoints.addAll(this.base.helipads());
        return endpoints;
    }

    /** Upstream: any waypoint, runway end, helipad or parking spot not yet in the list can be passed through. */
    private List<String> availableWaypoints(List<String> active) {
        List<String> result = new ArrayList<>();
        for (List<OpenBaseScreenPayload.Marker> markers : List.of(this.base.waypoints(), this.base.runwayA(),
                this.base.runwayB(), this.base.helipads(), this.base.parking())) {
            for (OpenBaseScreenPayload.Marker m : markers) {
                if (!active.contains(m.id())) {
                    result.add(m.id());
                }
            }
        }
        return result;
    }

    @Override
    protected void setInitialFocus() {
        if (this.routeIdField != null && this.routeIdField.visible) {
            this.setInitialFocus(this.routeIdField);
        }
    }

    // Route editing, as upstream.

    private void loadRoute(TaxiRoute r) {
        this.editingRouteId = r.routeId();
        this.selectedParkingId = r.parkingId();
        this.selectedRunwayId = r.runwayId();
        this.selectedRunwayBId = r.runwayBId();
        this.selectedArrivalRunwayId = r.arrivalRunwayId();
        this.routeWaypoints.clear();
        this.routeWaypoints.addAll(r.waypointIds());
        this.routeArrivalWaypoints.clear();
        this.routeArrivalWaypoints.addAll(r.arrivalWaypointIds());
        this.rebuildWidgets();
    }

    private void clearRouteForm() {
        this.editingRouteId = "";
        if (this.routeIdField != null) {
            this.routeIdField.setValue("");
        }
        this.selectedParkingId = "";
        this.selectedRunwayId = "";
        this.selectedRunwayBId = "";
        this.selectedArrivalRunwayId = "";
        this.routeWaypoints.clear();
        this.routeArrivalWaypoints.clear();
        this.editingArrivalWps = false;
    }

    private void chat(String message) {
        this.minecraft.gui.chatListener().handleSystemMessage(Component.literal(message), false);
    }

    private boolean isHelipad(String id) {
        return this.base.helipads().stream().anyMatch(m -> m.id().equals(id));
    }

    private void saveRoute() {
        String rid = this.routeIdField != null ? this.routeIdField.getValue().trim() : "";
        if (rid.isEmpty()) {
            this.chat("§cRoute ID が空です");
            return;
        }
        if (this.selectedParkingId.isEmpty()) {
            this.chat("§cParking を選択してください");
            return;
        }
        if (this.selectedRunwayId.isEmpty()) {
            if (this.isHelipad(this.selectedParkingId)) {
                this.selectedRunwayId = this.selectedParkingId;
            } else {
                this.chat("§cEndpoint (Runway A または Helipad) を選択してください");
                return;
            }
        }
        // The parking heading is set on the parking marker.
        TaxiRoute route = new TaxiRoute(rid, this.base.baseId(), this.selectedParkingId, this.selectedRunwayId,
                this.selectedRunwayBId, this.routeWaypoints, this.routeArrivalWaypoints, -1, this.selectedArrivalRunwayId);
        ClientPacketDistributor.sendToServer(new TaxiRouteActionPayload(this.base.pos(), false, route));
        boolean found = false;
        for (int i = 0; i < this.routes.size(); i++) {
            if (this.routes.get(i).routeId().equals(rid)) {
                this.routes.set(i, route);
                found = true;
                break;
            }
        }
        if (!found) {
            this.routes.add(route);
            this.selectedRouteIdx = this.routes.size() - 1;
        }
        this.rebuildWidgets();
    }

    private void deleteRoute() {
        if (this.selectedRouteIdx < 0 || this.selectedRouteIdx >= this.routes.size()) {
            return;
        }
        TaxiRoute route = this.routes.remove(this.selectedRouteIdx);
        ClientPacketDistributor.sendToServer(new TaxiRouteActionPayload(this.base.pos(), true, route));
        this.selectedRouteIdx = -1;
        this.clearRouteForm();
        this.rebuildWidgets();
    }

    // Input.

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (scrollY == 0.0) {
            return false;
        }
        int visH = this.clipBottom() - this.clipTop();
        int maxScroll = Math.max(0, ROUTES_CONTENT_H - visH);
        this.scrollRoutes = Math.clamp(this.scrollRoutes + (scrollY > 0.0 ? -14 : 14), 0, maxScroll);
        this.rebuildWidgets();
        return true;
    }

    /** Upstream's Tab did nothing on this tab. */
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_TAB) {
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // Drawing, as upstream's drawScreen and drawRoutesTab.

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int cx = this.width / 2;
        int ty = this.height / 4;
        graphics.centeredText(this.font, "§eBase: §f" + this.base.baseId(), cx, ty - 26, 0xFFFFFFFF);
        Button tab = this.routesTab;
        graphics.fill(tab.getX(), tab.getY() + tab.getHeight(), tab.getX() + tab.getWidth(),
                tab.getY() + tab.getHeight() + 2, 0xFFFFAA00);
        this.drawRoutesTab(graphics, cx);
        super.extractRenderState(graphics, mouseX, mouseY, a);
    }

    private void highlight(GuiGraphicsExtractor graphics, Button b, int color) {
        if (b.visible) {
            graphics.fill(b.getX() - 1, b.getY() - 1, b.getX() + b.getWidth() + 1, b.getY() + b.getHeight() + 1, color);
        }
    }

    private void highlightIf(GuiGraphicsExtractor graphics, List<Button> buttons, List<OpenBaseScreenPayload.Marker> markers,
            String... selected) {
        for (int i = 0; i < buttons.size(); i++) {
            String id = markers.get(i).id();
            for (String s : selected) {
                if (id.equals(s)) {
                    this.highlight(graphics, buttons.get(i), HIGHLIGHT);
                    break;
                }
            }
        }
    }

    private void label(GuiGraphicsExtractor graphics, String text, int x, int y, int rgb) {
        graphics.text(this.font, text, x, y, 0xFF000000 | rgb);
    }

    private void drawRoutesTab(GuiGraphicsExtractor graphics, int cx) {
        int clipTop = this.clipTop();
        int clipBot = this.clipBottom();
        graphics.enableScissor(0, clipTop, this.width, clipBot);
        int sty = clipTop - this.scrollRoutes;
        int lx = cx - 195;
        int rx = cx - 65;

        this.label(graphics, "§7─ Routes ─", lx, sty + 2, 0x888888);
        if (this.selectedRouteIdx >= 0 && this.selectedRouteIdx < this.routeButtons.size()) {
            this.highlight(graphics, this.routeButtons.get(this.selectedRouteIdx), 0x44FFAA00);
        }
        this.label(graphics, "Route ID:", rx, sty + 2, 0xAAAAAA);

        int py = sty + 28;
        this.label(graphics, "§7Parking:", rx, py - 10, 0xAAAAAA);
        this.highlightIf(graphics, this.parkingButtons, this.base.parking(), this.selectedParkingId);

        int ry = this.runwayAY(sty);
        this.label(graphics, "§7Runway A:", rx, ry - 10, 0xAAAAAA);
        if (this.base.runwayA().isEmpty()) {
            this.label(graphics, "§c(no RUNWAY_A markers in this base)", rx, ry + 2, 0xFF4444);
        } else {
            this.highlightIf(graphics, this.runwayAButtons, this.base.runwayA(), this.selectedRunwayId);
        }

        int rbyY = ry + 24;
        this.label(graphics, "§7Runway B:", rx, rbyY - 10, 0xAAAAAA);
        if (this.base.runwayB().isEmpty()) {
            this.label(graphics, "§7(no RUNWAY_B markers)", rx, rbyY + 2, 0x666666);
        } else {
            this.highlightIf(graphics, this.runwayBButtons, this.base.runwayB(), this.selectedRunwayBId);
        }

        int hpy = rbyY + 24;
        this.label(graphics, "§9Helipad §7(Runway End / P+R):", rx, hpy - 10, 0xAAAAAA);
        if (this.base.helipads().isEmpty()) {
            this.label(graphics, "§7(no HELIPAD markers)", rx, hpy + 2, 0x666666);
        } else {
            this.highlightIf(graphics, this.helipadButtons, this.base.helipads(), this.selectedParkingId,
                    this.selectedRunwayId);
        }

        int hbpy = hpy + this.helipadRows() * 20 + 6;
        this.label(graphics, "§bHelipad-B §7(facing dir):", rx, hbpy, 0xAAAAAA);
        if (this.base.helipadB().isEmpty()) {
            this.label(graphics, "§c(none — HELIPAD_B マーカーを設置して baseId を HELIPAD に合わせる)",
                    rx, hbpy + 12, 0xFF6666);
        } else {
            List<OpenBaseScreenPayload.Marker> facing = this.base.helipadB();
            for (int i = 0; i < facing.size() && i < 3; i++) {
                OpenBaseScreenPayload.Marker m = facing.get(i);
                this.label(graphics, "§b" + m.id() + " §7(" + m.pos().getX() + "," + m.pos().getZ() + ")",
                        rx + i * 100, hbpy + 12, 0x55FFFF);
            }
        }

        int colW = 118;
        int wy = this.waypointY(sty);
        this.label(graphics, this.departureSequence(rx), rx, wy - 52, 0xFFFFFF);
        this.label(graphics, this.arrivalSequence(), rx, wy - 38, 0x666666);
        if (this.editingArrivalWps && this.toggleButton != null) {
            this.highlight(graphics, this.toggleButton, HIGHLIGHT);
        }

        List<String> active = this.activeWaypoints();
        this.label(graphics, this.editingArrivalWps ? "§b着陸 WP:" : "§f出発 WP:", rx + 94, wy - 6,
                0xAAAAAA);
        if (this.editingArrivalWps) {
            this.label(graphics, "§bエントリー端点:", rx + colW + 8, wy - 6, 0x55FFFF);
            List<OpenBaseScreenPayload.Marker> endpoints = this.arrivalEndpoints();
            for (int i = 0; i < this.endpointButtons.size(); i++) {
                if (endpoints.get(i).id().equals(this.selectedArrivalRunwayId)) {
                    this.highlight(graphics, this.endpointButtons.get(i), HIGHLIGHT);
                    break;
                }
            }
            this.label(graphics, "§7Available:", rx + colW + 8, wy + 18 - 6, 0xAAAAAA);
        } else {
            this.label(graphics, "§7Available:", rx + colW + 8, wy - 6, 0xAAAAAA);
        }
        int arrOffset = this.editingArrivalWps ? 18 : 0;
        for (int i = 0; i < active.size() && i < MAX_PICKER; i++) {
            this.label(graphics, "§f" + (i + 1) + ". " + active.get(i), rx, wy + arrOffset + i * 18 + 3, 0xFFFFFF);
        }
        if (active.isEmpty()) {
            if (this.editingArrivalWps) {
                this.label(graphics, "§7(空=出発の逆順)", rx, wy + arrOffset + 3, 0x555555);
            } else {
                this.label(graphics, "§7(none)", rx, wy + 3, 0x666666);
            }
        }
        graphics.disableScissor();
        this.drawScrollbar(graphics, clipTop, clipBot);
    }

    /** Upstream's departure line, shortened to its first and last waypoints when too wide. */
    private String departureSequence(int rx) {
        String start = "§7出発: " + (this.selectedParkingId.isEmpty() ? "§c?" : "§a" + this.selectedParkingId);
        String end = "§7→" + (this.selectedRunwayId.isEmpty() ? "§c?" : "§b" + this.selectedRunwayId);
        StringBuilder seq = new StringBuilder(start);
        for (String wp : this.routeWaypoints) {
            seq.append("§7→§f").append(wp);
        }
        seq.append(end);
        String full = seq.toString();
        int size = this.routeWaypoints.size();
        if (this.font.width(ChatFormatting.stripFormatting(full)) > this.width - rx - 12 && size > 2) {
            return start + "§7→§f" + this.routeWaypoints.get(0) + "§7→§8…(" + (size - 2)
                    + ")…§7→§f" + this.routeWaypoints.get(size - 1) + end;
        }
        return full;
    }

    /** Upstream's arrival note: the arrival waypoints, or that the departure ones are flown in reverse. */
    private String arrivalSequence() {
        String entry = this.selectedArrivalRunwayId.isEmpty() ? this.selectedRunwayId : this.selectedArrivalRunwayId;
        if (!this.routeArrivalWaypoints.isEmpty()) {
            StringBuilder arr = new StringBuilder("§7着陸: §b").append(entry.isEmpty() ? "?" : entry);
            for (String wp : this.routeArrivalWaypoints) {
                arr.append("§7→§f").append(wp);
            }
            return arr.append("§7→§a").append(this.selectedParkingId.isEmpty() ? "?" : this.selectedParkingId)
                    .toString();
        }
        return "§7着陸: 逆順 (" + (entry.isEmpty() ? "?" : "§b" + entry + "§7") + "→…→"
                + (this.selectedParkingId.isEmpty() ? "?" : "§a" + this.selectedParkingId + "§7") + ")";
    }

    private void drawScrollbar(GuiGraphicsExtractor graphics, int top, int bot) {
        int visH = bot - top;
        int maxScroll = Math.max(0, ROUTES_CONTENT_H - visH);
        if (maxScroll <= 0) {
            return;
        }
        int bx = this.width - 7;
        int barH = Math.max(16, visH * visH / ROUTES_CONTENT_H);
        int barY = top + (int) ((long) this.scrollRoutes * (visH - barH) / maxScroll);
        graphics.fill(bx, top, bx + 4, bot, 0x44FFFFFF);
        graphics.fill(bx, barY, bx + 4, barY + barH, 0xCCCCCCCC);
    }
}
