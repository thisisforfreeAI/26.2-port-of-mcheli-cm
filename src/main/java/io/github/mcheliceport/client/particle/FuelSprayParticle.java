package io.github.mcheliceport.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;

/** A neutral ambient-lit droplet; a predicted ignition changes its art, never emits children. */
final class FuelSprayParticle extends McheliParticle {
    private final int ignition,ambient;
    private final float opacity;
    private final TextureAtlasSprite hot;
    private int frame;
    FuelSprayParticle(ClientLevel level,Vec3 at,Vec3 velocity,float opacity,int ignition) {
        super(level,at.x,at.y,at.z,velocity.x,velocity.y,velocity.z,
                Identifier.fromNamespaceAndPath("mcheli_ce_port","m8_debris"));
        this.opacity=opacity;this.ignition=ignition;
        hot=ignition<0?null:sprite(BlastCloudParticle.TEXTURE);ambient=super.getLightCoords(0);
        lifetime=18;hasPhysics=false;setColor(.88F,.85F,.74F);alpha=.65F*opacity;
    }
    boolean burning(){return ignition>=0&&age>=ignition;}
    @Override public void tick() {
        xo=x;yo=y;zo=z;if(++age>=lifetime){remove();return;}
        move(xd,yd,zd);xd*=.96;zd*=.96;yd-=.02;
        if(burning()){setSprite(hot);setColor(1,1,1);frame=Math.min(7,age-ignition);}
        alpha=opacity*(burning()?.8F:.65F)*Math.min(1,(lifetime-age)/5F);
    }
    @Override public float getQuadSize(float partial){return burning()?.16F+.012F*Math.min(8,age-ignition+partial):.045F;}
    @Override protected int getLightCoords(float partial){return burning()?LightCoordsUtil.FULL_BRIGHT:ambient;}
    @Override protected float getU0(){return burning()?frameU(frame%4,4,false):sprite.getU0();}
    @Override protected float getU1(){return burning()?frameU(frame%4,4,true):sprite.getU1();}
    @Override protected float getV0(){return burning()?sprite.getV(frame/4*.25F):sprite.getV0();}
    @Override protected float getV1(){return burning()?sprite.getV((frame/4+1)*.25F):sprite.getV1();}
}
