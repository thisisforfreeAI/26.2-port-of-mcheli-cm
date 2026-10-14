package io.github.mcheliceport.content.weapon;

/**
 * Trigger of a bomb dropped as a mine. Ported from upstream
 * {@code com.norwood.mcheli.weapon.MCH_MineFuze}.
 *
 * <p>Any value other than {@link #NONE} makes the bomb settle on the ground, arm after a delay and
 * detonate when its trigger comes within range.
 */
public enum MineFuze {
    /** Not a mine: ordinary bomb behaviour. */
    NONE,
    /** Detonates when a player who is not sneaking comes within range. */
    PROXIMITY_PLAYER,
    /** Detonates when a McHeli vehicle comes within range. */
    PROXIMITY_VEHICLE
}
