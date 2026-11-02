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
    /** Bigger billows grow gradually; short-lived wisps spread farther while losing opacity. */
    public static float growth(float age,int life,boolean wisp) {
        float t=(float)Math.clamp(age/Math.max(1,life)/(wisp?.65:.5),0,1);
        t=t*t*(3-2*t);return (wisp?.28F:.4F)+t*(wisp?.82F:.65F);
    }
    public static double lift(int age,int life,boolean wisp){return .018+.024*Math.clamp((double)age/Math.max(1,life),0,1)+(wisp?.015:0);}
    public static int period(double speed,double distanceSquared,int pressure,boolean low) {
        int base=low||distanceSquared>48*48?6:speed>.8&&distanceSquared<32*32?2:3;
        return base*(1+Math.clamp(pressure,0,3));
    }
    public static boolean emit(long tick,int id,float severity,int period){
        if(severity<=0||period<1||Math.floorMod(tick+id,period)!=0)return false;
        return severity>=.4F||VehicleAtmosphereLook.smokePulse(tick,id,severity)||Math.floorMod(tick+id,period*3)==0;
    }
}
