package io.github.mcheliceport.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.LightCoordsUtil;

/** One short hot core on the existing additive sheet; shares smoke limits, not the weapon reserve. */
final class ImpactCoreParticle extends McheliParticle {
    private final float radius;
    private int frame;

    ImpactCoreParticle(ClientLevel level, double x, double y, double z, float radius, int life) {
        super(level, x, y, z, 0, 0, 0,
                net.minecraft.resources.Identifier.fromNamespaceAndPath("mcheli_ce_port", "m8_blast_flash"));
        this.radius = radius;
        this.lifetime = life;
        this.hasPhysics = false;
        this.setColor(1, 0.95F, 0.8F);
    }

    @Override public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (++this.age >= this.lifetime) this.remove();
    }

    @Override public void extract(net.minecraft.client.renderer.state.level.QuadParticleRenderState state,
            net.minecraft.client.Camera camera, float partialTick) {
        float progress = Math.clamp((this.age + partialTick) / this.lifetime, 0, 1);
        this.frame = Math.min(7, (int) (progress * 8));
        this.setColor(1, 0.96F - progress * 0.30F, 0.86F - progress * 0.65F);
        this.alpha = Math.min(1, EffectLook.glowAlpha(this.age + partialTick, this.lifetime) * 2);
        super.extract(state, camera, partialTick);
    }

    @Override public float getQuadSize(float partialTick) {
        return this.radius * ExplosionSequence.flashGrowth(this.age + partialTick, this.lifetime);
    }
    @Override protected float getU0() { return this.frameU(this.frame % 4, 4, false); }
    @Override protected float getU1() { return this.frameU(this.frame % 4, 4, true); }
    @Override protected float getV0() { return this.sprite.getV(this.frame / 4 * 0.5F); }
    @Override protected float getV1() { return this.sprite.getV((this.frame / 4 + 1) * 0.5F); }
    @Override protected int getLightCoords(float partialTick) { return LightCoordsUtil.FULL_BRIGHT; }
    @Override protected Layer getLayer() { return ParticleLayers.ADDITIVE; }
}
