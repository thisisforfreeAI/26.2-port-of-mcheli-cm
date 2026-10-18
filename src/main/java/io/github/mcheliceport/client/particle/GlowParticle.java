package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.McheliCePort;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;

/**
 * An M8 glow: a soft, full-bright, additive disc that flares up and dies away, under explosion
 * fireballs and muzzle flashes. Not in upstream; drawn only with
 * {@link io.github.mcheliceport.config.EffectQuality#ENHANCED}.
 */
public class GlowParticle extends McheliParticle {

    static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "particle/glow");

    private final float size;
    private final float growth;
    private final float baseAlpha;

    /**
     * @param size   radius at birth, in blocks
     * @param growth radius gained over the glow's life, as a share of {@code size}
     */
    public GlowParticle(ClientLevel level, double x, double y, double z, float size, float growth, int lifetime,
            float r, float g, float b, float a) {
        super(level, x, y, z, 0.0, 0.0, 0.0, TEXTURE);
        this.size = size;
        this.growth = growth;
        this.baseAlpha = a;
        this.setColor(r, g, b);
        this.alpha = a;
        this.setParticleMaxAge(Math.max(1, lifetime));
        this.hasPhysics = false;
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
        }
    }

    @Override
    public void extract(net.minecraft.client.renderer.state.level.QuadParticleRenderState state,
            net.minecraft.client.Camera camera, float partialTick) {
        this.alpha = this.baseAlpha * EffectLook.glowAlpha(this.age + partialTick, this.lifetime);
        super.extract(state, camera, partialTick);
    }

    @Override
    public float getQuadSize(float partialTick) {
        float t = Math.min(1.0F, (this.age + partialTick) / this.lifetime);
        return this.size * (1.0F + this.growth * t);
    }

    @Override
    protected int getLightCoords(float partialTick) {
        return LightCoordsUtil.FULL_BRIGHT;
    }

    @Override
    protected Layer getLayer() {
        return ParticleLayers.ADDITIVE;
    }
}
