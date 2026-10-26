package io.github.mcheliceport.damage;

/** A component's condition: OK above half health, DAMAGED down to the last point, DISABLED at none. */
public enum ComponentState {
    OK,
    DAMAGED,
    DISABLED;

    public static ComponentState of(float health) {
        if (health <= 0.0F) {
            return DISABLED;
        }
        return health > 0.5F ? OK : DAMAGED;
    }
}
