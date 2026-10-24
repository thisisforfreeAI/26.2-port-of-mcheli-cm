package io.github.mcheliceport.client.model;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class VehicleSurfaceShadeTest {
    private static float[] triangle(float height, float normal) {
        return new float[]{0,0,0, 0,0, 0,normal,0,
                1,height,0, 0,0, 0,normal,0,
                0,height,1, 0,0, 0,normal,0};
    }
    @Test void flatPanelsStayNeutralWhileCreasesRemainRestrained() {
        for (float shade : VehicleSurfaceShade.bake(triangle(0, 1))) assertEquals(1, shade);
        float cavity = VehicleSurfaceShade.bake(triangle(1, 1))[0];
        float edge = VehicleSurfaceShade.bake(triangle(1, -1))[0];
        assertTrue(cavity >= 0.88F && cavity < 1);
        assertTrue(edge > 1 && edge <= 1.03F);
        assertEquals(1, VehicleSurfaceShade.bake(new float[24])[0]);
        assertNull(VehicleSurfaceShade.bake(new float[60003 * 8]));
    }
    @Test void loadedGroupsCarryReadyVertexColorsWithoutFrameTimeTintWork() {
        var group = new McheliModel.Group("panel", triangle(0, 1));
        var model = new McheliModel("test", java.util.List.of(group), 0, 0, 0, 1, 1, 1);
        VehicleSurfaceShade.prepare(model);
        for (int color : VehicleSurfaceShade.get(group)) assertEquals(VehicleSurfaceShade.BASE_COLOR, color);
    }
    @Test void tintPreservesAlphaAndDoesNotWrapBrightColors() {
        assertEquals(0x7FFFFFFF, VehicleSurfaceShade.tint(0x7FFFFFFF, 1.03F));
        assertEquals(0xFF888888, VehicleSurfaceShade.tint(0xFF888888, 1));
        assertEquals(0xFF787878, VehicleSurfaceShade.tint(0xFF888888, 0.88F));
    }
}
