package io.github.mcheliceport.client.gui;

import io.github.mcheliceport.net.DispatchOrderPayload;
import io.github.mcheliceport.net.OpenBaseScreenPayload;
import io.github.mcheliceport.net.RecallOrdersPayload;
import io.github.mcheliceport.net.TaxiRouteActionPayload;
import io.github.mcheliceport.wingman.block.MarkerType;
import io.github.mcheliceport.wingman.mission.TaxiRoute;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Upstream's {@code GuiBaseConfig}, opened by right-clicking a base marker. Its Taxi Routes tab
 * lists the base's routes on the left (New, Save, Delete), and on the right the route's name and
 * numbered steps: its parking spot, its runway ends or helipad, its departure and arrival
 * waypoints in order, then a summary of both legs and what still stops it saving. Its Mission tab
 * orders one of the aircraft near the base on a mission: the mission types and weapons, the
 * target, orbit, cruise height, strike passes and time limit, and the taxi routes to leave and
 * return by; an arrival route ending at a helipad lands vertically. The port adds Recall All
 * beside Dispatch, which sends every mission of the base home to land and park. Each tab scrolls
 * with the mouse wheel.
 *
 * <p>Upstream placed the Taxi Routes tab with fixed offsets that overlapped once a base had
 * helipads, and its labels were partly Japanese. The port lays the tab out from the top down,
 * draws each label where its widgets went, and uses English throughout.
 */
public class BaseConfigScreen extends Screen {

    private static final int MAX_PICKER = 99;
    /** Upstream's height of the Mission content, for scrolling. */
    private static final int MISSION_CONTENT_H = 460;
    private static final int MAX_AIRCRAFT = 8;
    private static final int MAX_ROUTE_PICK = 8;
    private static final int HIGHLIGHT = 0xAAFFAA00;
    private static final int TOGGLED = 0xFFFFAA00;
    private static final int GREY = 0xAAAAAA;
    /** The Taxi Routes tab's route list and step columns. */
    private static final int LEFT_W = 104;
    private static final int RIGHT_W = 292;
    private static final int COLUMN_GAP = 10;
    private static final String[] MISSION_TYPES = {"CAP", "CAS", "STRIKE", "ESCORT", "RECON", "FERRY"};
    private static final String[] WEAPON_LABELS = {"GUN", "AA Msl", "AS Msl", "CAS", "Rocket", "Bomb"};
    private static final String[] WEAPONS = {"gun", "aamissile", "asmissile", "cas", "rocket", "bomb"};
    private static final String[] MISSION_FIELD_LABELS = {"Target X:", "Z:", "Orbit R:", "Cruise Y:", "Passes:",
            "Limit(s):"};

    /** A line of text on the Taxi Routes tab, placed with its widgets. */
    private record Label(FormattedCharSequence text, int x, int y, int rgb) {
    }

    /** A button outlined while its choice is selected. */
    private record Highlight(Button button, BooleanSupplier on) {
    }

    private final OpenBaseScreenPayload base;
    private final List<TaxiRoute> routes;
    /** Each marker of the base and its type, by ID; the first of a repeated ID. */
    private final Map<String, MarkerType> markerTypes = new LinkedHashMap<>();
    /** The IDs more than one of the base's markers have. */
    private final List<String> duplicateIds = new ArrayList<>();

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
    private int routesContentH;
    /** Where the route's problems are drawn, under the route list. */
    private int problemsY;
    /** The last Save's result, under the route list. */
    private String status = "";
    private final List<Label> labels = new ArrayList<>();
    private final List<Highlight> highlights = new ArrayList<>();

    /** 0 for Taxi Routes, 1 for Mission. */
    private int currentTab;
    private final Set<String> selectedMissionTypes = new LinkedHashSet<>();
    private final Set<String> selectedWeapons = new LinkedHashSet<>();
    private @Nullable UUID selectedAircraft;
    /** The departure route, or empty for an airborne start. */
    private String selectedRouteForDispatch = "";
    /** The arrival route, or empty for upstream's fallback. */
    private String selectedArrivalRouteId = "";
    private boolean orbitAttackMode;
    private int scrollMission;
    /** The mission fields' text, kept across layouts: target X and Z, orbit, cruise, passes, limit. */
    private final String[] missionValues = {"0", "0", "300", "80", "2", "600"};
    private final List<EditBox> missionFields = new ArrayList<>();
    private final Button[] missionTypeButtons = new Button[MISSION_TYPES.length];
    private final Button[] weaponButtons = new Button[WEAPONS.length];
    private final List<Button> aircraftButtons = new ArrayList<>();
    private final List<Button> departureButtons = new ArrayList<>();
    private final List<Button> arrivalButtons = new ArrayList<>();
    private @Nullable Button orbitAttackButton;

    private Button routesTab;
    private Button missionTab;

