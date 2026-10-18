package io.github.mcheliceport.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.LightCoordsUtil;

/** A short, tiny ember using one cell of the existing flash sheet and the existing additive layer. */
final class SparkParticle extends McheliParticle {
    SparkParticle(ClientLevel level, double x, double y, double z, double mx, double my, double mz, int life) {
        super(level, x, y, z, mx, my, mz, FlashParticle.POLISHED_TEXTURE);
        this.hasPhysics = false;
        this.lifetime = life;
        this.setColor(1, 0.65F, 0.22F);
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }
        this.move(this.xd, this.yd, this.zd);
        this.xd *= 0.92;
        this.zd *= 0.92;
        this.yd -= 0.025;
        this.alpha = EffectLook.glowAlpha(this.age, this.lifetime);
    }

    @Override public float getQuadSize(float partialTick) { return 0.08F; }
    @Override protected float getU0() { return this.frameU(0, 4, false); }
    @Override protected float getU1() { return this.frameU(0, 4, true); }
    @Override protected float getV1() { return this.sprite.getV(0.5F); }
    @Override protected int getLightCoords(float partialTick) { return LightCoordsUtil.FULL_BRIGHT; }
    @Override protected Layer getLayer() { return ParticleLayers.ADDITIVE; }
}
