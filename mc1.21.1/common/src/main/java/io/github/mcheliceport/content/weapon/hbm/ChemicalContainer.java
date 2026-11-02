package io.github.mcheliceport.content.weapon.hbm;

/** Chemical cloud payload, set by a weapon's {@code NTM.Chemical} block. Upstream {@code ChemicalContainer}. */
public record ChemicalContainer(double speed, int count, ChemType type) {

    public static final int DEFAULT_COUNT = 8;
    public static final double DEFAULT_SPEED = 0.2;

    /** The payload of a chemical weapon without a {@code Chemical} block: chlorine. */
    public static ChemicalContainer defaults() {
        return new ChemicalContainer(DEFAULT_SPEED, DEFAULT_COUNT, ChemType.CHLORINE);
    }

    public enum ChemType {
        CHLORINE,
        CLOUD,
        PINK_CLOUD,
        ORANGE
    }
}
