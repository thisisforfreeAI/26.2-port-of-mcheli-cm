package io.github.mcheliceport.client.particle;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class AirflowFireLookTest {
    @Test void stationaryFireRisesAndFlightFireLeansAgainstTravel() {
        assertEquals(new Vec3(0,1,0),AirflowFireLook.axis(Vec3.ZERO));
        for(Vec3 motion:new Vec3[]{new Vec3(1,0,0),new Vec3(-1,0,0),new Vec3(0,0,1),new Vec3(0,0,-1),new Vec3(0,1.5,0)}) {
            Vec3 direction=AirflowFireLook.axis(motion);
            assertTrue(direction.dot(motion)<0,"Flame leans into the flight direction: "+motion);
            assertEquals(1,direction.length(),1.0E-9);
        }
        assertTrue(AirflowFireLook.axis(new Vec3(.05,0,0)).y>.98);
    }
    @Test void extremeInputsStayFiniteAndTheAircraftOutrunsItsFire() {
        for(Vec3 v:new Vec3[]{Vec3.ZERO,new Vec3(0,.22/.85,0),new Vec3(Double.MAX_VALUE,Double.MAX_VALUE,Double.MAX_VALUE),new Vec3(Double.NaN,1,0),new Vec3(0,Double.POSITIVE_INFINITY,0)}) {
            assertTrue(AirflowFireLook.motion(v).length()<=3.000001);
            assertTrue(Double.isFinite(AirflowFireLook.axis(v).length()));
            assertTrue(Double.isFinite(AirflowFireLook.velocity(v).length()));
            assertTrue(AirflowFireLook.lifetime(v)>=12&&AirflowFireLook.lifetime(v)<=20);
        }
        for(double speed:new double[]{.1,.5,1,2,3})
            assertTrue(AirflowFireLook.velocity(new Vec3(speed,0,0)).x<speed);
    }
    @Test void repairsStopNewFireAndHighSpeedHasShorterBoundedPockets() {
        assertEquals(0,AirflowFireLook.strength(0));
        assertEquals(0,AirflowFireLook.strength(.55F));
        assertEquals(0,AirflowFireLook.strength(Float.NaN));
        assertEquals(1,AirflowFireLook.strength(1));
        assertTrue(AirflowFireLook.lifetime(new Vec3(2,0,0))<AirflowFireLook.lifetime(Vec3.ZERO));
        for(float severity:new float[]{0,.55F,.8F,1,Float.POSITIVE_INFINITY})
            assertTrue(AirflowFireLook.radius(AirflowFireLook.strength(severity),new Vec3(20,0,0))<1);
    }
}
