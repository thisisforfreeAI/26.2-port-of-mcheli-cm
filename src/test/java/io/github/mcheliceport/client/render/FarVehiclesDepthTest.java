package io.github.mcheliceport.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FarVehiclesDepthTest {
    @Test void vanillaWhileEveryVehicleIsInside(){assertEquals(768F,FarVehicles.depthFar(768F,500,3000));}
    @Test void stretchedToTheFarthestVehicle(){assertEquals(1296F,FarVehicles.depthFar(768F,1200,3000));}
    @Test void neverPastTheConfiguredLimit(){assertEquals(2000F,FarVehicles.depthFar(768F,2500,2000));}
    @Test void zeroKeepsVanilla(){assertEquals(768F,FarVehicles.depthFar(768F,2500,0));}
    @Test void aLimitBelowVanillaNeverShrinksIt(){assertEquals(768F,FarVehicles.depthFar(768F,1200,400));}
}
