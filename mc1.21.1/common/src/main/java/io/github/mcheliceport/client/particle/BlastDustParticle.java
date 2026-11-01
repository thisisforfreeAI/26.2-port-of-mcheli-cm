package io.github.mcheliceport.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;

/** A handful of fast outward puffs. Existing smoke art/light cache, no collision or wind queries. */
final class BlastDustParticle extends SmokeParticle {
    BlastDustParticle(ClientLevel level, double x, double y, double z, double mx, double my, double mz,
            float r, float g, float b, int life) {
        super(level, x, y, z, mx, my, mz);
        this.enhanced = true;
        this.diffusible = true;
        this.hasPhysics = false;
        this.particleScale = 3;
        this.particleMaxScale = 8;
        this.lifetime = life;
        this.motionYUpAge = 2;
        this.gravityFactor = -0.002F;
        this.alpha = 0.45F;
        this.setColor(r, g, b);
    }
}