    public BaseConfigScreen(OpenBaseScreenPayload base) {
        super(Component.literal("Base: " + base.baseId()));
        this.base = base;
        this.routes = new ArrayList<>(base.routes());
        this.selectedWeapons.add("gun");
        Map<String, Integer> counts = new LinkedHashMap<>();
        this.collect(counts, base.parking(), MarkerType.PARKING);
        this.collect(counts, base.runwayA(), MarkerType.RUNWAY_A);
        this.collect(counts, base.runwayB(), MarkerType.RUNWAY_B);
        this.collect(counts, base.helipads(), MarkerType.HELIPAD);
        this.collect(counts, base.helipadB(), MarkerType.HELIPAD_B);
        // Waypoints near the base that name no base are listed too, maybe again; count each once.
        Set<OpenBaseScreenPayload.Marker> waypoints = new LinkedHashSet<>(base.waypoints());
        this.collect(counts, List.copyOf(waypoints), MarkerType.WAYPOINT);
        counts.forEach((id, n) -> {
            if (n > 1) {
                this.duplicateIds.add(id);
            }
        });
    }

    private void collect(Map<String, Integer> counts, List<OpenBaseScreenPayload.Marker> markers, MarkerType type) {
        for (OpenBaseScreenPayload.Marker m : markers) {
            counts.merge(m.id(), 1, Integer::sum);
            this.markerTypes.putIfAbsent(m.id(), type);
        }
    }

    // Layout.

    private int clipTop() {
        return 44;
    }

    private int clipBottom() {
        return this.height - 30;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        this.routesTab = this.addRenderableWidget(Button.builder(Component.literal("Taxi Routes"), b -> this.selectTab(0))
                .bounds(cx - 82, 20, 80, 18).build());
        this.missionTab = this.addRenderableWidget(Button.builder(Component.literal("Mission"), b -> this.selectTab(1))
                .bounds(cx + 2, 20, 80, 18).build());
        if (this.currentTab == 0) {
            this.initRoutesTab();
        } else {
            this.initMissionTab();
        }
        this.addRenderableWidget(Button.builder(Component.literal("Close"), b -> this.onClose())
                .bounds(cx - 25, this.height - 26, 50, 16).build());
    }

    private void selectTab(int tab) {
        if (this.currentTab != tab) {
            this.saveMissionValues();
            this.currentTab = tab;
            this.scrollRoutes = 0;
            this.scrollMission = 0;
            this.rebuildWidgets();
        }
    }

    // The Taxi Routes tab.

    private int leftX() {
        return this.width / 2 - (LEFT_W + COLUMN_GAP + RIGHT_W) / 2;
    }

    private int rightX() {
        return this.leftX() + LEFT_W + COLUMN_GAP;
    }

