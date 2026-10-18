package io.github.mcheliceport.client.particle;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;

/** Fast, low dust fans, reusing the wash sheet without changing helicopter downwash. */
final class BlastGroundDustParticle extends SmokeParticle {
    private final float radius;
    private final org.joml.Quaternionf rotation = new org.joml.Quaternionf();

    BlastGroundDustParticle(ClientLevel level, double x, double y, double z, double mx, double mz,
            float radius, float r, float g, float b, int life) {
        super(level, x, y, z, mx, 0.006, mz);
        this.setSprite(sprite(RotorWashParticle.TEXTURE));
        this.radius = radius;
        this.lifetime = life;
        this.hasPhysics = false;
        this.setColor(r, g, b);
    }

    @Override public void tick() {
        this.xo = this.x; this.yo = this.y; this.zo = this.z;
        if (++this.age >= this.lifetime) { this.remove(); return; }
        this.move(this.xd, this.yd, this.zd);
        this.xd *= 0.88; this.zd *= 0.88;
    }

    @Override public void extract(QuadParticleRenderState state, Camera camera, float partialTick) {
        float progress = Math.clamp((this.age + partialTick) / this.lifetime, 0, 1);
        this.frame = Math.min(3, (int) (progress * 4));
        this.alpha = 0.8F * Math.min(1, (this.age + partialTick) / 2) * (1 - progress) * (1 - progress);
        this.getFacingCameraMode().setRotation(this.rotation, camera, partialTick);
        this.extractRotatedQuad(state, camera, this.rotation, partialTick);
    }

    @Override public float getQuadSize(float partialTick) {
        return this.radius * ExplosionSequence.cloudGrowth(this.age + partialTick, 0, true, true);
    }
    @Override protected float getU0() { return this.frameU(this.frame % 2, 2, false); }
    @Override protected float getU1() { return this.frameU(this.frame % 2, 2, true); }
    @Override protected float getV0() { return this.sprite.getV(this.frame / 2 * 0.5F); }
    @Override protected float getV1() { return this.sprite.getV((this.frame / 2 + 1) * 0.5F); }
}
