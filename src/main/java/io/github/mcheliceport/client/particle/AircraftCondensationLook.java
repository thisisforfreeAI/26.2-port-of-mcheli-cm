package io.github.mcheliceport.client.particle;

/** Cosmetic wet-turn proxy. Never feeds forces back into the aircraft simulation. */
public final class AircraftCondensationLook {
    private long previous=Long.MIN_VALUE;private float yaw,pitch,strength;
    public float tick(long now,float heading,float attitude,double speed,float rain,boolean airborne,boolean enabled){
        if(!Float.isFinite(heading)||!Float.isFinite(attitude)||!Double.isFinite(speed)||!Float.isFinite(rain)||!enabled){previous=Long.MIN_VALUE;strength=0;return 0;}
        if(now==previous)return strength;
        if(previous==Long.MIN_VALUE||now<previous||now-previous>3){previous=now;yaw=heading;pitch=attitude;strength=0;return 0;}
        double rate=Math.hypot(Math.IEEEremainder(heading-yaw,360),Math.IEEEremainder(attitude-pitch,360))/(now-previous);
        previous=now;yaw=heading;pitch=attitude;
        float target=airborne&&rate<30?(float)(Math.clamp((speed-.5)/.8,0,1)*Math.clamp((rate*speed-.35)/1.25,0,1)*Math.clamp((rain-.2)/.6,0,1)):0;
        strength+=Math.clamp(target-strength,-.12F,.08F);return strength;
    }
    public float strength(){return strength;}
    public static int period(boolean low,int states,int pressure){return (low?4:2)*Math.max(1,(states+(low?5:11))/(low?6:12))*(1+Math.clamp(pressure,0,3));}
}