    private void initRoutesTab() {
        int lx = this.leftX();
        int rx = this.rightX();
        int sty = this.clipTop() - this.scrollRoutes;
        List<AbstractWidget> clipped = new ArrayList<>();
        this.labels.clear();
        this.highlights.clear();

        // The route list.
        int ly = sty;
        this.label("Routes", lx, ly, GREY);
        ly += 12;
        for (int i = 0; i < this.routes.size(); i++) {
            int index = i;
            Button b = this.button(clipped, shorten(this.routes.get(i).routeId()), lx, ly, LEFT_W, 16, () -> {
                this.selectedRouteIdx = index;
                this.status = "";
                this.loadRoute(this.routes.get(index));
            });
            this.highlights.add(new Highlight(b, () -> this.selectedRouteIdx == index));
            ly += 18;
        }
        if (this.routes.isEmpty()) {
            this.label("§7(none yet)", lx, ly + 3, GREY);
            ly += 16;
        }
        ly += 4;
        int half = (LEFT_W - 4) / 2;
        this.button(clipped, "New", lx, ly, half, 16, () -> {
            this.selectedRouteIdx = -1;
            this.status = "";
            this.clearRouteForm();
            this.rebuildWidgets();
        });
        this.button(clipped, "Save", lx + half + 4, ly, half, 16, this::saveRoute);
        ly += 20;
        if (this.selectedRouteIdx >= 0) {
            this.button(clipped, "Delete", lx + half + 4, ly, half, 16, this::deleteRoute);
            ly += 20;
        }
        ly = this.wrapped(this.status, lx, ly + 2, LEFT_W, 0xFFFFFF);
        this.problemsY = ly + 2;

        // The route's name.
        int y = sty;
        this.label("Route name", rx, y + 4, GREY);
        if (this.routeIdField != null) {
            this.editingRouteId = this.routeIdField.getValue();
        }
        EditBox field = new EditBox(this.font, rx + 62, y, 170, 16, Component.literal("Route name"));
        field.setMaxLength(64);
        field.setValue(this.editingRouteId);
        field.setResponder(value -> this.editingRouteId = value);
        this.routeIdField = this.addRenderableWidget(field);
        clipped.add(field);
        y += 24;

        // 1. Parking spot.
        y = this.step("1. Parking spot", "where the aircraft starts and ends", rx, y);
        List<OpenBaseScreenPayload.Marker> parking = this.base.parking();
        if (parking.isEmpty()) {
            y = this.wrapped("§cNo parking spots. Set a marker's type to Parking and its parent base to §f"
                    + this.base.baseId() + "§c.", rx, y, RIGHT_W, 0xFFFFFF);
        }
        y = this.pickers(clipped, parking, rx, y, 4, RIGHT_W, id -> this.selectedParkingId = id,
                id -> id.equals(this.selectedParkingId));
        y += 6;

        // 2. Take-off and landing.
        y = this.step("2. Take-off and landing", "runway or helipad", rx, y);
        int px = rx + 62;
        int pw = RIGHT_W - 62;
        boolean noRunwayA = this.base.runwayA().isEmpty();
        this.label("Runway A", rx, y + 4, GREY);
        if (noRunwayA) {
            this.label("§7none in this base", px, y + 4, GREY);
            y += 16;
        }
        y = this.pickers(clipped, this.base.runwayA(), px, y, 3, pw, id -> {
            this.selectedRunwayId = id;
        }, id -> id.equals(this.selectedRunwayId));
        // Upstream: without an end A, end B is the route's end (a return-only route).
        this.label("Runway B", rx, y + 4, GREY);
        if (this.base.runwayB().isEmpty()) {
            this.label("§7none in this base", px, y + 4, GREY);
            y += 16;
        }
        y = this.pickers(clipped, this.base.runwayB(), px, y, 3, pw, id -> {
            if (noRunwayA) {
                this.selectedRunwayId = id;
            } else {
                this.selectedRunwayBId = this.selectedRunwayBId.equals(id) ? "" : id;
            }
        }, id -> id.equals(noRunwayA ? this.selectedRunwayId : this.selectedRunwayBId));
        // Upstream: a helipad ends the route, and is its parking spot too when none is chosen.
        this.label("Helipad", rx, y + 4, GREY);
        if (this.base.helipads().isEmpty()) {
            this.label("§7none in this base", px, y + 4, GREY);
            y += 16;
        }
        y = this.pickers(clipped, this.base.helipads(), px, y, 3, pw, id -> {
            this.selectedRunwayId = id;
            this.selectedRunwayBId = "";
            if (this.selectedParkingId.isEmpty()) {
                this.selectedParkingId = id;
            }
        }, id -> id.equals(this.selectedRunwayId) || id.equals(this.selectedParkingId));
        if (!this.base.helipads().isEmpty()) {
            String facing = this.base.helipadB().isEmpty()
                    ? "§7No Helipad-B: aircraft on a helipad face their target."
                    : "§7Helipads face towards Helipad-B §3" + this.base.helipadB().get(0).id() + "§7.";
            y = this.wrapped(facing, px, y, pw, 0xFFFFFF);
        }
        y += 6;

        // 3. Waypoints, departure or arrival.
        y = this.step("3. Waypoints", "the way the aircraft taxis", rx, y);
        Button dep = this.button(clipped, "Departure", rx, y, 80, 16, () -> this.showArrival(false));
        Button arr = this.button(clipped, "Arrival", rx + 84, y, 80, 16, () -> this.showArrival(true));
        this.highlights.add(new Highlight(dep, () -> !this.editingArrivalWps));
        this.highlights.add(new Highlight(arr, () -> this.editingArrivalWps));
        y += 22;
        if (this.editingArrivalWps) {
            this.label("Landing end §7(empty: the take-off end)", rx, y, GREY);
            y += 12;
            y = this.pickers(clipped, this.arrivalEndpoints(), rx, y, 4, RIGHT_W,
                    id -> this.selectedArrivalRunwayId = this.selectedArrivalRunwayId.equals(id) ? "" : id,
                    id -> id.equals(this.selectedArrivalRunwayId));
            y += 2;
        }
        int colW = (RIGHT_W - 10) / 2;
        int ax = rx + colW + 10;
        this.label("Flown in order", rx, y, GREY);
        this.label("Add §8(click)", ax, y, GREY);
        y += 12;
        List<String> active = this.activeWaypoints();
        int listY = y;
        for (int i = 0; i < active.size() && i < MAX_PICKER; i++) {
            int index = i;
            int bx = rx + colW - 46;
            this.label("§f" + (i + 1) + ". " + this.tagged(active.get(i)), rx, listY + 3, 0xFFFFFF);
            if (i > 0) {
                this.button(clipped, "^", bx, listY, 14, 14, () -> this.editWaypoints(
                        () -> Collections.swap(active, index, index - 1)));
            }
            if (i < active.size() - 1) {
                this.button(clipped, "v", bx + 16, listY, 14, 14, () -> this.editWaypoints(
                        () -> Collections.swap(active, index, index + 1)));
            }
            this.button(clipped, "x", bx + 32, listY, 14, 14, () -> this.editWaypoints(() -> active.remove(index)));
            listY += 18;
        }
        if (active.isEmpty()) {
            listY = this.wrapped(this.editingArrivalWps ? "§7None: the departure waypoints, in reverse."
                    : "§7None: straight from the parking spot.", rx, listY + 3, colW, 0xFFFFFF);
        }
        int addY = y;
        List<String> available = this.availableWaypoints(active);
        for (int i = 0; i < available.size() && i < MAX_PICKER; i++) {
            String id = available.get(i);
            this.button(clipped, this.tagged(id), ax, addY, colW, 14, () -> this.editWaypoints(() -> active.add(id)));
            addY += 16;
        }
        if (available.isEmpty()) {
            addY = this.wrapped("§7No more markers. Waypoints within 500 blocks show here.", ax, addY + 3, colW,
                    0xFFFFFF);
        }
        y = Math.max(listY, addY) + 8;

        // The summary; what still stops a save is drawn under the route list, as it changes with typing.
        y = this.step("Summary", "", rx, y);
        y = this.wrapped(this.departureSequence(), rx, y, RIGHT_W, 0xFFFFFF);
        y = this.wrapped(this.arrivalSequence(), rx, y, RIGHT_W, 0xFFFFFF);

        // Room for the problems at their longest, with every save check failing.
        int problemsH = 3 * 23;
        for (String id : this.duplicateIds) {
            problemsH += this.font.split(Component.literal(duplicateNote(id)), LEFT_W).size() * 10 + 3;
        }
        this.routesContentH = Math.max(this.problemsY + problemsH, y) - sty + 8;
        this.clip(clipped);
        if (!this.routeIdField.visible) {
            this.routeIdField.setFocused(false);
        }
    }

