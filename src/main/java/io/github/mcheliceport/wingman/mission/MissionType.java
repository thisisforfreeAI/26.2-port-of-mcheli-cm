package io.github.mcheliceport.wingman.mission;

import java.util.HashSet;
import java.util.Set;

/** Upstream's {@code MissionType}. Several can be ordered together, except Strike and Ferry. */
public enum MissionType {
    /** Combat air patrol: circles the area and attacks what comes. */
    CAP,
    /** Close air support: circles the area and attacks the ground. */
    CAS,
    /** A number of attack passes, then home. */
    STRIKE,
    /** Escort: circles and attacks. */
    ESCORT,
    /** Circles the area, counts the hostile mobs and reports them on the way home. */
    RECON,
    /** Flies to the area without fighting, then home. */
    FERRY;

    /** Strike and Ferry cannot be combined with another type. */
    public boolean isExclusive() {
        return this == STRIKE || this == FERRY;
    }

    /** The type's default weapons. */
    public Set<String> defaultWeapons() {
        return new HashSet<>(switch (this) {
            case CAP, ESCORT -> Set.of("aamissile", "gun");
            case CAS -> Set.of("cas", "rocket", "gun");
            case STRIKE -> Set.of("asmissile", "bomb", "gun");
            case RECON -> Set.of("gun");
            case FERRY -> Set.<String>of();
        });
    }

    public String displayName() {
        return switch (this) {
            case STRIKE -> "Strike";
            case ESCORT -> "Escort";
            case RECON -> "Recon";
            case FERRY -> "Ferry";
            default -> this.name();
        };
    }
}
