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
    private float contrail,temperature=.8F;private long temperatureAt=Long.MIN_VALUE;
    public boolean sampleTemperature(long now){return temperatureAt==Long.MIN_VALUE||now<temperatureAt||now-temperatureAt>=20;}
    public void temperature(long now,float value){temperatureAt=now;temperature=Float.isFinite(value)?value:.8F;}
    public float temperature(){return temperature;}
    public float contrail(){return contrail;}
    public void cold(float target,boolean enabled){if(!enabled){contrail=0;return;}contrail+=Math.clamp(target-contrail,-.10F,.06F);}
    /** Height is scaled to Minecraft's vertical space, with warmer biomes requiring more altitude. */
    public static float coldTarget(double height,float temperature,double speed,double throttle,boolean airborne,boolean powered){
        if(!Double.isFinite(height)||!Float.isFinite(temperature)||!Double.isFinite(speed)||!Double.isFinite(throttle)||!airborne||!powered)return 0;
        double threshold=160+Math.clamp(temperature,-.5F,2)*24;
        return (float)(Math.clamp((height-threshold)/80,0,1)*Math.clamp((speed-.6)/.7,0,1)*Math.clamp((throttle-.3)/.4,0,1));
    }
    public static float contrailRadius(double speed,int period,int samples,boolean far){
        if(!Double.isFinite(speed)||samples<1)return 1.25F;
        return (float)Math.clamp(speed*period/samples/2.1,1.25,far?4:2.5);
    }
    public static boolean retainFarState(int alreadyRetained){return alreadyRetained<64;}
    public static boolean stateAllowed(boolean far,int count){return count<128&&(!far||count<64);}
    public float strength(){return strength;}
    public static int period(boolean low,int states,int pressure){return (low?4:2)*Math.max(1,(states+(low?5:11))/(low?6:12))*(1+Math.clamp(pressure,0,3));}
}
