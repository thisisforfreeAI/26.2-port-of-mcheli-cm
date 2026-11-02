package io.github.mcheliceport.shim.client;

/** 1.21.1 stand-in for 26.2's {@code LivingEntityRenderState}. */
public class LivingEntityRenderState extends EntityRenderState {
    public float bodyRot;
    public float yRot;
    public float xRot;
    public float scale = 1.0F;
    public float walkAnimationPos;
    public float walkAnimationSpeed;
    public boolean isBaby;
    public boolean isInWater;
    public float deathTime;
    public boolean hasRedOverlay;
}
