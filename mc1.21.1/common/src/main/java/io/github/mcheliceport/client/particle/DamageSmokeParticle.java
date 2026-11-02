package io.github.mcheliceport.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

/** World-space, non-glowing billows preserve the aircraft's path as older smoke drifts away. */
final class DamageSmokeParticle extends SmokeParticle {
    private final float radius;
    private final boolean wisp;
    private final int seed;
    DamageSmokeParticle(ClientLevel level,Vec3 at,Vec3 motion,float severity,float radius,boolean wisp,float pulse,int seed) {
        super(level,at.x,at.y,at.z,DamageTrailLook.inherited(motion.x),DamageTrailLook.inherited(motion.y),DamageTrailLook.inherited(motion.z));
        this.radius=radius;this.wisp=wisp;this.seed=seed;this.enhanced=true;this.hasPhysics=false;
        useBillow(wisp?SmokeAtlas.WISPS:random.nextFloat()<.35F?SmokeAtlas.EXPLOSION:SmokeAtlas.WRECK);
        this.lifetime=DamageTrailLook.lifetime(severity,wisp)+(int)(random.nextFloat()*7)-3;
        float gray=DamageTrailLook.gray(severity,wisp)+(random.nextFloat()-.5F)*.045F;
        setColor(gray,gray,gray);setAlphaF((.4F+severity*.35F)*(wisp?.56F:1)*pulse*(.9F+random.nextFloat()*.1F));
    }
    @Override public void tick() {
        xo=x;yo=y;zo=z;
        if(++age>=lifetime){remove();return;}
        frame=billowFrames?SmokeAtlas.frame(age,lifetime):Math.min(7,age*8/lifetime);
        double curl=Math.sin(level.getGameTime()*.09+seed*.2+age*.08)*.0025*BlastSmokeMotion.curlShare(age);
        xd=BlastSmokeMotion.advect(xd,BlastSmokeMotion.windX(level.getGameTime()),.95,age)+curl;
        zd=BlastSmokeMotion.advect(zd,BlastSmokeMotion.windZ(level.getGameTime()),.95,age)-curl*.7;
        yd=yd*.94+DamageTrailLook.lift(age,lifetime,wisp)*.06;
        move(xd,yd,zd);
    }
    @Override public float getQuadSize(float partial){return radius*DamageTrailLook.growth(age+partial,lifetime,wisp);}
}
