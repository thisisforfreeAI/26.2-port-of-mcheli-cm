package io.github.mcheliceport.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;

/** Short neutral wisps using existing original wash art and cached ambient light. */
final class EnginePlumeParticle extends SmokeParticle {
    private final float radius, opacity;
    private final org.joml.Quaternionf rotation = new org.joml.Quaternionf();
    private final float angle;

    EnginePlumeParticle(ClientLevel level, double x, double y, double z, double mx, double my, double mz,
            float radius, float opacity, int life, float angle) {
        super(level, x, y, z, mx, my, mz);
        this.setSprite(sprite(RotorWashParticle.TEXTURE));
        this.radius = radius;
        this.opacity = opacity;
        this.lifetime = life;
        this.angle = angle;
        this.hasPhysics = false;
        this.heat = 0;
        this.setColor(0.78F, 0.81F, 0.83F);
    }
    @Override public void tick() {
        this.xo = this.x; this.yo = this.y; this.zo = this.z;
        if (++this.age >= this.lifetime) { this.remove(); return; }
        this.move(this.xd, this.yd, this.zd);
        this.xd = BlastSmokeMotion.advect(this.xd, BlastSmokeMotion.windX(this.level.getGameTime()), 0.92, this.age);
        this.zd = BlastSmokeMotion.advect(this.zd, BlastSmokeMotion.windZ(this.level.getGameTime()), 0.92, this.age);
        this.yd = this.yd * 0.92 + 0.002;
    }
    @Override public void extract(net.minecraft.client.renderer.state.level.QuadParticleRenderState state,
            net.minecraft.client.Camera camera, float partialTick) {
        float age = this.age + partialTick;
        this.frame = Math.min(3, (int) (age * 4 / this.lifetime));
        this.alpha = this.opacity * EngineExhaustLook.fade(age, this.lifetime);
        this.getFacingCameraMode().setRotation(this.rotation, camera, partialTick);
        this.rotation.rotateZ(this.angle);
        this.extractRotatedQuad(state, camera, this.rotation, partialTick);
    }
    @Override public float getQuadSize(float partialTick) { return this.radius * (0.6F + 0.4F * Math.min(1, (this.age + partialTick) / this.lifetime)); }
    @Override protected float getU0() { return this.frameU(this.frame % 2, 2, false); }
    @Override protected float getU1() { return this.frameU(this.frame % 2, 2, true); }
    @Override protected float getV0() { return this.sprite.getV(this.frame / 2 * 0.5F); }
    @Override protected float getV1() { return this.sprite.getV((this.frame / 2 + 1) * 0.5F); }
}
