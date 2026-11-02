package io.github.mcheliceport.wingman;

import io.github.mcheliceport.net.OpenBaseScreenPayload;
import io.github.mcheliceport.net.OpenMarkerScreenPayload;
import io.github.mcheliceport.net.PlannerDataPayload;
import io.github.mcheliceport.net.WingmanPanelDataPayload;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/** What the wingman code asks of the client. The client registers it; on a server nothing is registered. */
public final class WingmanClientHooks {

    private static volatile @Nullable Consumer<OpenMarkerScreenPayload> markerScreen;
    private static volatile @Nullable Consumer<OpenBaseScreenPayload> baseScreen;
    private static volatile @Nullable Consumer<WingmanPanelDataPayload> wingmanPanel;
    private static volatile @Nullable Consumer<PlannerDataPayload> planner;

    private WingmanClientHooks() {
    }

    public static void setMarkerScreen(@Nullable Consumer<OpenMarkerScreenPayload> opener) {
        markerScreen = opener;
    }

    public static void setBaseScreen(@Nullable Consumer<OpenBaseScreenPayload> opener) {
        baseScreen = opener;
    }

    public static void setWingmanPanel(@Nullable Consumer<WingmanPanelDataPayload> opener) {
        wingmanPanel = opener;
    }

    public static void setPlanner(@Nullable Consumer<PlannerDataPayload> opener) {
        planner = opener;
    }

    /** Upstream {@code PacketPlannerData#onReceive}: opens the mission planner. */
    public static void openPlanner(PlannerDataPayload payload) {
        Consumer<PlannerDataPayload> opener = planner;
        if (opener != null) {
            opener.accept(payload);
        }
    }

    /** Upstream {@code PacketWingmanPanelData#onReceive}: opens the wingman panel. */
    public static void openWingmanPanel(WingmanPanelDataPayload payload) {
        Consumer<WingmanPanelDataPayload> opener = wingmanPanel;
        if (opener != null) {
            opener.accept(payload);
        }
    }

    /** Upstream {@code PacketOpenBaseGui#onReceive}: opens the base screen. */
    public static void openBaseScreen(OpenBaseScreenPayload payload) {
        Consumer<OpenBaseScreenPayload> opener = baseScreen;
        if (opener != null) {
            opener.accept(payload);
        }
    }

    /** Upstream {@code PacketOpenMarkerGui#onReceive}: opens the marker screen. */
    public static void openMarkerScreen(OpenMarkerScreenPayload payload) {
        Consumer<OpenMarkerScreenPayload> opener = markerScreen;
        if (opener != null) {
            opener.accept(payload);
        }
    }
}