    /** A step's heading and its note, returning the Y below it. */
    private int step(String title, String note, int x, int y) {
        this.label("§e" + title + (note.isEmpty() ? "" : " §7- " + note), x, y, 0xFFFFFF);
        return y + 12;
    }

    private void label(String text, int x, int y, int rgb) {
        this.labels.add(new Label(Component.literal(text).getVisualOrderText(), x, y, rgb));
    }

    /** Text wrapped to the width, returning the Y below it. */
    private int wrapped(String text, int x, int y, int width, int rgb) {
        if (text.isEmpty()) {
            return y;
        }
        for (FormattedCharSequence line : this.font.split(Component.literal(text), width)) {
            this.labels.add(new Label(line, x, y, rgb));
            y += 10;
        }
        return y + 2;
    }

    /**
     * A grid of buttons for the markers, outlined while selected, returning the Y below it. A
     * pick lays the tab out again, so the summary follows it.
     */
    private int pickers(List<AbstractWidget> clipped, List<OpenBaseScreenPayload.Marker> markers, int x, int y,
            int perRow, int width, java.util.function.Consumer<String> pick, java.util.function.Predicate<String> on) {
        int gap = 4;
        int bw = (width - gap * (perRow - 1)) / perRow;
        Set<String> seen = new LinkedHashSet<>();
        int n = 0;
        for (OpenBaseScreenPayload.Marker m : markers) {
            if (!seen.add(m.id()) || n >= MAX_PICKER) {
                continue;
            }
            String id = m.id();
            Button b = this.button(clipped, shorten(id), x + (n % perRow) * (bw + gap), y + (n / perRow) * 18, bw, 16,
                    () -> {
                        pick.accept(id);
                        this.rebuildWidgets();
                    });
            this.highlights.add(new Highlight(b, () -> on.test(id)));
            n++;
        }
        return n == 0 ? y : y + ((n + perRow - 1) / perRow) * 18 + 2;
    }

    /**
     * Upstream hid the tab's widgets outside the scrolling area, but showed one partly scrolled off
     * its top over the tab buttons, where a click on a tab pressed it; the port hides any widget
     * not wholly inside the area.
     */
    private void clip(List<AbstractWidget> clipped) {
        int clipTop = this.clipTop();
        int clipBot = this.clipBottom();
        for (AbstractWidget w : clipped) {
            w.visible = w.getY() >= clipTop && w.getY() + w.getHeight() <= clipBot;
        }
    }

    private Button button(List<AbstractWidget> clipped, String label, int x, int y, int w, int h, Runnable action) {
        Button button = this.addRenderableWidget(Button.builder(Component.literal(label), b -> action.run())
                .bounds(x, y, w, h).build());
        clipped.add(button);
        return button;
    }

    private void showArrival(boolean arrival) {
        this.editingArrivalWps = arrival;
        this.rebuildWidgets();
    }

    private void editWaypoints(Runnable edit) {
        edit.run();
        this.rebuildWidgets();
    }

    private List<String> activeWaypoints() {
        return this.editingArrivalWps ? this.routeArrivalWaypoints : this.routeWaypoints;
    }

    /** The marker's ID after its type's tag, as {@code WP wp_1}. */
    private String tagged(String id) {
        MarkerType type = this.markerTypes.get(id);
        return (type == null ? "§c?" : type.tag()) + " §f" + id;
    }

    /** Upstream's landing ends: runway ends A, then B, then helipads. */
    private List<OpenBaseScreenPayload.Marker> arrivalEndpoints() {
        List<OpenBaseScreenPayload.Marker> endpoints = new ArrayList<>(this.base.runwayA());
        endpoints.addAll(this.base.runwayB());
        endpoints.addAll(this.base.helipads());
        return endpoints;
    }

