package io.github.mcheliceport.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/** Vanilla-lit pixel drops. A few cheap block collisions stop blood passing through nearby walls. */
final class BloodParticle extends McheliParticle {
    private final float radius;
    BloodParticle(ClientLevel level, Vec3 at, Vec3 velocity, float radius, int life, float shade) {
        super(level, at.x, at.y, at.z, velocity.x, velocity.y, velocity.z,
                Identifier.fromNamespaceAndPath("mcheli_ce_port", "m8_debris"));
        this.radius = radius; lifetime = life; hasPhysics = true;
        setSize(radius*2, radius*2);
        setColor(.38F+shade*.14F, .025F+shade*.015F, .025F+shade*.02F);
    }
    @Override public void tick() {
        xo=x; yo=y; zo=z;
        if (!VehicleBloodEffects.enabled() || ++age >= lifetime) { remove(); return; }
        move(xd, yd, zd);
        if (onGround) { remove(); return; }
        xd*=.92; zd*=.92; yd-=.025;
        alpha=Math.min(1, (lifetime-age)/5F);
    }
    @Override public float getQuadSize(float partial) { return radius; }
}
