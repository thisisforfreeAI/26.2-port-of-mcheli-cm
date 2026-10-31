package io.github.mcheliceport.client.render;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class SceneLightLookTest {
    private static final float NOON = 0, SUNSET = (float) (Math.PI / 2) - 0.05F, MIDNIGHT = (float) Math.PI;

    @Test void briefLightsRiseFastThenFadeToNothing(){
        float life=10;
        assertTrue(SceneLightLook.fade(0.5F,life)>0.6F);
        float last=SceneLightLook.fade(1,life);
        for(float t=1.5F;t<life;t+=0.5F){float v=SceneLightLook.fade(t,life);assertTrue(v<=last+1e-6F);last=v;}
        assertEquals(0,SceneLightLook.fade(life,life));assertEquals(0,SceneLightLook.fade(-1,life));
        assertEquals(0,SceneLightLook.fade(Float.NaN,life));assertEquals(0,SceneLightLook.fade(1,0));
    }

    @Test void fireFlickerStaysLit(){
        for(int seed:new int[]{0,7,Integer.MAX_VALUE})for(float t=0;t<200;t+=0.37F){
            float v=SceneLightLook.flicker(t,seed);assertTrue(v>0.6F&&v<1.05F,"flicker "+v);
        }
        assertEquals(1,SceneLightLook.flicker(Float.NaN,3));
    }

    @Test void nearerBrighterWiderLightsRankFirstAndFarOnesDrop(){
        assertTrue(SceneLightLook.score(1,10,25)>SceneLightLook.score(1,10,400));
        assertTrue(SceneLightLook.score(1.5F,10,100)>SceneLightLook.score(1,10,100));
        assertTrue(SceneLightLook.score(1,20,400)>SceneLightLook.score(1,5,400));
        assertEquals(0,SceneLightLook.score(1,10,200*200));assertEquals(0,SceneLightLook.score(0,10,1));
        assertEquals(0,SceneLightLook.score(1,10,Double.NaN));
    }

    @Test void sourceSizesAreBounded(){
        assertEquals(0,SceneLightLook.explosionRadius(0));assertEquals(28,SceneLightLook.explosionRadius(1000));
        assertTrue(SceneLightLook.explosionRadius(4)>SceneLightLook.explosionRadius(1));
        assertTrue(SceneLightLook.explosionLife(100)<=20);assertTrue(SceneLightLook.muzzleRadius(100)<=9);
        assertEquals(0,SceneLightLook.fireRadius(Float.NaN));assertEquals(10,SceneLightLook.fireRadius(5));
    }

    @Test void fireLightCoolsWithTheSourceInsteadOfKeepingAConstantMinimum(){
        assertEquals(0,SceneLightLook.fireIntensity(0));
        assertEquals(0,SceneLightLook.fireIntensity(Float.NaN));
        assertEquals(0,SceneLightLook.fireIntensity(Float.POSITIVE_INFINITY));
        assertEquals(1,SceneLightLook.fireIntensity(5));
        assertTrue(SceneLightLook.fireIntensity(.01F)<SceneLightLook.fireIntensity(.25F)*.25F);
        assertEquals(0,SceneLightLook.poolRadius(6,0));
        assertEquals(0,SceneLightLook.poolRadius(Float.NaN,1));
        assertTrue(SceneLightLook.poolRadius(7,1)>SceneLightLook.poolRadius(2,1));
        assertEquals(14,SceneLightLook.poolRadius(1000,1));
        // Fuel is still burning after the hull's opening fire has cooled away.
        var cooling=new io.github.mcheliceport.client.particle.WreckCoolingLook();
        cooling.tick(false);cooling.tick(true);for(int i=0;i<400;i++)cooling.tick(true);
        assertEquals(0,cooling.fire());
        assertTrue(SceneLightLook.fireIntensity(io.github.mcheliceport.client.particle.WreckFuelFire.strength(cooling.age()))>0);
        assertEquals(0,SceneLightLook.fireIntensity(io.github.mcheliceport.client.particle.WreckFuelFire.strength(760)));
    }

    @Test void fireFlickerChangesGentlyBetweenFrames(){
        for(float t=0;t<200;t+=.2F)
            assertTrue(Math.abs(SceneLightLook.flicker(t+.2F,3)-SceneLightLook.flicker(t,3))<.015F);
    }

    @Test void sunRisesEastIsOverheadAtNoonAndMoonTakesOverAtNight(){
        float[] noon=SceneLightLook.keyLight(NOON,MIDNIGHT,0,0);
        assertEquals(1,noon[1],1e-4);assertEquals(1,noon[3],1e-4);
        float[] morning=SceneLightLook.skyDirection((float)(-Math.PI/4));
        assertTrue(morning[0]>0&&morning[1]>0,"the sun rises in the east, +X");
        float[] night=SceneLightLook.keyLight(MIDNIGHT,0,0,0);
        assertTrue(night[3]>0&&night[3]<0.4F,"moonlight is faint");assertTrue(night[6]>night[4],"moonlight is cool");
        assertEquals(1,night[1],1e-4);
    }

    @Test void lowSunIsWarmAndNeverLightsFromBelow(){
        float[] dusk=SceneLightLook.keyLight(SUNSET,SUNSET+(float)Math.PI,0,0);
        float[] noon=SceneLightLook.keyLight(NOON,MIDNIGHT,0,0);
        assertTrue(dusk[6]<noon[6]-0.2F,"dusk light has less blue");assertTrue(dusk[3]>0);
        for(float a=0;a<6.3F;a+=0.1F){float[] k=SceneLightLook.keyLight(a,a+(float)Math.PI,0,0);
            assertTrue(k[1]>0.07F);assertEquals(1,k[0]*k[0]+k[1]*k[1]+k[2]*k[2],1e-4);}
    }

    @Test void rainDimsTheSunAndTheSheen(){
        float clear=SceneLightLook.keyLight(NOON,MIDNIGHT,0,0)[3],storm=SceneLightLook.keyLight(NOON,MIDNIGHT,1,1)[3];
        assertTrue(storm<clear*0.2F);assertTrue(storm>=0);
        assertTrue(SceneLightLook.sheen(1)<SceneLightLook.sheen(0));
        float[] fill=SceneLightLook.fill(MIDNIGHT,0);assertTrue(fill[2]>fill[0],"night fill is cool");
        assertTrue(SceneLightLook.fill(NOON,1)[0]>=SceneLightLook.fill(NOON,0)[0]);
    }

    @Test void probeBlendsLightAcrossTheHull(){
        float[] light=new float[16];
        // Corners with x=1 (bits 0) are in the open; x=0 corners sit inside a dark hangar.
        for(int i=0;i<8;i++){light[i*2]=0;light[i*2+1]=(i&1)==0?0:240;}
        var probe=new VehicleLightProbe(0,0,0,10,2,4,light);
        assertEquals(0,probe.at(0,1,2)[1],1e-4);assertEquals(240,probe.at(10,1,2)[1],1e-4);
        assertEquals(120,probe.at(5,1,2)[1],1e-3);assertEquals(240,probe.at(50,9,-3)[1],1e-4);
    }
}
