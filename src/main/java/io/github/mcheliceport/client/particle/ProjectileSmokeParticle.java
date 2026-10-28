package io.github.mcheliceport.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

/** Cold motor smoke: small at the nozzle, wider and wind-bent after the projectile has passed. */
final class ProjectileSmokeParticle extends SmokeParticle {
    private final float radius;private final boolean wisp;
    ProjectileSmokeParticle(ClientLevel level,Vec3 at,Vec3 travel,ProjectileTrailLook.Profile profile,float radius,float weight,boolean wisp){
        super(level,at.x,at.y,at.z,DamageTrailLook.inherited(travel.x)*.25,DamageTrailLook.inherited(travel.y)*.25,DamageTrailLook.inherited(travel.z)*.25);
        this.radius=radius;this.wisp=wisp;enhanced=true;hasPhysics=false;
        useBillow(wisp?SmokeAtlas.WISPS:SmokeAtlas.EXPLOSION);
        lifetime=Math.max(16,(int)(profile.life()*(wisp?.65:1)));
        float gray=profile.cruise()?.84F:.96F;setColor(gray,gray,gray);
        setAlphaF((profile.cruise()?.28F:.64F)*weight*(wisp?.62F:1));
    }
    @Override public void tick(){
        xo=x;yo=y;zo=z;if(++age>=lifetime){remove();return;}
        frame=billowFrames?SmokeAtlas.frame(age,lifetime):Math.min(7,age*8/lifetime);
        xd=BlastSmokeMotion.advect(xd,BlastSmokeMotion.windX(level.getGameTime()),.95,age);
        zd=BlastSmokeMotion.advect(zd,BlastSmokeMotion.windZ(level.getGameTime()),.95,age);
        yd=yd*.96+.0007;move(xd,yd,zd);
    }
    @Override public float getQuadSize(float partial){return radius*ProjectileTrailLook.growth(age+partial,lifetime,wisp);}
}
