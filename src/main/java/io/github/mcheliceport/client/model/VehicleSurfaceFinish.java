package io.github.mcheliceport.client.model;

import java.util.Locale;

/** Conservative material cues from explicitly named content parts, baked with crease shading. */
public final class VehicleSurfaceFinish {
    public enum Material { PAINT, RUBBER, VENT, GLASS }
    private VehicleSurfaceFinish() {}
    public static Material material(String name) {
        String s=name.toLowerCase(Locale.ROOT).replace("$", "");
        if(s.startsWith("wheel")||s.startsWith("track")||s.startsWith("tread")||s.startsWith("tire")||s.startsWith("tyre"))return Material.RUBBER;
        if(s.startsWith("vent")||s.startsWith("exhaust")||s.startsWith("intake")||s.startsWith("nozzle"))return Material.VENT;
        if(s.startsWith("glass")||s.startsWith("window")||s.startsWith("windscreen")||s.startsWith("windshield"))return Material.GLASS;
        return Material.PAINT;
    }
    public static float shade(Material material,float crease) {
        if(!Float.isFinite(crease))return 1;
        return switch(material) {
            case PAINT -> Math.clamp(1+(crease-1)*(crease<1?1.15F:1.25F),.86F,1.04F);
            case RUBBER -> Math.clamp(crease*.94F,.84F,.96F);
            case VENT -> Math.clamp(crease*.91F,.82F,.94F);
            case GLASS -> Math.clamp(1+(crease-1)*.35F, .96F,1.025F);
        };
    }
    /** Local orientation cues baked once. No view-dependent glare, colored tint or new pass. */
    public static float shade(Material material, float crease, float nx, float ny, float nz) {
        float base = shade(material, crease);
        double length = Math.sqrt((double)nx*nx + (double)ny*ny + (double)nz*nz);
        if (!Double.isFinite(length) || length < 1e-6) return base;
        float up = (float)(ny / length);
        // Rubber stays matte; glass gets a restrained upper edge, painted undersides lose a little light.
        float cue = switch (material) {
            case GLASS -> .022F * up;
            case PAINT -> .014F * Math.min(0, up);
            case RUBBER -> -.012F * Math.abs(up);
            case VENT -> .012F * Math.min(0, up);
        };
        return Math.clamp(base + cue, .82F, 1.04F);
    }

}
