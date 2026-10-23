package io.github.mcheliceport.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;

/** Tiny airborne grass/snow/grit/drop accents. A cached surface plane bounds their fall. */
final class SurfaceDebrisParticle extends McheliParticle {
    private final float radius;
    private final double ground;
    private final float spin;
    private final org.joml.Quaternionf rotation = new org.joml.Quaternionf();

    SurfaceDebrisParticle(ClientLevel level, double x, double y, double z, double mx, double my, double mz,
            float radius, float r, float g, float b, double ground, int life, float spin) {
        super(level, x, y, z, mx, my, mz, net.minecraft.resources.Identifier.fromNamespaceAndPath("mcheli_ce_port", "m8_debris"));
        this.radius = radius; this.ground = ground; this.spin = spin;
        this.lifetime = life; this.hasPhysics = false;
        this.setColor(r, g, b);
    }

    @Override public void tick() {
        xo = x; yo = y; zo = z;
        if (++age >= lifetime || y < ground) { remove(); return; }
        move(xd, yd, zd);
        xd *= 0.96; zd *= 0.96; yd -= 0.025;
        alpha = Math.min(1, (lifetime - age) / 5F);
    }

    @Override public void extract(net.minecraft.client.renderer.state.level.QuadParticleRenderState state,
            net.minecraft.client.Camera camera, float partialTick) {
        getFacingCameraMode().setRotation(rotation, camera, partialTick);
        rotation.rotateZ((age + partialTick) * spin);
        extractRotatedQuad(state, camera, rotation, partialTick);
    }
    @Override public float getQuadSize(float partialTick) { return radius; }
}
