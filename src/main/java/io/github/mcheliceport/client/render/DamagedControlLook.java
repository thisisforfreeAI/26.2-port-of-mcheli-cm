package io.github.mcheliceport.client.render;

/** Bounded cosmetic hinge angles in radians; no input or flight authority changes. */
public final class DamagedControlLook {
    private DamagedControlLook() {}
    public static boolean detached(float health){return Float.isFinite(health)&&health<=.12F;}
    public static float strength(float health,float hullDamage) {
        if(!Float.isFinite(health))health=1;if(!Float.isFinite(hullDamage))hullDamage=0;
        return Math.max(Math.clamp((.75F-health)/.75F,0,1),Math.clamp((hullDamage-.55F)/.45F,0,1)*.28F);
    }
    public static float angle(float strength,double speed,double tick,int seed,boolean rudder,float distanceFade) {
        if(!Double.isFinite(tick))return 0;
        float s=Float.isFinite(strength)?Math.clamp(strength,0,1):0;
        if(s==0)return 0;
        float motion=Double.isFinite(speed)?(float)Math.clamp(speed/.65,0,1):0;
        float fade=Float.isFinite(distanceFade)?Math.clamp(distanceFade,0,1):0;
        double wave=Math.sin(Math.IEEEremainder(tick,Math.PI*2/.71)*.71+seed*.31)*.62
                +Math.sin(Math.IEEEremainder(tick,Math.PI*2/1.13)*1.13+seed*.17)*.38;
        double sag=(rudder?(seed%2==0?4:-4):-12)*s*s;
        return (float)Math.toRadians(sag+wave*5*s*motion*fade);
    }
}
