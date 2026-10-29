package io.github.mcheliceport.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

/** Small tire/dust billows, using the existing sixteen-frame wisps and shared smoke allowance. */
final class LandingPuffParticle extends SmokeParticle {
    LandingPuffParticle(ClientLevel level,Vec3 at,double mx,double mz,float[] color){
        super(level,at.x,at.y+.12,at.z,mx,.018,mz);
        enhanced=true;hasPhysics=false;lifetime=22;useBillow(SmokeAtlas.WISPS);
        setColor(color[0],color[1],color[2]);setAlphaF(.68F);
    }
    @Override public void tick(){
        xo=x;yo=y;zo=z;if(++age>=lifetime){remove();return;}
        frame=SmokeAtlas.frame(age,lifetime);
        xd=BlastSmokeMotion.advect(xd,BlastSmokeMotion.windX(level.getGameTime()),.94,age);
        zd=BlastSmokeMotion.advect(zd,BlastSmokeMotion.windZ(level.getGameTime()),.94,age);
        move(xd,yd,zd);yd*=.98;
    }
    @Override public float getQuadSize(float partial){return .65F*(.6F+.4F*Math.min(1,(age+partial)/10));}
}
