package io.github.mcheliceport.radar;

import java.util.List;

/**
 * Upstream {@code MCH_RWRThreatClientTracker}: the client's copy of the threat table of the vehicle
 * it rides. A table the server has not refreshed for 1.5 seconds (it re-sends a receiver's every
 * second) is dropped, so the scope goes when the player leaves the vehicle or the server stops
 * sending.
 */
public final class RwrClientTracker {

    private static final long STALE_MS = 1_500L;
    private static volatile Table table;

    private RwrClientTracker() {
    }

    public static void update(int receiverId, boolean fitted, List<RwrThreat> threats) {
        table = new Table(receiverId, fitted, List.copyOf(threats), System.currentTimeMillis());
    }

    private static Table current(int receiverId) {
        Table t = table;
        if (t == null || t.receiverId != receiverId || System.currentTimeMillis() - t.receivedMillis > STALE_MS) {
            return null;
        }
        return t;
    }

    /** Whether the server says the vehicle {@code receiverId} has a warning receiver. */
    public static boolean isFitted(int receiverId) {
        Table t = current(receiverId);
        return t != null && t.fitted;
    }

    /** The threats to the vehicle {@code receiverId}, empty when there is no fresh table. */
    public static List<RwrThreat> threats(int receiverId) {
        Table t = current(receiverId);
        return t != null ? t.threats : List.of();
    }

    public static void clear() {
        table = null;
    }

    private record Table(int receiverId, boolean fitted, List<RwrThreat> threats, long receivedMillis) {
    }
}
