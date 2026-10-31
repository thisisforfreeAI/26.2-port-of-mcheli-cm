package io.github.mcheliceport.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.LightCoordsUtil;

/** A small rising fire pocket, using the existing fire atlas and particle admission. */
final class WreckFlameParticle extends McheliParticle {
    private final float radius, opacity;
    private int frame;
    WreckFlameParticle(ClientLevel level,double x,double y,double z,float radius,float opacity){
        super(level,x,y,z,0,.035,0,BlastCloudParticle.TEXTURE);
        this.radius=radius;this.opacity=opacity;lifetime=24;hasPhysics=false;
        io.github.mcheliceport.client.render.SceneLights.flame(level,new net.minecraft.world.phys.Vec3(x,y,z),radius,opacity);
        roll=(random.nextFloat()-.5F)*.35F;oRoll=roll;
    }
    @Override public void tick(){
        xo=x;yo=y;zo=z;if(++age>=lifetime){remove();return;}move(xd,yd,zd);
    }
    @Override public void extract(net.minecraft.client.renderer.state.level.QuadParticleRenderState state,
            net.minecraft.client.Camera camera,float partial){
        float t=age+partial;frame=Math.min(7,(int)(t*8/lifetime));
        alpha=opacity*Math.clamp(t/3,0,1)*Math.clamp((lifetime-t)/8,0,1);
        super.extract(state,camera,partial);
    }
    @Override public float getQuadSize(float partial){return radius*(.85F+.15F*(float)Math.sin((age+partial)*.4));}
    @Override protected float getU0(){return frameU(frame%4,4,false);}
    @Override protected float getU1(){return frameU(frame%4,4,true);}
    @Override protected float getV0(){return sprite.getV(frame/4*.25F);}
    @Override protected float getV1(){return sprite.getV((frame/4+1)*.25F);}
    @Override protected int getLightCoords(float partial){return LightCoordsUtil.FULL_BRIGHT;}
}
