package io.github.mcheliceport.client.particle;

import net.minecraft.world.phys.Vec3;

/** A short cosmetic leak after a newly observed wing loss, independent of gameplay fuel. */
public final class AirframeFuelLook {
    public static final int WINDOW=90;
    private AirframeFuelLook() {}
    public static int age(long now,Long born) {
        if(born==null||now<born)return -1;
        long elapsed=now-born;
        return elapsed<0||elapsed>=WINDOW?-1:(int)elapsed;
    }
    public static float envelope(int age) {
        return age<0||age>=WINDOW?0:Math.min(1,age/3F)*Math.min(1,(WINDOW-age)/24F);
    }
    /** Predicted passage through a nearby existing engine fire; no entity or world query. */
    public static int ignitionTick(Vec3 gap,Vec3 relativeMotion) {
        if(!Double.isFinite(gap.lengthSqr())||!Double.isFinite(relativeMotion.lengthSqr()))return -1;
        for(int t=2;t<=12;t++) {
            double x=gap.x+relativeMotion.x*t,y=gap.y+relativeMotion.y*t-.01*t*t,z=gap.z+relativeMotion.z*t;
            if(x*x+y*y+z*z<4)return t;
        }
        return -1;
    }
}
