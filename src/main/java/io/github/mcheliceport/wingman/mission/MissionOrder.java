package io.github.mcheliceport.wingman.mission;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Upstream's {@code MissionOrder}: an order from the base screen's Mission tab, kept in the
 * aircraft's wingman entry while it flies it.
 */
public class MissionOrder {

    public final Set<MissionType> missionTypes = new LinkedHashSet<>();
    public final Set<String> weapons = new LinkedHashSet<>();
    /** The centre of the mission area. */
    public double targetX;
    public double targetZ;
    /** Seconds on station before returning; 0 for no limit. */
    public int timeLimitSeconds = 600;
    /** The base it leaves from and returns to. */
    public String baseId = "";
    /** The radius it circles the area at, for CAP and CAS. */
    public double orbitRadius = 300.0;
    /** The altitude (Y) it cruises at. */
    public double cruiseAlt = 80.0;
    public int strikePasses = 2;
    /** Upstream kept a ferry destination base, which nothing read. */
    public String ferryDestBase = "";
    /** Attacks while keeping its orbit, firing sideways (an AC-130), instead of attack passes. */
    public boolean orbitAttack;
    /** A VTOL plane lands vertically on a helipad (set when the arrival route ends at one). */
    public boolean useVstol;
    /** The taxi route used after landing; empty to find one. */
    public String arrivalRouteId = "";

    public int timeLimitTicks() {
        return this.timeLimitSeconds * 20;
    }

    public boolean hasType(MissionType type) {
        return this.missionTypes.contains(type);
    }

    public boolean isExclusive() {
        return this.missionTypes.stream().anyMatch(MissionType::isExclusive);
    }
}