    /**
     * Upstream: any waypoint, runway end, helipad or parking spot not yet in the list can be passed
     * through. The port lists each ID once.
     */
    private List<String> availableWaypoints(List<String> active) {
        Set<String> result = new LinkedHashSet<>();
        for (List<OpenBaseScreenPayload.Marker> markers : List.of(this.base.waypoints(), this.base.runwayA(),
                this.base.runwayB(), this.base.helipads(), this.base.parking())) {
            for (OpenBaseScreenPayload.Marker m : markers) {
                if (!active.contains(m.id())) {
                    result.add(m.id());
                }
            }
        }
        return new ArrayList<>(result);
    }

    // The Mission tab, as upstream's initMissionTab.

    /** The left edge of the Mission tab's centred three-column grid. */
    private int missionGridX() {
        return this.width / 2 - missionGridW() / 2;
    }

    private static int missionGridW() {
        return 3 * 58 + 2 * 4;
    }

    private int missionAircraftY(int sty) {
        return sty + 204;
    }

    private int missionDepartureY(int sty) {
        int acRows = Math.min((Math.min(this.base.aircraft().size(), MAX_AIRCRAFT) + 1) / 2, MAX_AIRCRAFT / 2);
        return this.missionAircraftY(sty) + acRows * 20 + 14;
    }

    private int routeRows() {
        return this.routes.isEmpty() ? 0 : Math.min((this.routes.size() + 1) / 2, MAX_ROUTE_PICK / 2);
    }

    private int missionArrivalY(int sty) {
        return this.missionDepartureY(sty) + this.routeRows() * 20 + 14;
    }

    private void saveMissionValues() {
        for (int i = 0; i < this.missionFields.size(); i++) {
            this.missionValues[i] = this.missionFields.get(i).getValue();
        }
    }

    private void initMissionTab() {
        this.saveMissionValues();
        this.missionFields.clear();
        this.aircraftButtons.clear();
        this.departureButtons.clear();
        this.arrivalButtons.clear();
        int cx = this.width / 2;
        int sty = this.clipTop() + 2 - this.scrollMission;
        int btnW = 58;
        int gap = 4;
        int gridW = missionGridW();
        int gx = this.missionGridX();
        List<AbstractWidget> clipped = new ArrayList<>();

        for (int i = 0; i < MISSION_TYPES.length; i++) {
            String type = MISSION_TYPES[i];
            boolean exclusive = "STRIKE".equals(type) || "FERRY".equals(type);
            this.missionTypeButtons[i] = this.button(clipped, type, gx + (i % 3) * (btnW + gap),
                    sty + 10 + (i / 3) * 20, btnW, 16, () -> this.toggleMission(type, exclusive));
        }
        for (int i = 0; i < WEAPONS.length; i++) {
            String weapon = WEAPONS[i];
            this.weaponButtons[i] = this.button(clipped, WEAPON_LABELS[i], gx + (i % 3) * (btnW + gap),
                    sty + 58 + (i / 3) * 20, btnW, 16, () -> {
                        if (!this.selectedWeapons.remove(weapon)) {
                            this.selectedWeapons.add(weapon);
                        }
                    });
        }

        int fy = sty + 112;
        int fw = (gridW - 8) / 2;
        for (int i = 0; i < this.missionValues.length; i++) {
            EditBox field = new EditBox(this.font, i % 2 == 0 ? gx : gx + fw + 8, fy + (i / 2) * 30, fw, 16,
                    Component.literal(MISSION_FIELD_LABELS[i]));
            field.setMaxLength(128);
            field.setValue(this.missionValues[i]);
            this.missionFields.add(this.addRenderableWidget(field));
            clipped.add(field);
        }

        int ay = this.missionAircraftY(sty);
        int abW = (gridW - gap) / 2;
        List<OpenBaseScreenPayload.Aircraft> aircraft = this.base.aircraft();
        for (int i = 0; i < aircraft.size() && i < MAX_AIRCRAFT; i++) {
            UUID id = aircraft.get(i).uuid();
            this.aircraftButtons.add(this.button(clipped, shorten(aircraft.get(i).name()),
                    gx + (i % 2) * (abW + gap), ay + (i / 2) * 20, abW, 16, () -> this.selectedAircraft = id));
        }

        int pky = this.missionDepartureY(sty);
        for (int i = 0; i < this.routes.size() && i < MAX_ROUTE_PICK; i++) {
            String rid = this.routes.get(i).routeId();
            this.departureButtons.add(this.button(clipped, shorten(rid), gx + (i % 2) * (abW + gap),
                    pky + (i / 2) * 20, abW, 16, () -> this.selectedRouteForDispatch =
                            this.selectedRouteForDispatch.equals(rid) ? "" : rid));
        }
        int ary = this.missionArrivalY(sty);
        for (int i = 0; i < this.routes.size() && i < MAX_ROUTE_PICK; i++) {
            String rid = this.routes.get(i).routeId();
            this.arrivalButtons.add(this.button(clipped, shorten(rid), gx + (i % 2) * (abW + gap),
                    ary + (i / 2) * 20, abW, 16, () -> this.selectedArrivalRouteId =
                            this.selectedArrivalRouteId.equals(rid) ? "" : rid));
        }

        int dispY = ary + this.routeRows() * 20 + 6;
        this.button(clipped, "Recall All", cx - 124, dispY, 80, 16,
                () -> ClientPacketDistributor.sendToServer(new RecallOrdersPayload(this.base.pos())));
        this.button(clipped, "Dispatch", cx - 40, dispY, 80, 16, this::dispatchOrder);
        this.orbitAttackButton = this.button(clipped, "Orbit Atk", cx + 44, dispY, 70, 16,
                () -> this.orbitAttackMode = !this.orbitAttackMode);

        this.clip(clipped);
    }

