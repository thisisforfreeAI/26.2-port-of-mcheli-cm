package io.github.mcheliceport.entity.aircraft;

import net.minecraft.util.RandomSource;

/**
 * Damage and pick-up rules of upstream's {@code MCH_EntityAircraft#attackEntityFrom}:
 * {@code calculateDamage}, {@code evaluatePlayerAttack} and {@code dropItemIfNeeded}, on plain
 * values so the tests need no world.
 */
public final class AircraftDamage {

    private AircraftDamage() {
    }

    /** The damage types upstream tells apart, by its 1.12 damage type names. */
    public enum Kind {
        /** {@code lava}: random ×2 to ×9, ignores armour, 2-tick cooldown. */
        LAVA,
        /** {@code explosion*}: 1-tick cooldown. */
        EXPLOSION,
        /** {@code onFire}: ignores armour, 10-tick cooldown. */
        ON_FIRE,
        /** {@code inFire} and {@code cactus}: ignored entirely. */
        IGNORED,
        /** {@code player} (a melee hit by a player). */
        PLAYER,
        OTHER
    }

    /** The content's armour values ({@code ArmorMinDamage}, {@code ArmorMaxDamage}, {@code ArmorDamageFactor}). */
    public record Armor(float minDamage, float maxDamage, float damageFactor, boolean invulnerable) {
    }

    /** The damage left after armour, and the hit cooldown ({@code timeSinceHit}) it sets. */
    public record Result(float damage, int timeSinceHit) {
    }

    /**
     * Upstream {@code calculateDamage} after the external damage factors.
     *
     * @param hitBoxFactor the damage factor of the hit box that was hit ({@code lastBBDamageFactor}), 1 for the main box
     * @param timeSinceHit the current cooldown, returned unchanged when the type sets none
     */
    public static Result calculate(Kind kind, float damage, float hitBoxFactor, Armor armor, RandomSource rand,
            int timeSinceHit) {
        if (armor.invulnerable()) {
            return new Result(0.0F, timeSinceHit);
        }
        switch (kind) {
            case LAVA -> {
                damage *= rand.nextInt(8) + 2;
                timeSinceHit = 2;
            }
            case EXPLOSION -> timeSinceHit = 1;
            case ON_FIRE -> timeSinceHit = 10;
            default -> {
            }
        }

        if (kind != Kind.LAVA && kind != Kind.ON_FIRE) {
            damage = Math.min(damage, armor.maxDamage());
            if (hitBoxFactor <= 1.0F) {
                damage *= hitBoxFactor;
            }
            damage *= armor.damageFactor();
            damage -= armor.minDamage();
            if (damage <= 0.0F) {
                return new Result(0.0F, timeSinceHit);
            }
            if (hitBoxFactor > 1.0F) {
                damage *= hitBoxFactor;
            }
        }
        return new Result(damage, timeSinceHit);
    }

    /**
     * Upstream {@code evaluatePlayerAttack}: whether a player's melee hit removes the vehicle (and
     * maybe drops its item) instead of damaging it.
     */
    public static boolean isPickUp(boolean creative, boolean creativeOnly, boolean preventingBroken,
            boolean breakableOnlyPickaxe, boolean holdingPickaxe, boolean ridden) {
        if (creative) {
            return true;
        }
        if (!creativeOnly && !preventingBroken) {
            if (breakableOnlyPickaxe) {
                return holdingPickaxe;
            }
            return !ridden;
        }
        return false;
    }

    /** Upstream {@code dropItemIfNeeded}: whether a pick-up drops the vehicle's item. */
    public static boolean dropsItem(boolean creative, boolean sneaking, boolean dropItemInCreativeMode) {
        if (creative) {
            return dropItemInCreativeMode != sneaking;
        }
        return true;
    }
}
