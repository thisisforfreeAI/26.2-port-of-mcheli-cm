package io.github.mcheliceport.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.LightCoordsUtil;
import org.joml.Quaternionf;

/** One faint broken ring on actual water, sharing the smoke live/admission budget. */
final class RotorRippleParticle extends McheliParticle {
    static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("mcheli_ce_port", "m8_wash_ripple");
    private final Quaternionf facing;
    private final BlockPos water;
    private final float radius, opacity;
    private final int light;

    RotorRippleParticle(ClientLevel level, double x, double y, double z, float radius, float opacity, float angle) {
        super(level, x, y, z, 0, 0, 0, TEXTURE);
        this.radius = radius;
        this.opacity = opacity;
        this.lifetime = 22;
        this.hasPhysics = false;
        this.water = BlockPos.containing(x, y - 0.03, z);
        this.light = LightCoordsUtil.getLightCoords(level, this.water.above());
        this.facing = new Quaternionf().rotationX(-(float) Math.PI / 2).rotateZ(angle);
        this.setColor(0.78F, 0.9F, 0.95F);
    }

    @Override public void tick() {
        this.xo = this.x; this.yo = this.y; this.zo = this.z;
        if (++this.age >= this.lifetime || this.age % 5 == 0
                && (!this.level.hasChunkAt(this.water) || !this.level.getFluidState(this.water).is(FluidTags.WATER))) this.remove();
    }

    @Override public void extract(net.minecraft.client.renderer.state.level.QuadParticleRenderState state,
            net.minecraft.client.Camera camera, float partialTick) {
        this.alpha = this.opacity * RotorWashLook.fade(this.age + partialTick, this.lifetime);
        this.extractRotatedQuad(state, camera, this.facing, partialTick);
    }

    @Override public float getQuadSize(float partialTick) { return this.radius * (0.4F + 0.6F * Math.min(1, (this.age + partialTick) / this.lifetime)); }
    @Override protected int getLightCoords(float partialTick) { return this.light; }
}
