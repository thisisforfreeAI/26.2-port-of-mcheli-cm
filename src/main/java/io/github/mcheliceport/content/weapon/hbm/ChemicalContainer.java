package io.github.mcheliceport.content.weapon.hbm;

/** Chemical cloud payload, configured by the weapon YAML's {@code NTM.Chemical} block. */
public record ChemicalContainer(double speed, int count, ChemType type) {

    public ChemicalContainer {
        if (!Double.isFinite(speed) || speed < 0 || count < 0)
            throw new IllegalArgumentException("Chemical Speed must be finite and nonnegative; Count must be nonnegative");
        java.util.Objects.requireNonNull(type, "Chemical Type");
    }

    public enum ChemType {
        CHLORINE,
        CLOUD,
        PINK_CLOUD,
        ORANGE
    }
}
