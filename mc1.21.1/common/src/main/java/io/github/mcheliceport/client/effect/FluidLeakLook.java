package io.github.mcheliceport.client.effect;

/** Cosmetic wetness/drying rules; never changes blocks, fuel or vehicle health. */
public final class FluidLeakLook {
    private FluidLeakLook() {}
    public static boolean parked(double speedSquared){return Double.isFinite(speedSquared)&&speedSquared<.0025;}
    public static float grow(float radius){return Math.min(.55F,radius+.035F);}
    public static int life(boolean coolant){return coolant?600:1200;}
    public static float alpha(long sinceBirth,long sinceWet,int life){
        return Math.clamp(sinceBirth/10F,0,1)*Math.clamp((life-sinceWet)/(life/3F),0,1)*.78F;
    }
    public static int tint(boolean coolant){return coolant?0x347C72:0x24201C;}
}
