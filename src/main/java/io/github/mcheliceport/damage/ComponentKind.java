package io.github.mcheliceport.damage;

/** The vehicle systems M9 damages separately. */
public enum ComponentKind {
    ENGINE(1.0F),
    MAIN_ROTOR(1.2F),
    TAIL_ROTOR(1.2F),
    FUEL_TANK(0.8F);

    /** Share of a hit's hull damage the component takes. */
    public final float damageShare;

    ComponentKind(float damageShare) {
        this.damageShare = damageShare;
    }
}
