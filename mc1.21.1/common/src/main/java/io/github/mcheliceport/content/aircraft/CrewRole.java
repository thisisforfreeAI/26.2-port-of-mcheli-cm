package io.github.mcheliceport.content.aircraft;

/**
 * M10b, a port addition: the job of one seat. A seat with no role set in its file takes one from
 * what it already is (see {@code entity.aircraft.CrewRoles}), so stock vehicles need no edits.
 */
public enum CrewRole {
    /** Flies the vehicle; its weapons, flares and the flight HUD. */
    PILOT,
    /** Its own turret weapons, camera and zoom. */
    GUNNER,
    /** Fires nothing; reloads the vehicle's guns faster and its reload key serves the emptiest gun. */
    LOADER,
    /** Marks targets for the rest of the crew; may fire its seat's weapons. */
    COMMANDER,
    /** Rides along. */
    PASSENGER;

    /** Whether a rider in this role may fire. */
    public boolean canFire() {
        return this != LOADER && this != PASSENGER;
    }

    /** One letter for the crew HUD. */
    public char letter() {
        return switch (this) {
            case PILOT -> 'P';
            case GUNNER -> 'G';
            case LOADER -> 'L';
            case COMMANDER -> 'C';
            case PASSENGER -> '-';
        };
    }
}
