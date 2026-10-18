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
    private final float smokeShade;

    BlastCloudParticle(ClientLevel level, double x, double y, double z, double mx, double my, double mz,
            float radius, int life, int fireTicks, float opacity) {
        super(level, x, y, z, mx, my, mz);
        this.setSprite(sprite(TEXTURE));
        this.radius = radius;
        this.lifetime = life;
        this.fireTicks = fireTicks;
        this.opacity = opacity;
        this.hasPhysics = false;
        this.angle = this.random.nextInt(4) * (float) (Math.PI / 2);
        this.smokeShade = 0.78F + this.random.nextFloat() * 0.22F;
        this.heat = fireTicks > 0 ? 1 : 0;
        this.coolingShare = fireTicks / (float) life;
    }

    @Override public void tick() {
        this.xo = this.x; this.yo = this.y; this.zo = this.z;
        if (++this.age >= this.lifetime) { this.remove(); return; }
        this.move(this.xd, this.yd, this.zd);
        double drag = this.fireTicks > 0 && this.age < 8 ? 0.84 : 0.95;
        this.xd *= drag; this.zd *= drag;
        this.yd = this.yd * (this.fireTicks > 0 ? 0.9 : 0.97) + 0.005;
    }

    @Override public void extract(QuadParticleRenderState state, Camera camera, float partialTick) {
        float age = this.age + partialTick;
        // Advance through fire promptly, then spend most of the lifetime as non-emissive smoke.
        this.frame = age < this.fireTicks ? Math.min(2, (int) (age * 3 / this.fireTicks))
                : 3 + Math.min(4, (int) ((age - this.fireTicks) * 5 / Math.max(1, this.lifetime - this.fireTicks)));
        float entrance = this.fireTicks > 0 ? 1 : Math.clamp((age - 3) / 5, 0, 1);
        this.alpha = this.opacity * entrance * Math.min(1, (this.lifetime - age) / 18);
        float shade = age >= this.fireTicks ? this.smokeShade : 1;
        this.setColor(shade, shade, shade);
        // Skip SmokeParticle's fire tint/fade; the dedicated sheet contains that transition.
        this.getFacingCameraMode().setRotation(this.rotation, camera, partialTick);
        this.rotation.rotateZ(this.angle);
        this.extractRotatedQuad(state, camera, this.rotation, partialTick);
    }

    @Override public float getQuadSize(float partialTick) {
        return this.radius * ExplosionSequence.growth(this.age + partialTick,
                this.fireTicks > 0 ? Math.min(this.lifetime, 24) : this.lifetime);
    }

}
