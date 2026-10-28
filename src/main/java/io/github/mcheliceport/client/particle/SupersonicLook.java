package io.github.mcheliceport.client.particle;

/** Cosmetic transonic crossing and observer-pass timing, independent of flight forces. */
public final class SupersonicLook {
    public record Event(boolean cone,boolean boom){public static final Event NONE=new Event(false,false);}
    private long previous=Long.MIN_VALUE,nextCone,nextBoom;
    private double previousMach,previousAhead;
    private int below;
    private boolean armed;
    public void reset(){previous=Long.MIN_VALUE;below=0;armed=false;nextCone=nextBoom=0;}
    public Event tick(long now,double speed,double machOne,double ahead,double distance,boolean stable,boolean active){
        if(!active||!Double.isFinite(speed)||speed<0||!Double.isFinite(machOne)||machOne<=0||!Double.isFinite(ahead)||!Double.isFinite(distance)||distance>512){reset();return Event.NONE;}
        if(now==previous)return Event.NONE;
        double mach=speed/machOne;
        if(!stable||previous==Long.MIN_VALUE||now<previous||now-previous>3){
            previous=now;previousMach=mach;previousAhead=ahead;armed=mach<.9;below=0;return Event.NONE;
        }
        below=mach<.9?Math.min(10,below+1):0;if(below>=10)armed=true;
        boolean cone=armed&&previousMach<.98&&mach>=.98&&now>=nextCone;
        boolean boom=mach>=1.02&&previousAhead>0&&ahead<=0&&now>=nextBoom;
        if(cone){armed=false;nextCone=now+200;}if(boom)nextBoom=now+200;
        previous=now;previousMach=mach;previousAhead=ahead;return cone||boom?new Event(cone,boom):Event.NONE;
    }
    /** Physical sound travel in world blocks (one block/metre), distinct from flight's cosmetic scale. */
    public static int delay(double distance){return Double.isFinite(distance)?(int)Math.clamp(Math.ceil(Math.max(0,distance)/17.15),1,30):30;}
    /** Short clock corrections/interpolation gaps are normal; a large spatial discontinuity is not. */
    public static boolean continuous(long elapsed,double travel,double machOne){return elapsed>=1&&elapsed<=3&&Double.isFinite(travel)&&travel<=Math.max(8,machOne*6)*elapsed;}
    public static float volume(double distance){return Double.isFinite(distance)?(float)Math.clamp(.9*(1-Math.max(0,distance)/600),0,.9):0;}
}
