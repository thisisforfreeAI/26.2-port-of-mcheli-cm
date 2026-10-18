package io.github.mcheliceport.wingman.wingman;

import java.util.UUID;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * Upstream's {@code WingmanEntry}: one wingman's leader, formation slot and attack orders. The
 * autonomous mission fields come with the mission increment.
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

    public WingmanEntry(@Nullable Entity leader, int slot) {
        this.state = WingmanState.FOLLOWING;
        this.leader = leader;
        this.formationSlot = slot;
    }

    /** Upstream {@code isAutonomous}: flying a mission rather than following. Missions come later. */
    public boolean isAutonomous() {
        return false;
    }
}
