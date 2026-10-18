package io.github.mcheliceport.wingman;

import io.github.mcheliceport.net.OpenMarkerScreenPayload;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/** What the wingman code asks of the client. The client registers it; on a server nothing is registered. */
public final class WingmanClientHooks {

    private static volatile @Nullable Consumer<OpenMarkerScreenPayload> markerScreen;

    private WingmanClientHooks() {
    }

    public static void setMarkerScreen(@Nullable Consumer<OpenMarkerScreenPayload> opener) {
        markerScreen = opener;
    }

    /** Upstream {@code PacketOpenMarkerGui#onReceive}: opens the marker screen. */
    public static void openMarkerScreen(OpenMarkerScreenPayload payload) {
        Consumer<OpenMarkerScreenPayload> opener = markerScreen;
        if (opener != null) {
            opener.accept(payload);
        }
    }
}
