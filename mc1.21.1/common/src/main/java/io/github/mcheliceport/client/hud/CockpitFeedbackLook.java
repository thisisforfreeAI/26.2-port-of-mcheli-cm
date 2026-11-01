package io.github.mcheliceport.client.hud;

/** Cosmetic cockpit presentation; never changes controls, aim or component health. */
public final class CockpitFeedbackLook {
    public static final int MAX_DROPS=28;
    public static final float MAX_SHIFT=.4F;
    private float wetness;
    private long previous=Long.MIN_VALUE;

    public record View(boolean enabled,boolean pilot,boolean firstPerson,boolean enclosed,
                       boolean closed,boolean remote,boolean destroyed) {
        public boolean visible(){return enabled&&pilot&&firstPerson&&enclosed&&closed&&!remote&&!destroyed;}
    }
    public float wetness(){return wetness;}
    public void reset(){wetness=0;previous=Long.MIN_VALUE;}
    public void tick(long now,boolean rain){
        if(now==previous)return;
        if(previous!=Long.MIN_VALUE&&(now<previous||now-previous>3))wetness=0;
        previous=now;wetness+=Math.clamp((rain?1:0)-wetness,-.035F,.06F);
    }
    public static float shift(double time,int seed,double throttle,float damage){
        if(!Double.isFinite(time)||!Double.isFinite(throttle)||!Float.isFinite(damage))return 0;
        double strength=(.18+.22*Math.clamp(damage,0,1))*Math.clamp(throttle,0,1);
        return (float)(Math.sin(time*1.7+seed*.31)*strength);
    }
    /** Only the small backlight flickers. Warning glyphs keep a readable minimum intensity. */
    public static float backlight(long tick,int seed,float damage,boolean powered){
        if(!powered)return .25F;
        return damage>.35F&&Math.floorMod(tick+seed*13L,97)<3?.45F:1;
    }
    public static float health(float value){return Float.isFinite(value)?Math.clamp(value,0,1):1;}
    public static int drops(int quality,int pressure,float wetness){
        return Math.clamp(Math.round((quality==0?12:quality==2?28:20)*health(wetness)/(1+Math.clamp(pressure,0,3))),0,MAX_DROPS);
    }
    public record Drop(float x,float y,float size,float tail){}
    /** Deterministic rivulets, with a clear central aiming area and smooth cycle boundaries. */
    public static Drop drop(double tick,int seed,int index,double speed){
        double phase=((tick+(long)index*43+Math.floorMod(seed,101))/(85+index%5*13));phase-=Math.floor(phase);
        int hash=Integer.rotateLeft(seed^(index+1)*0x9e3779b9,11);
        float x=.06F+Math.floorMod(hash,880)*.001F;
        if(x>.33F&&x<.67F)x=x<.5F?x-.22F:x+.22F;
        float y=(float)(.10+.73*phase*phase);
        float size=(float)Math.sin(Math.PI*phase);
        float tail=(float)(1+Math.clamp(Double.isFinite(speed)?speed:0,0,3)*1.3*phase);
        return new Drop(x,y,size,tail);
    }
}
