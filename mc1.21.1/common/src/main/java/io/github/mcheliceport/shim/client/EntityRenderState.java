package io.github.mcheliceport.shim.client;

import net.minecraft.world.entity.EntityType;
import org.jspecify.annotations.Nullable;

/** 1.21.1 stand-in for 26.2's {@code EntityRenderState}: what a renderer extracted from its entity. */
public class EntityRenderState {
    public static final int NO_OUTLINE = 0;

    public @Nullable EntityType<?> entityType;
    public double x;
    public double y;
    public double z;
    public float ageInTicks;
    public float partialTick;
    public float boundingBoxWidth;
    public float boundingBoxHeight;
    public float eyeHeight;
    public double distanceToCameraSq;
    public boolean isInvisible;
    public boolean isDiscrete;
    public boolean displayFireAnimation;
    public int lightCoords = 0xF000F0;
    public int outlineColor = NO_OUTLINE;
    public float shadowRadius;
}
