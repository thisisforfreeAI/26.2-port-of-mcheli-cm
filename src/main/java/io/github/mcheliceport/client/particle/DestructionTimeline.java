package io.github.mcheliceport.client.particle;

/** Pure deterministic visual timing; never applies gameplay damage. */
public final class DestructionTimeline {
    public static final int TRACK=20,COVER=36,WING=56,TURRET=96,TAIL=110;
    private DestructionTimeline(){}
    public static boolean turret(String model,int seed,int explosiveAmmunition){
        return (model.equals("t-90")||model.equals("t-72")||model.equals("t-80")||model.equals("t-64")||model.equals("t84"))
                &&explosiveAmmunition>=8&&Math.floorMod(seed,8)==0;
    }
    public static float collapse(int age){return Math.clamp((age-80)/70F,0,1);}
    public static float spin(float age,float speed){float t=Math.clamp(age,0,140);return speed*(t-t*t/280F);}
    public static boolean secondary(int age,int seed){return age==144+Math.floorMod(seed,7)||age==220+Math.floorMod(seed,11);}
    public static float ember(int age){return age<120?0:Math.clamp((1100-age)/700F,0,1);}
}
