package io.github.mcheliceport.multiplay;

import org.jspecify.annotations.Nullable;

/**
 * What spotting asks of the client: upstream's direct calls into {@code MCH_GuiTargetMarker}.
 * The client registers its implementation; on a server nothing is registered.
 */
public final class SpotClientHooks {

    /** The client's side of spotting. Only called on the client thread. */
    public interface Hooks {
        /** Upstream {@code addSpotedEntity}: marks the entities for {@code count} seconds. */
        void spotted(int count, int[] entityIds);

        /** Upstream {@code MCH_GuiTargetMarker#markPoint}: sets the mark point, or clears it at y 1000. */
        void markPoint(int x, int y, int z);
    }

    private static volatile @Nullable Hooks hooks;

    private SpotClientHooks() {
    }

    public static void set(@Nullable Hooks value) {
        hooks = value;
    }

    public static void spotted(int count, int[] entityIds) {
        Hooks h = hooks;
        if (h != null) {
            h.spotted(count, entityIds);
        }
    }

    public static void markPoint(int x, int y, int z) {
        Hooks h = hooks;
        if (h != null) {
            h.markPoint(x, y, z);
        }
    }
}
