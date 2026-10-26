package io.github.mcheliceport.client.particle;

/** Bounded world-space shedding, so an aircraft outruns the smoke it leaves behind. */
public final class DamageTrailLook {
    private DamageTrailLook() {}
    public static double inherited(double velocity){return Double.isFinite(velocity)?Math.clamp(velocity,-4,4)*.18:0;}
    public static float radius(float severity,double speed){
        if(!Double.isFinite(speed))speed=0;
        return .8F+Math.clamp(severity,0,1)*.65F+(float)Math.min(.8,Math.max(0,speed))*.2F;
    }
    public static int lifetime(float severity,boolean wisp){return 36+(int)(Math.clamp(severity,0,1)*24)-(wisp?12:0);}
    public static boolean wisp(long tick,int id){return Math.floorMod(tick/3+id,3)==2;}
    public static float gray(float severity,boolean wisp){return .5F-Math.clamp(severity,0,1)*.34F+(wisp?.1F:0);}
}
