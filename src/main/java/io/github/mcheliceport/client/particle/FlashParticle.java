package io.github.mcheliceport.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;

/**
 * Upstream {@code MCH_EntityParticleExplode}: the bright 8-frame muzzle and explosion flash
 * ({@code flash.png}, four frames by two), turned at a random angle, played over
 * {@code (1 + age) % 7} ticks.
 *
 * <p>Upstream's flash never expired when that count was 0 (it was drawn as nothing until the
 * particle limit dropped it); the port removes it at once.
 */
public class FlashParticle extends McheliParticle {

    static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("mcheli", "flash");
    private static final int FULL_BRIGHT = 0xF000F0;

    private int nowCount;
    private final int endCount;
    private final float size;
    private int currentFrame;

    public FlashParticle(ClientLevel level, double x, double y, double z, float size, double age) {
        super(level, x, y, z, 0.0, 0.0, 0.0, TEXTURE);
        this.endCount = (1 + (int) age) % 7;
        this.roll = -45.0F + this.random.nextFloat() * 90.0F;
        this.oRoll = this.roll;
        this.size = size + this.random.nextFloat() * 0.2F;
        this.hasPhysics = false;
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        this.nowCount++;
        if (this.nowCount == this.endCount || this.endCount == 0) {
            this.remove();
        }
    }

    private int frame(float partialTick) {
        return this.endCount == 0 ? 8 : (int) ((this.nowCount + partialTick) * 7.0F / this.endCount);
    }

    @Override
    public void extract(net.minecraft.client.renderer.state.level.QuadParticleRenderState state,
            net.minecraft.client.Camera camera, float partialTick) {
        this.currentFrame = this.frame(partialTick);
        if (this.currentFrame > 7) {
            return;
        }
        super.extract(state, camera, partialTick);
    }

    @Override
    public float getQuadSize(float partialTick) {
        return this.size * 1.2F;
    }

    @Override
    protected float getU0() {
        return this.cell(this.currentFrame % 4, 4, true);
    }

    @Override
    protected float getU1() {
        return this.cell(this.currentFrame % 4 + 1, 4, true);
    }

    @Override
    protected float getV0() {
        return this.cell(this.currentFrame / 4, 2, false);
    }

    @Override
    protected float getV1() {
        return this.cell(this.currentFrame / 4 + 1, 2, false);
    }

    private float cell(int index, int count, boolean u) {
        float f = (float) index / count;
        return u ? this.sprite.getU0() + (this.sprite.getU1() - this.sprite.getU0()) * f
                : this.sprite.getV0() + (this.sprite.getV1() - this.sprite.getV0()) * f;
    }

    @Override
    protected int getLightCoords(float partialTick) {
        return FULL_BRIGHT;
    }
}