    /** Upstream cut a button's label to 13 characters and an ellipsis. */
    private static String shorten(String label) {
        return label.length() > 14 ? label.substring(0, 13) + "…" : label;
    }

    /** Upstream: STRIKE and FERRY are flown alone; the others combine. */
    private void toggleMission(String type, boolean exclusive) {
        if (exclusive) {
            if (!this.selectedMissionTypes.remove(type)) {
                this.selectedMissionTypes.clear();
                this.selectedMissionTypes.add(type);
            }
        } else {
            if (this.selectedMissionTypes.contains("STRIKE") || this.selectedMissionTypes.contains("FERRY")) {
                this.selectedMissionTypes.clear();
            }
            if (!this.selectedMissionTypes.remove(type)) {
                this.selectedMissionTypes.add(type);
            }
        }
    }

    /** Upstream: an arrival route that ends at a helipad lands vertically. */
    private boolean isVtolArrivalRoute(String routeId) {
        if (routeId.isEmpty()) {
            return false;
        }
        for (TaxiRoute r : this.routes) {
            if (r.routeId().equals(routeId)) {
                return this.isHelipad(r.runwayId());
            }
        }
        return false;
    }

    private void dispatchOrder() {
        if (this.selectedAircraft == null || this.selectedMissionTypes.isEmpty()) {
            return;
        }
        this.saveMissionValues();
        ClientPacketDistributor.sendToServer(new DispatchOrderPayload(this.base.pos(), this.selectedAircraft,
                this.selectedRouteForDispatch, List.copyOf(this.selectedMissionTypes), List.copyOf(this.selectedWeapons),
                parseDouble(this.missionValues[0], 0.0), parseDouble(this.missionValues[1], 0.0),
                parseDouble(this.missionValues[2], 300.0), parseDouble(this.missionValues[3], 80.0),
                parseInt(this.missionValues[4], 2), parseInt(this.missionValues[5], 600), this.orbitAttackMode,
                this.isVtolArrivalRoute(this.selectedArrivalRouteId), this.selectedArrivalRouteId));
    }

    private static double parseDouble(String text, double fallback) {
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int parseInt(String text, int fallback) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @Override
    protected void setInitialFocus() {
        if (this.currentTab == 0) {
            if (this.routeIdField != null && this.routeIdField.visible) {
                this.setInitialFocus(this.routeIdField);
            }
        } else if (!this.missionFields.isEmpty() && this.missionFields.get(0).visible) {
            this.setInitialFocus(this.missionFields.get(0));
        }
    }

    // Route editing, as upstream.

    private void loadRoute(TaxiRoute r) {
        this.editingRouteId = r.routeId();
        if (this.routeIdField != null) {
            this.routeIdField.setValue(r.routeId());
        }
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

    private boolean isHelipad(String id) {
        return this.base.helipads().stream().anyMatch(m -> m.id().equals(id));
    }

    /** What still stops the route saving, then warnings about the base's markers. */
    private List<String> problems() {
        List<String> problems = new ArrayList<>();
        if (this.editingRouteId.trim().isEmpty()) {
            problems.add("§cTo save: name the route.");
        }
        if (this.selectedParkingId.isEmpty()) {
            problems.add("§cTo save: pick a parking spot (1).");
        }
        if (this.selectedRunwayId.isEmpty() && !this.isHelipad(this.selectedParkingId)) {
            problems.add("§cTo save: pick a runway or helipad (2).");
        }
        for (String id : this.duplicateIds) {
            problems.add(duplicateNote(id));
        }
        return problems;
    }

    private static String duplicateNote(String id) {
        return "§eTwo markers share the ID §f" + id + "§e. Rename one on its marker screen.";
    }

    private void saveRoute() {
        String rid = this.routeIdField != null ? this.routeIdField.getValue().trim() : "";
        if (this.selectedRunwayId.isEmpty() && this.isHelipad(this.selectedParkingId)) {
            this.selectedRunwayId = this.selectedParkingId;
        }
        for (String problem : this.problems()) {
            if (problem.startsWith("§cTo save")) {
                this.status = "§cNot saved:";
                this.rebuildWidgets();
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
                this.selectedRouteIdx = i;
                found = true;
                break;
            }
        }
        if (!found) {
            this.routes.add(route);
            this.selectedRouteIdx = this.routes.size() - 1;
        }
        this.status = "§aSaved " + rid + ".";
        this.rebuildWidgets();
    }

    private void deleteRoute() {
        if (this.selectedRouteIdx < 0 || this.selectedRouteIdx >= this.routes.size()) {
            return;
        }
        TaxiRoute route = this.routes.remove(this.selectedRouteIdx);
        ClientPacketDistributor.sendToServer(new TaxiRouteActionPayload(this.base.pos(), true, route));
        this.selectedRouteIdx = -1;
        this.status = "§aDeleted " + route.routeId() + ".";
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
        int step = scrollY > 0.0 ? -20 : 20;
        if (this.currentTab == 0) {
            this.scrollRoutes = Math.clamp(this.scrollRoutes + step, 0, Math.max(0, this.routesContentH - visH));
        } else {
            this.scrollMission = Math.clamp(this.scrollMission + step, 0, Math.max(0, MISSION_CONTENT_H - visH));
        }
        this.rebuildWidgets();
        return true;
    }

    /** Upstream's Tab moved between the Mission tab's fields, and did nothing on the Taxi Routes tab. */
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_TAB) {
            if (this.currentTab == 1) {
                this.cycleFocus();
            }
            return true;
        }
        return super.keyPressed(event);
    }

