package io.github.mcheliceport.client.render.baked;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.mcheliceport.client.model.VehicleSurfaceShade;
import org.junit.jupiter.api.Test;

class BakedVehiclesTest {

    @Test
    void vertexIsPositionUvAndTwoPackedNormals() {
        assertEquals(12 + 8 + 4 + 4, BakedVehicles.FORMAT.getVertexSize());
    }

    @Test
    void partBlockHoldsTwoMatricesColourLightAndFlag() {
        // std140: two mat4 (128), vec4 (16), ivec2 (8), int (4).
        assertEquals(156, BakedVehicles.PART_UBO_SIZE);
    }

    @Test
    void normalsPackToSignedBytes() {
        assertEquals(127, BakedModelCache.snorm(1.0F));
        assertEquals(-127, BakedModelCache.snorm(-1.0F));
        assertEquals(0, BakedModelCache.snorm(0.0F));
        assertEquals(64, BakedModelCache.snorm(0.5F));
        assertEquals(127, BakedModelCache.snorm(1.5F));
    }

    @Test
    void surfaceShadeGreyPacksExactlyAboveHalf() {
        int grey = VehicleSurfaceShade.tint(VehicleSurfaceShade.BASE_COLOR, 0.88F);
        int[] shades = {VehicleSurfaceShade.BASE_COLOR, grey};
        assertEquals(0xBF - 128, BakedModelCache.shade(shades, 0));
        assertEquals((grey & 0xFF) - 128, BakedModelCache.shade(shades, 1));
        assertEquals(Byte.MIN_VALUE, BakedModelCache.shade(null, 0));
        assertEquals(Byte.MIN_VALUE, BakedModelCache.shade(shades, 2));
    }
}
