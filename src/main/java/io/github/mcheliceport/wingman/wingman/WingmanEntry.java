package io.github.mcheliceport.wingman.wingman;

import io.github.mcheliceport.wingman.mission.AutonomousState;
import io.github.mcheliceport.wingman.mission.MissionNode;
import io.github.mcheliceport.wingman.mission.MissionOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * Upstream's {@code WingmanEntry}: one wingman's leader, formation slot and attack orders, and the
 * state of a mission it flies on its own: an order from a base's Mission tab, or a list of route
 * nodes from the mission planner or {@code /wingman mission assign}.
 */
public class WingmanEntry {

    public static final int ATK_NONE = 0;
    public static final int ATK_MANUAL = 1;
    public static final int ATK_AUTO = 2;

    public WingmanState state;
    public @Nullable Entity leader;
    public int formationSlot;

    public int attackMode = ATK_NONE;
    public @Nullable UUID manualTargetId;
    public @Nullable UUID currentAutoTarget;
    public int weaponSeat;
    /** The weapon type to fire ({@code gun}, {@code aamissile}...), or null to try every type. */
    public @Nullable String weaponType;

    /** The orbit angle around a missile target, in degrees. */
    public double orbitAngle;

    /** A tick counter for the periodic log lines. */
    public int diagTick;

    // ---- autonomous flight ----

    public AutonomousState autoState = AutonomousState.NONE;
    /** The ticks spent in the current step; negative while waiting after a vertical landing. */
    public int missionNodeTimer;
    /** Where the mission steers the aircraft. */
    public double autoTargetX;
    public double autoTargetY;
    public double autoTargetZ;

    /** The route nodes of a planner mission, or null when not flying one. */
    public @Nullable List<MissionNode> mission;
    /** The index of the node being flown. */
    public int missionIndex;

    /** The mission order, or null when not flying one. */
    public @Nullable MissionOrder order;
    /** Ticks on station. */
    public int orderTimer;
    /**
     * The angle of the mission orbit, in radians. Upstream shared {@link #orbitAngle} with the attack
     * orbit, which turns it in degrees, so an attack on station threw the mission orbit around.
     */
    public double missionOrbitAngle;
    public int strikePassesRemaining;
    /** The parking spot or helipad the aircraft left from or is taxiing to. */
    public String assignedParkingId = "";
    /**
     * Set by {@code /wingman order park}: the aircraft waits on {@link #assignedParkingId} for its
     * next order, so the entry is kept without a leader.
     */
    public boolean parkingHold;
    /**
     * The team of the player who sent it on its order or route, which it fights for without a
     * leader (NpcBattles); "" for none.
     */
    public String teamName = "";
    /** The taxi route the order named for departure. */
    public String departureRouteId = "";
    /** The marker IDs of the taxi in progress, and the next one's index. */
    public List<String> taxiWpQueue = new ArrayList<>();
    public int taxiWpIndex;
    /** The hostile mobs a reconnaissance counted in the area. */
    public int reconMobCount;
    /** Why it is returning, for the log. */
    public String rtbReason = "";

    /** Whether a VTOL plane should be in hover mode (nozzles down); set by the mission. */
    public boolean vtolHoverMode;
    /** The plane's nozzle state: 0 forward, 1 turning, 2 down. */
    public int vtolNozzleMode;
    /** Whether the hover switch was sent for the current {@link #vtolHoverMode}. */
    public boolean vtolOnSent;
    /** The position before the aircraft moved this tick, which the taxi push starts from. */
    public double taxiPreX;
    public double taxiPreZ;
    /** The roll-out speed after touchdown, decaying to the taxi speed; 0 before touchdown. */
    public double landingRollSpeed;
    /** The heading held while parked, or NaN for none. */
    public float parkingHeadingYaw = Float.NaN;
    /** The height the take-off roll is held down to, or 0. */
    public double groundRollY;
    /** Whether a helicopter returning below a raised helipad climbs over the spot it holds. */
    public boolean climbHold;
    public double climbHoldX;
    public double climbHoldZ;

    public WingmanEntry(@Nullable Entity leader, int slot) {
        this.state = WingmanState.FOLLOWING;
        this.leader = leader;
        this.formationSlot = slot;
    }

    /** An entry for an aircraft that flies a mission without a leader. */
    public WingmanEntry() {
        this(null, 0);
    }

    /** Upstream {@code isAutonomous}: flying a planner mission or an order rather than following. */
    public boolean isAutonomous() {
        return this.autoState != AutonomousState.NONE && this.mission != null || this.order != null;
    }

    public boolean hasOrder() {
        return this.order != null;
    }

    /** The planner mission's current node, or null when it has none left. */
    public @Nullable MissionNode currentNode() {
        List<MissionNode> nodes = this.mission;
        return nodes == null || this.missionIndex >= nodes.size() ? null : nodes.get(this.missionIndex);
    }

    /** Upstream {@code advanceMission}: on to the next node, its timer reset. */
    public void advanceMission() {
        this.missionIndex++;
        this.missionNodeTimer = 0;
    }

    /** Starts a planner mission from its first node. */
    public void startMission(List<MissionNode> nodes) {
        this.parkingHold = false;
        this.mission = List.copyOf(nodes);
        this.missionIndex = 0;
        this.missionNodeTimer = 0;
        this.autoState = AutonomousState.ENROUTE;
    }
}