    private void cycleFocus() {
        List<EditBox> visible = this.missionFields.stream().filter(f -> f.visible).toList();
        if (visible.isEmpty()) {
            return;
        }
        int next = (visible.indexOf(this.getFocused()) + 1) % visible.size();
        this.setFocused(visible.get(next));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // Drawing, as upstream's drawScreen.

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int cx = this.width / 2;
        graphics.centeredText(this.font, "§eBase: §f" + this.base.baseId(), cx, 7, 0xFFFFFFFF);
        Button tab = this.currentTab == 0 ? this.routesTab : this.missionTab;
        graphics.fill(tab.getX(), tab.getY() + tab.getHeight(), tab.getX() + tab.getWidth(),
                tab.getY() + tab.getHeight() + 2, 0xFFFFAA00);
        if (this.currentTab == 0) {
            this.drawRoutesTab(graphics);
        } else {
            this.drawMissionTab(graphics);
        }
        super.extractRenderState(graphics, mouseX, mouseY, a);
    }

    private void highlight(GuiGraphicsExtractor graphics, Button b, int color) {
        if (b.visible) {
            graphics.fill(b.getX() - 1, b.getY() - 1, b.getX() + b.getWidth() + 1, b.getY() + b.getHeight() + 1, color);
        }
    }

    private void label(GuiGraphicsExtractor graphics, String text, int x, int y, int rgb) {
        graphics.text(this.font, text, x, y, 0xFF000000 | rgb);
    }

    private void drawRoutesTab(GuiGraphicsExtractor graphics) {
        int clipTop = this.clipTop();
        int clipBot = this.clipBottom();
        for (Highlight h : this.highlights) {
            if (h.on().getAsBoolean()) {
                this.highlight(graphics, h.button(), HIGHLIGHT);
            }
        }
        graphics.enableScissor(0, clipTop, this.width, clipBot);
        for (Label l : this.labels) {
            graphics.text(this.font, l.text(), l.x(), l.y(), 0xFF000000 | l.rgb(), true);
        }
        int y = this.problemsY;
        for (String problem : this.problems()) {
            for (FormattedCharSequence line : this.font.split(Component.literal(problem), LEFT_W)) {
                graphics.text(this.font, line, this.leftX(), y, 0xFFFFFFFF, true);
                y += 10;
            }
            y += 3;
        }
        graphics.disableScissor();
        this.drawScrollbar(graphics, clipTop, clipBot, this.routesContentH, this.scrollRoutes);
    }

