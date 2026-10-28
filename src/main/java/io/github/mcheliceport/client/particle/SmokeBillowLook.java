package io.github.mcheliceport.client.particle;

/** Bounded smoke envelopes. No world queries, extra particles or gameplay changes. */
public final class SmokeBillowLook {
    private SmokeBillowLook() {}
    private static float smooth(float t){t=Math.clamp(t,0,1);return t*t*(3-2*t);}
    public static float surge(long tick,int seed){
        double phase=Math.floorMod(tick,24000)*.075+Math.floorMod(seed,997)*.13;
        return (float)(.74+.18*Math.sin(phase)+.08*Math.sin(phase*.43+1.7));
    }
    public static boolean quiet(float density,long tick,int seed){
        return density<.35F&&surge(tick,seed)<.62F;
    }
    public static int wreckLife(boolean wisp,float fire){return wisp?38:68+Math.round(Math.clamp(fire,0,1)*20);}
    public static float growth(float age,int life,boolean wisp){
        float t=Math.clamp(age/Math.max(1,life),0,1);
        return (wisp?.30F:.45F)+.55F*smooth((float)Math.sqrt(Math.min(1,t/.8F)))*(wisp?.86F:1);
    }
    public static float lift(float birthFire,float age,boolean wisp){
        float hot=Math.clamp(birthFire,0,1);
        return (wisp?.07F:.09F+hot*.055F)*(1-.32F*smooth(age/90));
    }
    public static float windShare(float height){return .35F+1.1F*smooth(Math.max(0,height)/7);}
    public static float gray(float fire,float age,int life,float variation){
        float cool=smooth(age/Math.max(1,life));
        return Math.clamp(.91F-.10F*Math.clamp(fire,0,1)+cool*.05F+(variation-.5F)*.06F,.74F,.99F);
    }
    public static float amber(float fire,float age,float height){
        return Math.clamp(fire,0,1)*(1-smooth(age/24))*(1-smooth(Math.max(0,height)/3));
    }
    public static float curl(long tick,int seed,float height){
        return (float)Math.sin(Math.floorMod(tick,24000)*.055+Math.floorMod(seed,997)*.13+height*.48);
    }
    /** Small acceleration; neighboring billows share a time/height field instead of independent orbits. */
    public static double blastCurl(long tick,float phase,double height,int age,boolean base){
        return Math.sin(Math.floorMod(tick,24000)*.055+phase+height*.45)
                * (base?.0005:.0012) * (1-smooth((age-15)/90F));
    }
}
