package io.github.mcheliceport.client.sound;

import java.util.Locale;
import net.minecraft.world.phys.Vec3;

/** Predicts only close incoming passes. Does not change a projectile's trajectory. */
public final class ProjectileFlybyLook {
    public enum Kind {NONE,ROCKET,SHELL}
    private ProjectileFlybyLook(){}
    public static Kind kind(String type,boolean ntm,boolean bullet,int power,int explosion){
        if(ntm||type==null)return Kind.NONE;String key=type.toLowerCase(Locale.ROOT);
        if(key.equals("rocket")||key.endsWith("missile"))return Kind.ROCKET;
        return bullet&&key.startsWith("machinegun")&&(power>=10||explosion>0)?Kind.SHELL:Kind.NONE;
    }
    public static boolean incoming(Vec3 source,Vec3 motion,Vec3 listener,Kind kind){
        double speed=motion.lengthSqr();if(kind==Kind.NONE||!Double.isFinite(speed)||speed<.04||speed>32*32)return false;
        var toward=listener.subtract(source);if(!Double.isFinite(toward.lengthSqr()))return false;
        double time=toward.dot(motion)/speed;
        if(time<=0||time>6)return false;
        double radius=kind==Kind.ROCKET?12:5;
        return toward.lengthSqr()>radius*radius&&toward.subtract(motion.scale(time)).lengthSqr()<radius*radius;
    }
}