    /** Upstream's drawMissionTab. */
    private void drawMissionTab(GuiGraphicsExtractor graphics) {
        int clipTop = this.clipTop();
        int clipBot = this.clipBottom();
        graphics.enableScissor(0, clipTop, this.width, clipBot);
        int sty = clipTop + 2 - this.scrollMission;
        int gx = this.missionGridX();
        int gridW = missionGridW();

        this.label(graphics, "Mission Types:", gx, sty, GREY);
        this.label(graphics, "Weapons:", gx, sty + 48, GREY);
        int fy = sty + 112;
        int fw = (gridW - 8) / 2;
        for (int i = 0; i < MISSION_FIELD_LABELS.length; i++) {
            this.label(graphics, MISSION_FIELD_LABELS[i], i % 2 == 0 ? gx : gx + fw + 8, fy - 12 + (i / 2) * 30, GREY);
        }

        int ay = this.missionAircraftY(sty);
        this.label(graphics, "Select Aircraft:", gx, ay - 12, GREY);
        List<OpenBaseScreenPayload.Aircraft> aircraft = this.base.aircraft();
        if (aircraft.isEmpty()) {
            this.label(graphics, "§7(no aircraft nearby)", gx, ay + 3, 0x666666);
        }
        for (int i = 0; i < this.aircraftButtons.size(); i++) {
            if (aircraft.get(i).uuid().equals(this.selectedAircraft)) {
                this.highlight(graphics, this.aircraftButtons.get(i), HIGHLIGHT);
            }
        }

        int pky = this.missionDepartureY(sty);
        this.label(graphics, "§fDeparture route §7(optional):", gx, pky - 12, GREY);
        if (this.routes.isEmpty()) {
            this.label(graphics, "§7(none: make one on the Taxi Routes tab)", gx, pky + 3, 0x666666);
        }
        this.highlightRoute(graphics, this.departureButtons, this.selectedRouteForDispatch);

        int ary = this.missionArrivalY(sty);
        this.label(graphics, "§fArrival route §7(optional):", gx, ary - 12, GREY);
        if (this.routes.isEmpty()) {
            this.label(graphics, "§7(none)", gx, ary + 3, 0x666666);
        }
        this.highlightRoute(graphics, this.arrivalButtons, this.selectedArrivalRouteId);
        if (this.selectedArrivalRouteId.isEmpty()) {
            this.label(graphics, "§7← §f(fallback)", gx + gridW + 4, ary + 3, 0x888888);
        }

        for (int i = 0; i < MISSION_TYPES.length; i++) {
            if (this.selectedMissionTypes.contains(MISSION_TYPES[i])) {
                this.highlight(graphics, this.missionTypeButtons[i], TOGGLED);
            }
        }
        for (int i = 0; i < WEAPONS.length; i++) {
            if (this.selectedWeapons.contains(WEAPONS[i])) {
                this.highlight(graphics, this.weaponButtons[i], TOGGLED);
            }
        }
        if (this.orbitAttackMode && this.orbitAttackButton != null) {
            this.highlight(graphics, this.orbitAttackButton, TOGGLED);
        }
        graphics.disableScissor();
        // Upstream drew this note just below the scrolling area, where its clipping hid it.
        if (this.isVtolArrivalRoute(this.selectedArrivalRouteId)) {
            this.label(graphics, "§b⬆ VTOL landing §7(the arrival route ends at a helipad)", 6, this.height - 22,
                    0x55FFFF);
        }
        this.drawScrollbar(graphics, clipTop, clipBot, MISSION_CONTENT_H, this.scrollMission);
    }

    private void highlightRoute(GuiGraphicsExtractor graphics, List<Button> buttons, String selected) {
        for (int i = 0; i < buttons.size(); i++) {
            if (this.routes.get(i).routeId().equals(selected)) {
                this.highlight(graphics, buttons.get(i), HIGHLIGHT);
            }
        }
    }

    /** Upstream's departure line: parking spot, waypoints, take-off end. */
    private String departureSequence() {
        StringBuilder seq = new StringBuilder("§fDeparture: ")
                .append(this.selectedParkingId.isEmpty() ? "§c?" : "§a" + this.selectedParkingId);
        for (String wp : this.routeWaypoints) {
            seq.append(" §7→ §f").append(wp);
        }
        seq.append(" §7→ ").append(this.selectedRunwayId.isEmpty() ? "§c?" : "§b" + this.selectedRunwayId);
        if (!this.selectedRunwayBId.isEmpty()) {
            seq.append(" §7(take-off towards §b").append(this.selectedRunwayBId).append("§7)");
        }
        return seq.toString();
    }

    /** Upstream's arrival note: the arrival waypoints, or that the departure ones are flown in reverse. */
    private String arrivalSequence() {
        String entry = this.selectedArrivalRunwayId.isEmpty() ? this.selectedRunwayId : this.selectedArrivalRunwayId;
        String start = "§fArrival: " + (entry.isEmpty() ? "§c?" : "§b" + entry);
        String end = " §7→ " + (this.selectedParkingId.isEmpty() ? "§c?" : "§a" + this.selectedParkingId);
        StringBuilder arr = new StringBuilder(start);
        if (this.routeArrivalWaypoints.isEmpty() && !this.routeWaypoints.isEmpty()) {
            List<String> reversed = new ArrayList<>(this.routeWaypoints);
            Collections.reverse(reversed);
            for (String wp : reversed) {
                arr.append(" §7→ §f").append(wp);
            }
            return arr.append(end).append(" §8(departure reversed)").toString();
        }
        for (String wp : this.routeArrivalWaypoints) {
            arr.append(" §7→ §f").append(wp);
        }
        return arr.append(end).toString();
    }

    private void drawScrollbar(GuiGraphicsExtractor graphics, int top, int bot, int contentH, int scroll) {
        int visH = bot - top;
        int maxScroll = Math.max(0, contentH - visH);
        if (maxScroll <= 0) {
            return;
        }
        int bx = this.width - 7;
        int barH = Math.max(16, visH * visH / contentH);
        int barY = top + (int) ((long) scroll * (visH - barH) / maxScroll);
        graphics.fill(bx, top, bx + 4, bot, 0x44FFFFFF);
        graphics.fill(bx, barY, bx + 4, barY + barH, 0xCCCCCCCC);
    }
}
