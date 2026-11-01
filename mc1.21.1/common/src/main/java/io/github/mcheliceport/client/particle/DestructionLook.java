package io.github.mcheliceport.client.particle;

import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Bounded presentation only: no damage, ammunition consumption, terrain edits or entity movement. */
public final class DestructionLook {
    public enum Kind {TANK, HELICOPTER, PLANE, OTHER}
    public record Settle(float drop,float roll,float pitch){}
    private DestructionLook(){}

    public static float scale(Kind kind){return switch(kind){case TANK->1.35F;case HELICOPTER->1.15F;case PLANE->1.55F;default->1;};}
    public static Vec3 direction(Vec3 outlet,Kind kind){
        if(outlet==null||!Double.isFinite(outlet.lengthSqr())||outlet.lengthSqr()<1e-6)outlet=new Vec3(0,.8,-.3);
        return outlet.normalize().scale(kind==Kind.PLANE?1:.55).add(0,kind==Kind.TANK?.8:.3,0).normalize();
    }
    /** Unequal small/large pops, deterministic without consuming the world's random stream. */
    public static float burstStrength(int seed,int age){return .48F+Math.floorMod(seed*31L+age*17L,13)/24F;}
    public static float pulse(float age){return !Float.isFinite(age)||age<0||age>=10?0:(1-age/10)*(1-age/10);}
    public static float interior(float age,float sinceFlash,int seed){
        if(!Float.isFinite(age)||age<0)return 0;
        float fire=Math.clamp(1-age/360,0,1);
        float flicker=.88F+.12F*(float)Math.sin(age*.21+(seed&255));
        return Math.min(.6F,fire*.18F*flicker+pulse(sinceFlash)*.45F);
    }
    /** Delayed give-way, a small damped rebound, then an entirely still final pose. */
    public static Settle settle(float age,Kind kind,int seed){
        if(!Float.isFinite(age)||age<=80)return new Settle(0,0,0);
        float t=Math.clamp((age-80)/70,0,1),s=t*t*(3-2*t);
        float kick=age>=88&&age<128?(float)(Math.sin((age-88)*.22)*Math.exp(-(age-88)*.1)):.0F;
        float lean=switch(kind){case TANK->.042F;case HELICOPTER->.065F;case PLANE->.03F;default->.025F;};
        float side=(seed&1)==0?1:-1;
        return new Settle(-.1F*s+Math.max(0,kick)*.012F,(lean*s+kick*.01F)*side,-.018F*s-kick*.006F);
    }
    public static Vector3f panelSpin(int seed,float span){
        if(!Float.isFinite(span)||span<=0||span>3)return new Vector3f();
        float speed=.08F/Math.max(.7F,span),side=(seed&1)==0?1:-1;
        return new Vector3f(speed*side,speed*.45F,-speed*.65F*side);
    }
}
