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

    public MarkerType next() {
        MarkerType[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }

    /** The coloured name in chat messages. */
    public String displayName() {
        return switch (this) {
            case BASE -> "\u00a76[Base]";
            case PARKING -> "\u00a7e[Parking]";
            case RUNWAY_A -> "\u00a7a[Runway-A]";
            case RUNWAY_B -> "\u00a7b[Runway-B]";
            case WAYPOINT -> "\u00a7d[Waypoint]";
            case HELIPAD -> "\u00a79[Helipad]";
            case HELIPAD_B -> "\u00a73[Helipad-B]";
        };
    }

    /** The name on the marker screen's buttons. */
    public String shortName() {
        return switch (this) {
            case BASE -> "Base";
            case PARKING -> "Parking";
            case RUNWAY_A -> "Runway-A";
            case RUNWAY_B -> "Runway-B";
            case WAYPOINT -> "Waypoint";
            case HELIPAD -> "Helipad";
            case HELIPAD_B -> "Helipad-B";
        };
    }

    /** One line on what the marker is for, under the marker screen's type buttons. */
    public String description() {
        return switch (this) {
            case BASE -> "The airfield's anchor. Name it, then set it as the parent base of its other markers.";
            case PARKING -> "Where an aircraft waits, and where it starts and ends its taxi routes.";
            case RUNWAY_A -> "The runway end where take-off starts and landing ends.";
            case RUNWAY_B -> "The far runway end: where take-off ends and landing starts.";
            case WAYPOINT -> "A point a taxi route passes through. Only its X and Z are used.";
            case HELIPAD -> "A vertical take-off and landing spot for helicopters and VTOL aircraft.";
            case HELIPAD_B -> "Place it beside a helipad, in the direction aircraft should face there.";
        };
    }

    /** The start of the type's automatic IDs, as {@code p} in {@code p_1}. */
    public String idPrefix() {
        return switch (this) {
            case BASE -> "base";
            case PARKING -> "p";
            case RUNWAY_A -> "ra";
            case RUNWAY_B -> "rb";
            case WAYPOINT -> "wp";
            case HELIPAD -> "hp";
            case HELIPAD_B -> "hpb";
        };
    }

    /** A short tag for the type in lists that mix types. */
    public String tag() {
        return switch (this) {
            case BASE -> "\u00a76BASE";
            case PARKING -> "\u00a7eP";
            case RUNWAY_A -> "\u00a7aRA";
            case RUNWAY_B -> "\u00a7bRB";
            case WAYPOINT -> "\u00a7dWP";
            case HELIPAD -> "\u00a79HP";
            case HELIPAD_B -> "\u00a73HPB";
        };
    }

    /** A landing spot: {@code HELIPAD_B} only gives the heading. */
    public boolean isHelipad() {
        return this == HELIPAD;
    }

    /** An end of a take-off or landing route. */
    public boolean isRunwayEndpoint() {
        return this == RUNWAY_A || this == RUNWAY_B || this == HELIPAD;
    }

    /** Upstream read unknown names as {@code PARKING}. */
    public static MarkerType byName(String name) {
        for (MarkerType type : values()) {
            if (type.name().equals(name)) {
                return type;
            }
        }
        return PARKING;
    }
}
