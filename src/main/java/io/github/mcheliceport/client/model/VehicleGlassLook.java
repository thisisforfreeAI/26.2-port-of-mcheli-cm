package io.github.mcheliceport.client.model;

/** Repeated strikes spread cracks instead of stacking identical opaque marks. */
public final class VehicleGlassLook {
    private VehicleGlassLook(){}
    public static float radius(float previous,float hit){return Math.min(.6F,Math.max(.18F,Math.max(hit,previous*1.5F)));}
    public static int stage(int previous){return Math.min(4,previous+1);}
}
