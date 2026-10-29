package io.github.mcheliceport.client.render;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class LandingLightGroundTest {
    @Test void aShortPoolCannotDisappearBetweenBlockCorners(){
        float radius=LandingLightGround.radius(5,9,48);
        for(float x:new float[]{0,.25F,.5F,.75F,.99F})for(float z:new float[]{0,.25F,.5F,.75F,.99F}){
            float energy=0;for(int cx=0;cx<=1;cx++)for(int cz=0;cz<=1;cz++)energy+=LandingLightGround.falloff(cx-x,cz-z,radius);
            assertTrue(energy>0,"A parked light must still illuminate a surface when centered inside a block");
        }
        assertTrue(LandingLightGround.radius(1000,100,1)<=1.9F);
    }
}
