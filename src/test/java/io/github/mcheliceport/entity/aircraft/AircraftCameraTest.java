package io.github.mcheliceport.entity.aircraft;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AircraftCameraTest {

    @Test
    void vehicleCamerasCycleThroughThermalVision() {
        AircraftCamera camera = new AircraftCamera(true);
        assertEquals(3, camera.getModeNum(0));
        camera.setMode(0, AircraftCamera.MODE_THERMALVISION);
        assertEquals(AircraftCamera.MODE_THERMALVISION, camera.getMode(0));
        assertEquals("THERMAL VISION", camera.getModeName(0));
        camera.setMode(0, AircraftCamera.MODE_THERMALVISION + 1);
        assertEquals(AircraftCamera.MODE_NORMAL, camera.getMode(0), "the next mode wraps to normal");
        assertEquals(AircraftCamera.MODE_NORMAL, camera.getMode(1), "each user has their own mode");
    }

    @Test
    void gltdCameraHasNoThermalVisionAsUpstream() {
        AircraftCamera camera = new AircraftCamera();
        assertEquals(2, camera.getModeNum(0));
        camera.setMode(0, AircraftCamera.MODE_THERMALVISION);
        assertEquals(AircraftCamera.MODE_NORMAL, camera.getMode(0));
    }
}
