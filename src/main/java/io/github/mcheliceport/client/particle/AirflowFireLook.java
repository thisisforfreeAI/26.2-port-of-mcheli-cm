package io.github.mcheliceport.client.particle;

import net.minecraft.world.phys.Vec3;

/** Presentation in world space: backward slip in flight, buoyant fire when stationary. */
public final class AirflowFireLook {
    private AirflowFireLook() {}

    public static Vec3 motion(Vec3 velocity) {
        if (!Double.isFinite(velocity.x) || !Double.isFinite(velocity.y) || !Double.isFinite(velocity.z)) return Vec3.ZERO;
        double largest=Math.max(Math.abs(velocity.x),Math.max(Math.abs(velocity.y),Math.abs(velocity.z)));
        if(largest>3)velocity=velocity.scale(3/largest);
        double speed=velocity.length();
        return speed>3?velocity.scale(3/speed):velocity;
    }
    public static Vec3 axis(Vec3 velocity) {
        Vec3 motion=motion(velocity);
        Vec3 direction=motion.scale(-.85).add(0,.22,0);
        return direction.lengthSqr()<1.0E-6 ? new Vec3(0,1,0) : direction.normalize();
    }
    public static Vec3 velocity(Vec3 motion) {
        Vec3 bounded=motion(motion);
        return bounded.scale(.72).add(axis(bounded).scale(.09));
    }
    public static float strength(float severity) {
        return Float.isFinite(severity)?Math.clamp((severity-.55F)/.45F,0,1):0;
    }
    public static int lifetime(Vec3 velocity) {
        return 20-(int)(8*Math.min(1,motion(velocity).length()/1.25));
    }
    public static float radius(float strength,Vec3 velocity) {
        float fire=Float.isFinite(strength)?Math.clamp(strength,0,1):0;
        return .32F+.5F*fire+(float)Math.min(.16,motion(velocity).length()*.16);
    }
}
