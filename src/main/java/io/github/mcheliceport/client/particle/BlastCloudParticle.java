package io.github.mcheliceport.client.particle;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.resources.Identifier;

/** A cohesive fire lobe becomes a shaded cloud; no block collision or entity wind scans. */
final class BlastCloudParticle extends SmokeParticle {
    static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("mcheli_ce_port", "m8_blast_cloud");
    private final float radius;
    private final int fireTicks;
    private final float opacity;
    private final org.joml.Quaternionf rotation = new org.joml.Quaternionf();
    private final float angle;

    BlastCloudParticle(ClientLevel level, double x, double y, double z, double mx, double my, double mz,
            float radius, int life, int fireTicks, float opacity) {
        super(level, x, y, z, mx, my, mz);
        this.setSprite(sprite(TEXTURE));
        this.radius = radius;
        this.lifetime = life;
        this.fireTicks = fireTicks;
        this.opacity = opacity;
        this.hasPhysics = false;
        this.angle = this.random.nextFloat() * (float) (Math.PI * 2);
        this.heat = 1;
        this.coolingShare = fireTicks / (float) life;
    }

    @Override public void tick() {
        this.xo = this.x; this.yo = this.y; this.zo = this.z;
        if (++this.age >= this.lifetime) { this.remove(); return; }
        this.move(this.xd, this.yd, this.zd);
        this.xd *= 0.91; this.zd *= 0.91;
        this.yd = this.yd * 0.94 + 0.004;
    }

    @Override public void extract(QuadParticleRenderState state, Camera camera, float partialTick) {
        float age = this.age + partialTick;
        // Advance through fire promptly, then spend most of the lifetime as non-emissive smoke.
        this.frame = age < this.fireTicks ? Math.min(2, (int) (age * 3 / this.fireTicks))
                : 3 + Math.min(4, (int) ((age - this.fireTicks) * 5 / Math.max(1, this.lifetime - this.fireTicks)));
        this.alpha = this.opacity * Math.min(1, (this.lifetime - age) / 18);
        this.setColor(1, 1, 1);
        // Skip SmokeParticle's fire tint/fade; the dedicated sheet contains that transition.
        this.getFacingCameraMode().setRotation(this.rotation, camera, partialTick);
        this.rotation.rotateZ(this.angle + age * 0.002F);
        this.extractRotatedQuad(state, camera, this.rotation, partialTick);
    }

    @Override public float getQuadSize(float partialTick) {
        return this.radius * ExplosionSequence.growth(this.age + partialTick, this.lifetime);
    }

}
