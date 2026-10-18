package io.github.mcheliceport.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

/** A tiny four-tick hot jet wisp carries the aircraft's motion instead of hanging in the air. */
final class EngineGlowParticle extends GlowParticle {
    EngineGlowParticle(ClientLevel level, Vec3 position, Vec3 velocity, float radius, float alpha) {
        super(level, position.x, position.y, position.z, radius, 0.1F, 4, 1, 0.65F, 0.3F, alpha);
        this.xd = velocity.x; this.yd = velocity.y; this.zd = velocity.z;
    }
    @Override public void tick() {
        this.xo = this.x; this.yo = this.y; this.zo = this.z;
        if (++this.age >= this.lifetime) { this.remove(); return; }
        this.move(this.xd, this.yd, this.zd);
    }
}
