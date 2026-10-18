package io.github.mcheliceport.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.Blocks;

/**
 * Upstream {@code MCH_EntityParticleSplash}: a falling drop of water drawn with the smoke texture,
 * thrown up by explosions in water; it splashes and bubbles where it falls back in, and vanishes on
 * the ground.
 */
public class SplashParticle extends SmokeParticle {

    public SplashParticle(ClientLevel level, double x, double y, double z, double mx, double my, double mz) {
        super(level, x, y, z, mx, my, mz);
        if (this.polished) {
            this.setSprite(sprite(net.minecraft.resources.Identifier.fromNamespaceAndPath(
                    "mcheli_ce_port", "m8_splash")));
        }
        float c = this.random.nextFloat() * 0.3F + 0.7F;
        this.setColor(c, c, c);
        this.particleScale = this.random.nextFloat() * 0.5F + 5.0F;
        this.setParticleMaxAge((int) (80.0 / (this.random.nextFloat() * 0.8 + 0.2)) + 2);
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age < this.lifetime) {
            this.frame = (int) (8.0 * this.age / this.lifetime);
            this.age++;
        } else {
            this.remove();
        }
        this.yd -= 0.06F;
        boolean beforeInWater = this.isWaterAt();
        this.move(this.xd, this.yd, this.zd);
        boolean nowInWater = this.isWaterAt();
        if (this.yd < -0.6 && !beforeInWater && nowInWater) {
            double p = -this.yd * 10.0;
            for (int i = 0; i < p; i++) {
                this.level.addParticle(ParticleTypes.SPLASH,
                        this.x + 0.5 + (this.random.nextDouble() - 0.5) * 2.0,
                        this.y + this.random.nextDouble(),
                        this.z + 0.5 + (this.random.nextDouble() - 0.5) * 2.0,
                        (this.random.nextDouble() - 0.5) * 2.0, 4.0, (this.random.nextDouble() - 0.5) * 2.0);
                this.level.addParticle(ParticleTypes.BUBBLE,
                        this.x + 0.5 + (this.random.nextDouble() - 0.5) * 2.0,
                        this.y - this.random.nextDouble(),
                        this.z + 0.5 + (this.random.nextDouble() - 0.5) * 2.0,
                        (this.random.nextDouble() - 0.5) * 2.0, -0.5, (this.random.nextDouble() - 0.5) * 2.0);
            }
        } else if (this.onGround) {
            this.remove();
        }
        this.xd *= 0.9;
        this.zd *= 0.9;
    }

    /** Upstream's test: the block at the position rounded as {@code (int) (pos + 0.5)} is water. */
    private boolean isWaterAt() {
        BlockPos pos = new BlockPos((int) (this.x + 0.5), (int) (this.y + 0.5), (int) (this.z + 0.5));
        return this.level.getBlockState(pos).is(Blocks.WATER);
    }

    @Override
    protected int getLightCoords(float partialTick) {
        // Upstream's splash used the default brightness at its position.
        return net.minecraft.util.LightCoordsUtil.getLightCoords(this.level,
                BlockPos.containing(this.x, this.y, this.z));
    }
}
