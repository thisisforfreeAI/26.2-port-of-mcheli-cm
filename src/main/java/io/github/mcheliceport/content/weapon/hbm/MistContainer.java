package io.github.mcheliceport.content.weapon.hbm;

/** Gas or fluid mist clouds. Upstream {@code MistContainer}. */
public class MistContainer {

    /** HBM fluid name, upper case; {@code None} for no mist. */
    public String fluidType = "None";
    public int cloudCount = 1;
    public float width = 10;
    public float height = 5;
    public int areaSpread = 0;
    public int lifetime = 80;
    public int lifetimeVariance = 0;
}
