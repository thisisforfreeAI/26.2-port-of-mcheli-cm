package io.github.mcheliceport.content.weapon.hbm;

/** Chemical cloud payload. Upstream {@code ChemicalContainer}; no file format sets it yet. */
public record ChemicalContainer(double speed, int count, ChemType type) {

    public enum ChemType {
        CHLORINE,
        CLOUD,
        PINK_CLOUD,
        ORANGE
    }
}
