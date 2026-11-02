package io.github.mcheliceport.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import java.util.Optional;
import net.minecraft.core.particles.ParticleGroup;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Upstream {@code MCH_EntityBlockDustFX} (1.12's {@code ParticleBlockDust}): a block fragment that
 * keeps the exact motion it is given and can be scaled up, for the dust clouds of explosions.
 */
public class BlockDustParticle extends TerrainParticle {

    private final Optional<ParticleGroup> particleLimit = EffectAdmission.limit(false);

    @Override
    public Optional<ParticleGroup> getParticleGroup() {
        return this.particleLimit;
    }

    /** 26.2's particle drawing entry point; by default vanilla's quad. */
    public void extract(io.github.mcheliceport.shim.client.QuadParticleRenderState state,
            net.minecraft.client.Camera camera, float partialTick) {
        super.render(state.buffer(), camera, partialTick);
    }

    @Override
    public void render(com.mojang.blaze3d.vertex.VertexConsumer buffer, net.minecraft.client.Camera camera,
            float partialTick) {
        this.extract(new io.github.mcheliceport.shim.client.QuadParticleRenderState(buffer), camera, partialTick);
    }

    public BlockDustParticle(ClientLevel level, double x, double y, double z, double mx, double my, double mz,
            BlockState state, BlockPos pos) {
        super(level, x, y, z, mx, my, mz, state, pos);
        this.xd = mx;
        this.yd = my;
        this.zd = mz;
    }

    /** Upstream {@code setScale}: 1.12 drew a digging particle {@code 0.1 × scale} wide each way. */
    public void setScale(float scale) {
        this.quadSize = 0.1F * scale;
    }
}
