package io.github.mcheliceport.client.render;

import static org.junit.jupiter.api.Assertions.*;
import java.util.function.IntBinaryOperator;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/** Fragments on blocky terrain (crater rims and steps), swept the way the debris systems sweep them. */
class DebrisTerrainTest {
    /** Block columns: the top of column (x,z) is at the returned y. */
    private record Terrain(IntBinaryOperator top){
        boolean solid(Vec3 p){return p.y<top.applyAsInt((int)Math.floor(p.x),(int)Math.floor(p.z));}
        /** First block face crossed from {@code from} to {@code to}: {hit, normal}, {null,null} inside, or null for a miss. */
        Vec3[] clip(Vec3 from,Vec3 to){
            if(solid(from))return new Vec3[]{null,null};
            int n=(int)Math.ceil(from.distanceTo(to)/.002);Vec3 last=from;
            for(int i=1;i<=n;i++){
                Vec3 p=from.lerp(to,i/(double)n);
                if(solid(p)){
                    Vec3 normal=Math.floor(p.y)!=Math.floor(last.y)&&!solid(new Vec3(p.x,last.y,p.z))?new Vec3(0,Math.signum(last.y-p.y),0)
                            :Math.floor(p.x)!=Math.floor(last.x)?new Vec3(Math.signum(last.x-p.x),0,0):new Vec3(0,0,Math.signum(last.z-p.z));
                    return new Vec3[]{last,normal};
                }
                last=p;
            }
            return null;
        }
    }
    private static void tick(DebrisMotion m,Terrain t){
        m.step();
        if(!m.moving()&&m.phase!=DebrisMotion.Phase.REST)return;
        var hit=t.clip(m.sweepFrom(),m.sweepTo());
        if(hit==null){m.clear();return;}
        if(hit[0]==null){m.blocked();return;}
        m.contact(hit[0],hit[1],false);
    }
    private static int reversals(double[] ys,int from){
        int n=0;double last=0;
        for(int i=from+1;i<ys.length;i++){double d=ys[i]-ys[i-1];if(Math.abs(d)<1e-4)continue;if(last!=0&&Math.signum(d)!=Math.signum(last))n++;last=d;}
        return n;
    }
    /** A crater: ground at 64, a stepped pit down to 61 for x >= 0. */
    private static final Terrain CRATER=new Terrain((x,z)->x<0?64:Math.max(61,64-x-1));

    @Test void piecesSlidingOverACraterRimDoNotBobUpAndDown(){
        for(int seed=0;seed<40;seed++){
            var size=seed%2==0?new Vector3f(4,.3F,1.5F):new Vector3f(.9F,.9F,.9F);
            var m=new DebrisMotion(new Vec3(-3+seed*.05,66,.5+seed*.3),new Vec3(.35+seed%5*.05,-.1,seed%3*.05),new Vector3f(.1F,.05F,.12F),new Quaternionf().rotateZ(seed*.3F),size,seed);
            double[] ys=new double[400];
            for(int i=0;i<ys.length;i++){tick(m,CRATER);ys[i]=m.position.y;}
            assertEquals(DebrisMotion.Phase.REST,m.phase,"seed "+seed);
            assertTrue(reversals(ys,120)==0,"seed "+seed+" keeps bobbing: "+reversals(ys,120));
            double ground=CRATER.top.applyAsInt((int)Math.floor(m.position.x),(int)Math.floor(m.position.z));
            assertEquals(ground+m.height(),m.position.y,.05,"seed "+seed+" rests on the block under it");
        }
    }
    @Test void aRestingPieceFallsWhenTheGroundUnderItIsBlownAway(){
        var m=new DebrisMotion(new Vec3(-2.5,66,.5),Vec3.ZERO,new Vector3f(),new Quaternionf(),new Vector3f(1,.2F,1),1);
        for(int i=0;i<200;i++)tick(m,new Terrain((x,z)->64));
        assertEquals(DebrisMotion.Phase.REST,m.phase);
        var pit=new Terrain((x,z)->60);
        for(int i=0;i<200;i++)tick(m,pit);
        assertEquals(DebrisMotion.Phase.REST,m.phase);
        assertEquals(60+m.height(),m.position.y,.05);
    }
    @Test void aSlideIntoAStepStopsAtTheWall(){
        var wall=new Terrain((x,z)->x>=2?66:64);
        var m=new DebrisMotion(new Vec3(0,64.6,.5),new Vec3(.5,-.2,0),new Vector3f(),new Quaternionf(),new Vector3f(.8F,.2F,.8F),2);
        for(int i=0;i<300;i++)tick(m,wall);
        assertEquals(DebrisMotion.Phase.REST,m.phase);
        assertTrue(m.position.x<2,"inside the wall at "+m.position);
        assertEquals(64+m.height(),m.position.y,.05);
    }
}
