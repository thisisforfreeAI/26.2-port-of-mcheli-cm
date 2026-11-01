package io.github.mcheliceport.client.particle;

import net.minecraft.world.phys.Vec3;

/** Small outward fans, biased by the incoming shot's reflection rather than an isotropic ball. */
public final class VehicleShrapnelLook {
    private VehicleShrapnelLook(){}
    public static Vec3 velocity(Vec3 incoming,Vec3 normal,int seed,int index,boolean cannon){
        var n=normal.normalize();var reflected=incoming.normalize().subtract(n.scale(2*incoming.normalize().dot(n)));
        var tangent=n.cross(Math.abs(n.y)<.9?new Vec3(0,1,0):new Vec3(1,0,0)).normalize();
        var up=n.cross(tangent);
        double angle=Math.floorMod((long)seed*1664525+index*1013904223L,65536)*Math.PI*2/65536;
        var velocity=reflected.scale(cannon?.3:.18).add(n.scale(.12)).add(tangent.scale(Math.sin(angle)*.14)).add(up.scale(Math.cos(angle)*.14));
        double outward=velocity.dot(n);if(outward<.08)velocity=velocity.add(n.scale(.08-outward));
        return velocity;
    }
}
