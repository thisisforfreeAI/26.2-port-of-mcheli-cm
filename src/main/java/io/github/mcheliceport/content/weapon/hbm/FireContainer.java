package io.github.mcheliceport.content.weapon.hbm;

/** Lingering fire. Upstream {@code FireContainer}. */
public class FireContainer {

    /** One of DIESEL, FIRE, BALEFIRE, PHOSPHORUS, OXY, BLACK. */
    public String fireType = "DIESEL";
    public int count = 1;
    public float width = 5.0F;
    public float height = 3.0F;
    public int areaSpread = 0;
    public int lifetime = 150;
    public int lifetimeVariance = 0;
    public boolean particles = true;
}
