package io.github.mcheliceport.client.particle;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;

/** A connected, rising wreck plume: tight hot foot, wider wind-bent upper billows and eroding edges. */
final class WreckSmokeParticle extends SmokeParticle {
    private final float radius,birthFire,opacity,variation;
    private final double originY;
    private final boolean wisp;
    private final int column;
    /** Gray multiplier: 1 for the hull's cooler smoke, lower for the black smoke of burning fuel. */
    float soot=1;
    WreckSmokeParticle(ClientLevel level,double x,double y,double z,float radius,float fire,float opacity,
            boolean wisp,int column,float variation){
        super(level,x,y,z,0,0,0);
        this.radius=radius;this.birthFire=fire;this.opacity=opacity;this.wisp=wisp;this.column=column;
        this.variation=variation;this.originY=y;this.heat=0;
        this.enhanced=true;this.eroding=true;this.hasPhysics=false;
        useBillow(wisp?SmokeAtlas.WISPS:variation<.35F?SmokeAtlas.EXPLOSION:SmokeAtlas.WRECK);
        this.lifetime=SmokeBillowLook.wreckLife(wisp,fire);
        this.frame=0;this.yd=SmokeBillowLook.lift(fire,0,wisp);
    }
    @Override public void tick(){
        xo=x;yo=y;zo=z;
        if(++age>=lifetime){remove();return;}
        frame=billowFrames?SmokeAtlas.frame(age,lifetime):Math.min(7,age*8/lifetime);
        float height=(float)(y-originY);
        float share=SmokeBillowLook.windShare(height),curl=SmokeBillowLook.curl(level.getGameTime(),column,height);
        xd=BlastSmokeMotion.advect(xd,BlastSmokeMotion.windX(level.getGameTime())*share,.94,age)+curl*.0018;
        zd=BlastSmokeMotion.advect(zd,BlastSmokeMotion.windZ(level.getGameTime())*share,.94,age)-curl*.0012;
        yd=SmokeBillowLook.lift(birthFire,age,wisp);
        move(xd,yd,zd);
    }
    @Override public float getQuadSize(float partial){return radius*SmokeBillowLook.growth(age+partial,lifetime,wisp);}
    @Override public void extract(QuadParticleRenderState state,Camera camera,float partial){
        float at=age+partial,height=(float)(y-originY);
        float gray=SmokeBillowLook.gray(birthFire,at,lifetime,variation);
        float amber=SmokeBillowLook.amber(birthFire,at,height);
        gray*=soot;
        setColor(gray+amber*.10F,gray+amber*.035F,gray*(1-amber*.10F));
        alpha=opacity;
        super.extract(state,camera,partial);
    }
    @Override protected int getLightCoords(float partial){
        return EffectLook.smokeLight(super.getLightCoords(partial),
                .16F*SmokeBillowLook.amber(birthFire,age+partial,(float)(y-originY)));
    }
}
