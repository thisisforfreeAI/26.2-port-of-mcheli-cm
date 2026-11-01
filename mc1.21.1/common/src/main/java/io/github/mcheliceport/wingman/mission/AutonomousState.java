package io.github.mcheliceport.wingman.mission;

/** Upstream's {@code AutonomousState}: the steps of an autonomous flight. */
public enum AutonomousState {
    /** Not autonomous (following or idle). */
    NONE,
    /** Taxiing from the parking spot to the runway. */
    TAXI_OUT,
    /** Waiting on the runway until the nose lines up. */
    ALIGN,
    /** Accelerating along the runway. */
    TAKEOFF_ROLL,
    /** Climbing to the cruise altitude. */
    CLIMB,
    /** Flying to a route node. */
    ENROUTE,
    /** Attacking around a route node. */
    ATTACK,
    /** Circling at a route node. */
    LOITER,
    /** Descending to the landing circuit. */
    DESCEND,
    /** Downwind leg, parallel to the runway the other way. */
    CIRCUIT_DOWNWIND,
    /** Base leg, towards the final approach. */
    CIRCUIT_BASE,
    /** Final approach down the glide slope. */
    CIRCUIT_FINAL,
    /** Rolling out after touchdown. */
    LANDING,
    /** Taxiing from the runway to the parking spot. */
    TAXI_IN,
    /** Parked. */
    PARKED,
    /** Flying straight to the mission area. */
    TRANSIT_TO,
    /** On station: circling, escorting or reconnoitring. */
    ON_STATION,
    /** Flying a strike pass. */
    STRIKE_PASS,
    /** Returning to base. */
    RTB,
    /** A helicopter or VTOL aircraft climbing straight up from a helipad. */
    VTOL_TAKEOFF,
    /** A helicopter or VTOL aircraft landing straight down on a helipad. */
    VTOL_LAND
}
