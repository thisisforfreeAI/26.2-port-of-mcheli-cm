package io.github.mcheliceport.shim.client;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;

/** 26.2's {@code LightCoordsUtil} over 1.21.1's {@link LightTexture}. */
public final class LightCoordsUtil {
    public static final int FULL_BRIGHT = LightTexture.FULL_BRIGHT;
    public static final int FULL_SKY = LightTexture.FULL_SKY;

    private LightCoordsUtil() {
    }

    public static int pack(int block, int sky) {
        return LightTexture.pack(block, sky);
    }

    public static int block(int coords) {
        return LightTexture.block(coords);
    }

    public static int sky(int coords) {
        return LightTexture.sky(coords);
    }

    public static int getLightCoords(BlockAndTintGetter level, BlockPos pos) {
        return LevelRenderer.getLightColor(level, pos);
    }
}
