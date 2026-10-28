package io.github.mcheliceport.client.particle;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class DamageTrailLookTest {
    @Test void movingVehiclesOutrunTheirShedSmoke(){
        for(double speed:new double[]{.1,.5,1,3,20})assertTrue(DamageTrailLook.inherited(speed)<speed);
        assertEquals(0,DamageTrailLook.inherited(Double.NaN));
        assertEquals(-DamageTrailLook.inherited(2),DamageTrailLook.inherited(-2));
    }
    @Test void wispsAreLighterAndExpireBeforeDenseCores(){
        for(float severity:new float[]{0,.5F,1}){
            assertTrue(DamageTrailLook.lifetime(severity,true)<DamageTrailLook.lifetime(severity,false));
            assertTrue(DamageTrailLook.gray(severity,true)>DamageTrailLook.gray(severity,false));
            assertTrue(DamageTrailLook.radius(severity,10)<=1.61F);
        }
    }
    @Test void billowsExpandGraduallyAndDistanceOrPressureNeverIncreasesBirths(){
        for(boolean wisp:new boolean[]{false,true}){
            float previous=0;for(int age=0;age<=80;age++){
                float size=DamageTrailLook.growth(age,60,wisp);assertTrue(size>=previous);assertTrue(size<=1.11F);previous=size;
                assertTrue(DamageTrailLook.lift(age,60,wisp)<=.0571);}
            assertTrue(DamageTrailLook.growth(0,60,wisp)<DamageTrailLook.growth(30,60,wisp));}
        int near=DamageTrailLook.period(1.2,100,0,false);
        assertTrue(DamageTrailLook.period(1.2,3000,0,false)>=near);
        assertTrue(DamageTrailLook.period(1.2,100,0,true)>=near);
        for(int pressure=0;pressure<4;pressure++){
            int period=DamageTrailLook.period(1.2,100,pressure,false);assertTrue(period>=near);
            int count=0;for(int tick=0;tick<600;tick++){
                if(DamageTrailLook.emit(tick,0,.8F,period)){count++;assertEquals(0,tick%period);}
                assertFalse(DamageTrailLook.emit(tick,0,0,period));}
            assertTrue(count<=600/period);assertTrue(count>0);}
    }
    @Test void severeDamageKeepsItsCoreBetweenSurgesWithoutExceedingOneBirthSlot(){
        for(int tick=0;tick<410;tick++){
            assertEquals(tick%3==0,DamageTrailLook.emit(tick,0,.6F,3));
            assertFalse(DamageTrailLook.emit(tick,0,0,3));
        }
    }
}
