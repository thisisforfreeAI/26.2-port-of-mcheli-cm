package io.github.mcheliceport.client.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AircraftCameraClientTest {

    @AfterEach
    void reset() {
        AircraftCameraClient.reset();
    }

    @Test
    void pageUpAndDownStepByFourBetweenFourAndSeventyTwo() {
        assertEquals(8.0F, AircraftCameraClient.stepThirdPersonDistance(4.0F, true));
        assertEquals(4.0F, AircraftCameraClient.stepThirdPersonDistance(4.0F, false));
        assertEquals(72.0F, AircraftCameraClient.stepThirdPersonDistance(70.0F, true));
        // Upstream truncates before stepping.
        assertEquals(10.0F, AircraftCameraClient.stepThirdPersonDistance(6.9F, true));
    }

    @Test
    void distanceIsKeptBetweenFourAndSixtyAsUpstream() {
        AircraftCameraClient.setThirdPersonDistance(64.0F);
        assertEquals(60.0F, AircraftCameraClient.thirdPersonDistance());
        AircraftCameraClient.setThirdPersonDistance(2.0F);
        assertEquals(4.0F, AircraftCameraClient.thirdPersonDistance());
        AircraftCameraClient.setThirdPersonDistance(0.05F);
        assertEquals(4.0F, AircraftCameraClient.thirdPersonDistance(), "below 0.1 is ignored");
        AircraftCameraClient.setThirdPersonDistance(12.0F);
        AircraftCameraClient.reset();
        assertEquals(4.0F, AircraftCameraClient.thirdPersonDistance());
    }
}
