package io.github.mcheliceport.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;

/** Flat animated wisps; cached smoke lighting, no buoyancy, wind search or collision queries. */
final class RotorWashParticle extends SmokeParticle {
    static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("mcheli_ce_port", "m8_wash");
    private final float radius, opacity;
    private final double turnSin, turnCos;
    private final org.joml.Quaternionf rotation = new org.joml.Quaternionf();

    RotorWashParticle(ClientLevel level, double x, double y, double z, double mx, double mz,
            float radius, float opacity, float r, float g, float b, int life, double turn) {
        super(level, x, y, z, mx, 0.008, mz);
        this.setSprite(sprite(TEXTURE));
        this.enhanced = true;
        this.radius = radius;
        this.opacity = opacity;
        this.lifetime = life;
        this.hasPhysics = false;
        this.setColor(r, g, b);
        this.turnSin = Math.sin(turn);
        this.turnCos = Math.cos(turn);
    }

    @Override public void tick() {
        this.xo = this.x; this.yo = this.y; this.zo = this.z;
        if (++this.age >= this.lifetime) { this.remove(); return; }
        double dx = this.xd * this.turnCos - this.zd * this.turnSin;
        this.zd = (this.xd * this.turnSin + this.zd * this.turnCos) * 0.97;
        this.xd = dx * 0.97;
        this.move(this.xd, this.yd, this.zd);
    }

    @Override public void extract(net.minecraft.client.renderer.state.level.QuadParticleRenderState state,
            net.minecraft.client.Camera camera, float partialTick) {
        this.frame = Math.min(3, (int) ((this.age + partialTick) * 4 / this.lifetime));
        this.alpha = this.opacity * RotorWashLook.fade(this.age + partialTick, this.lifetime);
        // Skip SmokeParticle's fire/fade path; this particle has its own short, flat-wisp fade.
        this.getFacingCameraMode().setRotation(this.rotation, camera, partialTick);
        this.extractRotatedQuad(state, camera, this.rotation, partialTick);
    }

    @Override public float getQuadSize(float partialTick) { return this.radius * (0.55F + 0.45F * Math.min(1, (this.age + partialTick) / this.lifetime)); }
    @Override protected float getU0() { return this.frameU(this.frame % 2, 2, false); }
    @Override protected float getU1() { return this.frameU(this.frame % 2, 2, true); }
    @Override protected float getV0() { return this.sprite.getV(this.frame / 2 * 0.5F); }
    @Override protected float getV1() { return this.sprite.getV((this.frame / 2 + 1) * 0.5F); }
}
