package io.github.mcheliceport.client.sound;

import net.minecraft.world.phys.Vec3;

/** Relative 3D motion, with no initial distance spike and no own-cockpit Doppler. */
public final class FlightAudioLook {
    private Vec3 source,listener;
    private float doppler=1;
    public float value(){return doppler;}
    public void reset(){source=listener=null;doppler=1;}
    private static boolean finite(Vec3 v){return v!=null&&Double.isFinite(v.lengthSqr());}
    public float tick(Vec3 at,Vec3 ear,boolean aboard,boolean enabled){
        if(!finite(at)||!finite(ear)){reset();return 1;}
        if(!enabled||aboard||source==null||listener==null||source.distanceToSqr(at)>32*32||listener.distanceToSqr(ear)>32*32) doppler=1;
        else {
            var relative=at.subtract(source).subtract(ear.subtract(listener));
            double closing=relative.dot(ear.subtract(at).normalize());
            doppler+=(pitch(closing)-doppler)*.25F;
        }
        source=at;listener=ear;return doppler;
    }
    public static float pitch(double closing){
        if(!Double.isFinite(closing))return 1;
        return (float)Math.clamp(17.15/Math.max(4.2875,17.15-closing),.85,1.15);
    }
    public static double range(float authored){return Float.isFinite(authored)?Math.clamp(authored,1,512):16;}
    public static float edgeFade(double distance,double range){
        double t=Math.clamp((range-distance)/Math.max(4,range*.2),0,1);return (float)(t*t*(3-2*t));
    }
    public static float rearGain(double forwardCosine,boolean jet){return jet?(float)(.84+.21*(1-Math.clamp(forwardCosine,-1,1))*.5):1;}
    public static int voiceCap(int quality,int pressure){return Math.max(8,(quality==0?12:quality==2?32:24)/(1+Math.clamp(pressure,0,3)));}
}
