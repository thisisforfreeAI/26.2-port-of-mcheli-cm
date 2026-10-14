package io.github.mcheliceport.wingman.block;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/**
 * What a wingman marker block marks. Ported from upstream
 * {@code com.norwood.mcheli.wingman.block.MarkerType}.
 */
public enum MarkerType implements StringRepresentable {
    /** A base: the anchor that groups the other markers. */
    BASE,
    /** A parking spot. */
    PARKING,
    /** Runway end A: where take-off starts and landing ends. */
    RUNWAY_A,
    /** Runway end B: where take-off ends and landing starts. */
    RUNWAY_B,
    /** A waypoint in the air; only its X and Z are used. */
    WAYPOINT,
    /** A vertical take-off and landing spot for helicopters and VTOL aircraft. */
    HELIPAD,
    /** Points the nose direction for a helipad, as runway end B does for a runway. */
    HELIPAD_B;

    @Override
    public String getSerializedName() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
