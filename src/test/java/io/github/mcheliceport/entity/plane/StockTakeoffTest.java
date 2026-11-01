package io.github.mcheliceport.entity.plane;

import static org.junit.jupiter.api.Assertions.*;

import io.github.mcheliceport.content.ContentRegistries;
import io.github.mcheliceport.content.ContentTestSupport;
import io.github.mcheliceport.content.aircraft.PlaneInfo;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Full-throttle ground rolls of stock planes through the shared flight model, with the entity's
 * on-ground pitch hold. Tail-draggers rest nose-up and must lift off like the jets do.
 */
class StockTakeoffTest {
    private static ContentRegistries.Snapshot stock;

    @BeforeAll static void load() {
        stock = ContentTestSupport.load(ContentTestSupport.stockPack());
    }

    /** Ticks until the plane climbs ten blocks above the runway, or -1 when it never does. */
    static int takeoffTicks(PlaneInfo info) {
        PlaneFlightModel.Params p = PlaneFlightModel.Params.of(info, 1.0);
        PlaneFlightModelTest.State s = new PlaneFlightModelTest.State();
        s.p = p; s.y = 0.0; s.throttle = 1.0; s.nearbyGround = true; s.grounded = true;
        s.groundSolid = true; s.pitch = p.onGroundPitch();
        for (int tick = 0; tick < 1200; tick++) {
            // WingedAircraftEntity#onUpdateAngles holds the ground pitch while within three blocks.
            if (s.blockWithin(3, -3) && Math.abs(s.pitch) < 40.0F) {
                PlaneFlightModel.groundPitch(s, p, 0.97F);
            }
            PlaneFlightModel.physics(s, p, 0.0F, 0.0F, false, false, false);
            if (s.y > 10.0) {
                return tick;
            }
        }
        return -1;
    }

    @Test void tailDraggersRestNoseUp() {
        for (String name : List.of("a6m2", "p-51d", "an2", "fi167", "pzl-m18")) {
            assertTrue(stock.plane().get(name).onGroundPitch < -10.0F, name + " should sit nose-up on the ground");
        }
    }

    @Test void ww2PropPlanesTakeOff() {
        for (String name : List.of("a6m2", "p-51d", "an2", "fi167", "pzl-m18", "n1k1", "f1m", "macchi_m33")) {
            int ticks = takeoffTicks(stock.plane().get(name));
            assertTrue(ticks > 0 && ticks < 400, name + " took " + ticks + " ticks to climb off the runway");
        }
    }

    @Test void jetsKeepTheirTakeoffRoll() {
        // Recorded before the ground-pitch sign fix; these jets have no ground pitch, so must not change.
        assertEquals(List.of(JET_TICKS), List.of(List.of("f22a", "f-15e", "f-35a", "a-10", "mig29").stream()
                .map(n -> takeoffTicks(stock.plane().get(n))).toArray(Integer[]::new)));
    }

    private static final Integer[] JET_TICKS = {76, 76, 76, 76, 76};
}
