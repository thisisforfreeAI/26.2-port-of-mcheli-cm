package io.github.mcheliceport.client.render;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class DebrisMotionTest {
    private static final Vector3f WING=new Vector3f(5,.3F,2), CHUNK=new Vector3f(1.2F,1.1F,1.3F);
    private static DebrisMotion torn(Vec3 hinge){var m=new DebrisMotion(new Vec3(0,100,0),new Vec3(0,0,1.5),new Vector3f(),new Quaternionf(),WING,9);m.tear(hinge);return m;}
    private static DebrisMotion free(Vector3f size,Vec3 velocity){return new DebrisMotion(new Vec3(0,100,0),velocity,new Vector3f(),new Quaternionf(),size,3);}
    private static void land(DebrisMotion m,int ticks){
        for(int t=0;t<ticks;t++){m.step();if(!m.moving())continue;
            if(m.sweepTo().y<=64)m.contact(new Vec3(m.position.x,64,m.position.z),new Vec3(0,1,0),false);else m.clear();}
    }
    @Test void flatWingsFallSlowerThanCompactChunks(){
        var wing=free(WING,Vec3.ZERO);var chunk=free(CHUNK,Vec3.ZERO);
        for(int t=0;t<160;t++){wing.step();chunk.step();}
        assertTrue(wing.position.y>chunk.position.y+8,"wing "+wing.position.y+" chunk "+chunk.position.y);
        assertTrue(Math.abs(wing.velocity.y)<1.3&&Math.abs(chunk.velocity.y)<2.6);
    }
    @Test void fragmentsKeepFlightVelocityAndSlowDown(){
        var m=free(WING,new Vec3(0,0,1.8));m.step();
        assertTrue(m.velocity.z>1.6);
        for(int t=0;t<80;t++)m.step();
        assertTrue(m.velocity.z<1.2&&m.velocity.y<-.2,m.velocity.toString());
    }
    @Test void wingsAutorotateAboutTheirSpan(){
        var m=free(WING,new Vec3(0,-.5,1));
        for(int t=0;t<60;t++)m.step();
        assertTrue(m.spinRate()>.05,"spin "+m.spinRate());
        assertTrue(m.spinRate()<=DebrisMotion.MAX_SPIN+1e-4);
    }
    @Test void ripPivotsAboutTheRootThenReleasesWithTangentialSpeed(){
        var hinge=new Vec3(-2.5,0,0);var m=torn(hinge);
        assertEquals(DebrisMotion.Phase.RIP,m.phase);
        double root0=m.position.add(m.rotated(hinge)).z;
        m.step();m.step();
        // The root edge rides with the aircraft while the section folds.
        assertEquals(root0+3,m.position.add(m.rotated(hinge)).z,1e-3);
        for(int t=0;t<6&&m.phase==DebrisMotion.Phase.RIP;t++)m.step();
        assertEquals(DebrisMotion.Phase.FREE,m.phase);
        assertNotEquals(0,m.spinRate(),0);
        assertTrue(m.velocity.subtract(new Vec3(0,0,1.5)).length()>.05);
    }
    @Test void bouncesThenSlidesAndRestsFlat(){
        var m=new DebrisMotion(new Vec3(0,72,0),new Vec3(.6,-.4,.3),new Vector3f(.2F,.1F,.15F),new Quaternionf().rotateZ(1.1F),WING,5);
        land(m,400);
        assertEquals(DebrisMotion.Phase.REST,m.phase);
        assertTrue(m.bounces()>=1);
        assertEquals(64+.15,m.position.y,.03);
        var y=new Quaternionf().rotateZ(1.1F).transform(new Vector3f(0,1,0));var thin=m.rotated(new Vec3(y.x,y.y,y.z));assertTrue(Math.abs(thin.y)>.99,thin.toString());
    }
    @Test void waterSinksInsteadOfBouncing(){
        var m=free(CHUNK,new Vec3(0,-1,0));m.step();
        assertEquals(DebrisMotion.Event.SPLASH,m.contact(new Vec3(0,98,0),new Vec3(0,1,0),true));
        double y=m.position.y;for(int t=0;t<10;t++)m.step();assertTrue(m.position.y<y);
    }
    @Test void degenerateInputStaysFinite(){
        var m=new DebrisMotion(new Vec3(0,80,0),new Vec3(Double.NaN,0,0),new Vector3f(Float.NaN,0,0),new Quaternionf(),new Vector3f(),1);m.tear(new Vec3(Double.NaN,0,0));
        land(m,300);
        assertTrue(Double.isFinite(m.position.y)&&m.orientation.isFinite());
    }
    @Test void aircraftTurnRateIsInherited(){
        var w=DebrisMotion.angularVelocity(DebrisMotion.attitude(10,0,0),DebrisMotion.attitude(10,0,6));
        assertEquals(Math.toRadians(6),w.length(),1e-4);
        assertEquals(0,DebrisMotion.angularVelocity(DebrisMotion.attitude(179,0,0),DebrisMotion.attitude(-179,0,0)).length(),.04);
    }
}
