package io.github.mcheliceport.client.particle;

import static org.junit.jupiter.api.Assertions.*;
import io.github.mcheliceport.config.ClientConfig;
import org.junit.jupiter.api.Test;

class ProjectileTrailLookTest {
    @Test void solidMotorsAndCruiseExhaustHaveDifferentBoundedFootprints(){
        var rocket=ProjectileTrailLook.profile("hydra70","rocket",5,100);
        var missile=ProjectileTrailLook.profile("aim9","aamissile",7,100);
        var cruise=ProjectileTrailLook.profile("AGM-158","asmissile",10,100);
        assertFalse(rocket.cruise());assertFalse(missile.cruise());assertTrue(cruise.cruise());
        assertTrue(cruise.radius()<missile.radius());assertTrue(cruise.motor()<missile.motor());
        for(float size:new float[]{0,.2F,10,100,Float.NaN}){
            var p=ProjectileTrailLook.profile("test","rocket",size,Integer.MAX_VALUE);
            assertTrue(Float.isFinite(p.radius()));assertTrue(p.radius()>0&&p.radius()<=1.4F);assertTrue(p.life()<=96);}
    }
    @Test void authoredMotorWindowSpoolsUpAndFadesBeforeItsCutoff(){
        assertEquals(0,ProjectileTrailLook.weight(9,10,40));assertEquals(0,ProjectileTrailLook.weight(10,10,40));
        assertTrue(ProjectileTrailLook.weight(11,10,40)>0);assertEquals(1,ProjectileTrailLook.weight(12,10,40));
        assertTrue(ProjectileTrailLook.weight(38,10,40)<ProjectileTrailLook.weight(34,10,40));
        assertEquals(0,ProjectileTrailLook.weight(40,10,40));assertEquals(0,ProjectileTrailLook.weight(50,10,40));
        assertEquals(1,ProjectileTrailLook.weight(10000,0,-1));
    }
    @Test void samplingRejectsWarpsAndReducesWorkAtDistanceOrUnderPressure(){
        for(double distance:new double[]{0,.5,2,8,32}){
            int close=ProjectileTrailLook.samples(distance,3,0,false);assertTrue(close>=1&&close<=3);
            assertTrue(ProjectileTrailLook.samples(distance,3,1,false)<=close);assertTrue(ProjectileTrailLook.samples(distance,3,0,true)<=close);
            for(boolean wisp:new boolean[]{false,true})assertTrue(ProjectileTrailLook.growth(96,96,wisp)<=1.1F);}
        assertEquals(0,ProjectileTrailLook.samples(Double.NaN,3,0,false));assertEquals(0,ProjectileTrailLook.samples(100,3,0,false));
        assertTrue(ProjectileTrailLook.birthLimit(ClientConfig.EffectBudget.LOW)<ProjectileTrailLook.birthLimit(ClientConfig.EffectBudget.BALANCED));
    }
    @Test void nearbyMovingTrailsOverlapAndReachTheirBillowSizeEarly(){
        assertEquals(2,ProjectileTrailLook.samples(.85,3,0,false));
        var solid=ProjectileTrailLook.profile("hydra70","rocket",5,80);
        int count=ProjectileTrailLook.samples(4,3,0,false);
        float radius=ProjectileTrailLook.radius(solid,4,count);
        assertTrue(radius<=solid.radius()*1.6F+.0001F);
        assertTrue(radius*2>=4F/count,"Fast solid motor billows should overlap");
        assertEquals(1.05F,ProjectileTrailLook.growth(24,96,false),.0001F);
    }
}
