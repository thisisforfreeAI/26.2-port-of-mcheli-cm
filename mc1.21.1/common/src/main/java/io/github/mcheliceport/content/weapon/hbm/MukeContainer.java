package io.github.mcheliceport.content.weapon.hbm;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/** Mini-nuke ("Muke") settings. Upstream {@code MukeContainer}, without the HBM calls. */
public class MukeContainer {

    public boolean miniNuke = true;
    public boolean safe = false;
    public float blastRadius;
    public float killRadius;
    public float radiationLevel = 1F;
    public String particle = "Muke";
    public int shrapnelCount = 25;
    public int resolution = 64;
    /** HBM {@code ExAttrib} names. */
    public List<String> attributes = Arrays.asList("FIRE", "NOPARTICLE", "NOSOUND", "NODROP", "NOHURT");

    /**
     * Returns a new container with one of upstream's presets (SAFE, TOTS, LOW, MEDIUM, HIGH), or null
     * if the name is not a preset. Upstream shared one instance per preset; the port hands out copies
     * because containers are mutable.
     */
    public static @Nullable MukeContainer preset(String name) {
        MukeContainer muke = new MukeContainer();
        switch (name.toUpperCase(Locale.ROOT).trim()) {
            case "SAFE" -> {
                muke.safe = true;
                muke.killRadius = 45F;
                muke.radiationLevel = 2F;
            }
            case "TOTS" -> {
                muke.blastRadius = 10F;
                muke.killRadius = 30F;
                muke.particle = "TinyTot";
                muke.shrapnelCount = 0;
                muke.resolution = 32;
                muke.radiationLevel = 1;
            }
            case "LOW" -> {
                muke.blastRadius = 15F;
                muke.killRadius = 45F;
                muke.radiationLevel = 2;
            }
            case "MEDIUM" -> {
                muke.blastRadius = 20F;
                muke.killRadius = 55F;
                muke.radiationLevel = 3;
            }
            case "HIGH" -> {
                muke.miniNuke = false;
                muke.blastRadius = 35;
                muke.shrapnelCount = 0;
            }
            default -> {
                return null;
            }
        }
        return muke;
    }
}
