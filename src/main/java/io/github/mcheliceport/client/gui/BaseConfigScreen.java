package io.github.mcheliceport.client.gui;

import io.github.mcheliceport.net.DispatchOrderPayload;
import io.github.mcheliceport.net.OpenBaseScreenPayload;
import io.github.mcheliceport.net.TaxiRouteActionPayload;
import io.github.mcheliceport.wingman.mission.TaxiRoute;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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
 * Upstream's {@code GuiBaseConfig}, opened by right-clicking a base marker. Its Taxi Routes tab
 * lists the base's routes on the left (New, Save, Delete), and on the right the route ID and
 * buttons picking its parking spot, runway ends or helipad, then its departure or arrival
 * waypoints in order. Its Mission tab orders one of the aircraft near the base on a mission: the
 * mission types and weapons, the target, orbit, cruise height, strike passes and time limit, and
 * the taxi routes to leave and return by; an arrival route ending at a helipad lands vertically.
 * Each tab scrolls with the mouse wheel. Upstream's labels, including its Japanese ones, are kept.
 */
public class BaseConfigScreen extends Screen {

    private static final int MAX_ROUTE_LIST = 8;
    private static final int MAX_PICKER = 99;
    private static final int MAX_ARR_RUNWAY_SEL = 8;
    /** Upstream's height of the Taxi Routes content, for scrolling. */
    private static final int ROUTES_CONTENT_H = 480;
    /** Upstream's height of the Mission content, for scrolling. */
    private static final int MISSION_CONTENT_H = 460;
    private static final int MAX_AIRCRAFT = 8;
    private static final int MAX_ROUTE_PICK = 8;
    private static final int HIGHLIGHT = 0xAAFFAA00;
    private static final int TOGGLED = 0xFFFFAA00;
    private static final String[] MISSION_TYPES = {"CAP", "CAS", "STRIKE", "ESCORT", "RECON", "FERRY"};
    private static final String[] WEAPON_LABELS = {"GUN", "AA Msl", "AS Msl", "CAS", "Rocket", "Bomb"};
    private static final String[] WEAPONS = {"gun", "aamissile", "asmissile", "cas", "rocket", "bomb"};
    private static final String[] MISSION_FIELD_LABELS = {"Target X:", "Z:", "Orbit R:", "Cruise Y:", "Passes:",
            "Limit(s):"};

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
        this.selectedWeapons.add("gun");
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
        this.routesTab = this.addRenderableWidget(Button.builder(Component.literal("Taxi Routes"), b -> this.selectTab(0))
                .bounds(cx - 82, this.height / 4 - 12, 80, 18).build());
        this.missionTab = this.addRenderableWidget(Button.builder(Component.literal("Mission"), b -> this.selectTab(1))
                .bounds(cx + 2, this.height / 4 - 12, 80, 18).build());
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

        this.clip(clipped);
        if (!this.routeIdField.visible) {
            this.routeIdField.setFocused(false);
        }
    }

    /**
     * Upstream hid the tab's widgets outside the scrolling area, but showed one partly scrolled off
     * its top over the tab buttons, where a click on a tab pressed it; the port hides that one too.
     */
    private void clip(List<AbstractWidget> clipped) {
        int clipTop = this.clipTop();
        int clipBot = this.clipBottom();
        for (AbstractWidget w : clipped) {
            w.visible = w.getY() >= clipTop && w.getY() < clipBot;
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
        int sty = this.clipTop() - this.scrollMission;
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
        int step = scrollY > 0.0 ? -14 : 14;
        if (this.currentTab == 0) {
            this.scrollRoutes = Math.clamp(this.scrollRoutes + step, 0, Math.max(0, ROUTES_CONTENT_H - visH));
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

    // Drawing, as upstream's drawScreen and drawRoutesTab.

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int cx = this.width / 2;
        int ty = this.height / 4;
        graphics.centeredText(this.font, "§eBase: §f" + this.base.baseId(), cx, ty - 26, 0xFFFFFFFF);
        Button tab = this.currentTab == 0 ? this.routesTab : this.missionTab;
        graphics.fill(tab.getX(), tab.getY() + tab.getHeight(), tab.getX() + tab.getWidth(),
                tab.getY() + tab.getHeight() + 2, 0xFFFFAA00);
        if (this.currentTab == 0) {
            this.drawRoutesTab(graphics, cx);
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
        this.drawScrollbar(graphics, clipTop, clipBot, ROUTES_CONTENT_H, this.scrollRoutes);
    }

    /** Upstream's drawMissionTab. */
    private void drawMissionTab(GuiGraphicsExtractor graphics) {
        int clipTop = this.clipTop();
        int clipBot = this.clipBottom();
        graphics.enableScissor(0, clipTop, this.width, clipBot);
        int sty = clipTop - this.scrollMission;
        int gx = this.missionGridX();
        int gridW = missionGridW();

        this.label(graphics, "Mission Types:", gx, sty, 0xAAAAAA);
        this.label(graphics, "Weapons:", gx, sty + 48, 0xAAAAAA);
        int fy = sty + 112;
        int fw = (gridW - 8) / 2;
        for (int i = 0; i < MISSION_FIELD_LABELS.length; i++) {
            this.label(graphics, MISSION_FIELD_LABELS[i], i % 2 == 0 ? gx : gx + fw + 8, fy - 12 + (i / 2) * 30,
                    0xAAAAAA);
        }

        int ay = this.missionAircraftY(sty);
        this.label(graphics, "Select Aircraft:", gx, ay - 12, 0xAAAAAA);
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
        this.label(graphics, "§f出発ルート §7(Departure, optional):", gx, pky - 12, 0xAAAAAA);
        if (this.routes.isEmpty()) {
            this.label(graphics, "§7(none — Routes tab で作成)", gx, pky + 3, 0x666666);
        }
        this.highlightRoute(graphics, this.departureButtons, this.selectedRouteForDispatch);

        int ary = this.missionArrivalY(sty);
        this.label(graphics, "§f帰着ルート §7(Arrival, optional):", gx, ary - 12, 0xAAAAAA);
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
            this.label(graphics, "§b⬆ VTOL着陸 §7(帰着ルートから自動)", 6, this.height - 22, 0x55FFFF);
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
