package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Upstream {@code MCH_EntityParticleSmoke}: the 8-frame smoke puff of explosions, trails, damage
 * and sand clouds. It can grow ({@code diffusible}), fade to white, rise after part of its life and
 * be blown away by nearby rotors.
 */
public class SmokeParticle extends McheliParticle {

    static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("mcheli", "particles/smoke");
    static final Identifier POLISHED_TEXTURE = Identifier.fromNamespaceAndPath("mcheli_ce_port", "m8_smoke");
    /** Upstream's brightness: sky light at full, block light none (it sampled 3000 blocks up). */
    static final int SKY_LIGHT = net.minecraft.util.LightCoordsUtil.FULL_SKY;

    protected int frame;
    public boolean warmStart;
    private float previousScale = Float.NaN;
    private final boolean mirror;

    public SmokeParticle(ClientLevel level, double x, double y, double z, double mx, double my, double mz) {
        super(level, x, y, z, mx, my, mz, TEXTURE);
        if (this.polished) {
            this.setSprite(sprite(POLISHED_TEXTURE));
        }
        this.mirror = this.polished && this.random.nextBoolean();
        float c = this.random.nextFloat() * 0.3F + 0.7F;
        this.setColor(c, c, c);
        this.particleScale = this.random.nextFloat() * 0.5F + 5.0F;
        this.setParticleMaxAge((int) (16.0 / (this.random.nextFloat() * 0.8 + 0.2)) + 2);
    }

    @Override
    public void tick() {
        this.previousScale = this.particleScale;
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age < this.lifetime) {
            this.frame = (int) (8.0 * this.age / this.lifetime);
            this.age++;
            if (this.diffusible && this.particleScale < this.particleMaxScale) {
                this.particleScale = this.polished ? Math.min(this.particleScale + 0.8F, this.particleMaxScale)
                        : this.particleScale + 0.8F;
            }
            if (this.toWhite) {
                float mn = Math.min(Math.min(this.bCol, this.gCol), this.rCol);
                float mx = Math.max(Math.max(this.bCol, this.gCol), this.rCol);
                if (mx - mn > 0.2) {
                    this.rCol = this.rCol + (mx - this.rCol) * 0.016F;
                    this.gCol = this.gCol + (mx - this.gCol) * 0.016F;
                    this.bCol = this.bCol + (mx - this.bCol) * 0.016F;
                }
            }
            this.effectWind();
            if ((float) this.age / this.lifetime > this.motionYUpAge) {
                this.yd += 0.02;
            } else {
                this.yd = this.yd + this.gravityFactor;
            }
            this.move(this.xd, this.yd, this.zd);
            if (this.diffusible) {
                this.xd *= 0.96;
                this.zd *= 0.96;
                this.yd *= 0.96;
            } else {
                this.xd *= 0.9;
                this.zd *= 0.9;
            }
        } else {
            this.remove();
        }
    }

    /** Upstream {@code effectWind}: rotors of nearby aircraft above 0.1 throttle push the smoke away. */
    private void effectWind() {
        if (!this.isEffectedWind) {
            return;
        }
        for (AircraftEntity ac : this.level.getEntitiesOfClass(AircraftEntity.class,
                this.getBoundingBox().inflate(15.0, 15.0, 15.0))) {
            if (ac.getThrottle() > 0.1F) {
                float dx = (float) (this.x - ac.getX());
                float dy = (float) (this.y - ac.getY());
                float dz = (float) (this.z - ac.getZ());
                float dist = Mth.sqrt(dx * dx + dy * dy + dz * dz);
                double vel = (23.0 - dist) * 0.01F * ac.getThrottle();
                double mx = ac.getX() - this.x;
                double mz = ac.getZ() - this.z;
                this.xd -= mx * vel;
                this.zd -= mz * vel;
            }
        }
    }

    @Override
    public float getQuadSize(float partialTick) {
        return 0.1F * (this.polished && !Float.isNaN(this.previousScale)
                ? Mth.lerp(partialTick, this.previousScale, this.particleScale) : this.particleScale);
    }

    @Override
    public void extract(net.minecraft.client.renderer.state.level.QuadParticleRenderState state,
            net.minecraft.client.Camera camera, float partialTick) {
        if (!this.polished) {
            super.extract(state, camera, partialTick);
            return;
        }
        float a = this.alpha;
        float r = this.rCol;
        float g = this.gCol;
        float b = this.bCol;
        float age = Math.max(0.0F, this.age - 1 + partialTick);
        this.alpha *= EffectTuning.fade(age, this.lifetime);
        float heat = this.warmStart ? Math.max(0.0F, 1.0F - age / 6.0F) : 0.0F;
        this.rCol = Mth.lerp(heat, r, 1.0F);
        this.gCol = Mth.lerp(heat, g, 0.58F);
        this.bCol = Mth.lerp(heat, b, 0.22F);
        super.extract(state, camera, partialTick);
        this.alpha = a;
        this.setColor(r, g, b);
    }

    @Override
    protected float getU0() {
        return this.frameU(this.polished ? this.frame % 4 : this.frame, this.polished ? 4 : 8, this.mirror);
    }

    @Override
    protected float getU1() {
        return this.frameU(this.polished ? this.frame % 4 : this.frame, this.polished ? 4 : 8, !this.mirror);
    }

    @Override
    protected float getV0() {
        return this.polished ? this.sprite.getV(this.frame / 4 * 0.5F) : super.getV0();
    }

    @Override
    protected float getV1() {
        return this.polished ? this.sprite.getV((this.frame / 4 + 1) * 0.5F) : super.getV1();
    }

    @Override
    protected int getLightCoords(float partialTick) {
        int heat = this.polished && this.warmStart
                ? (int) (12.0F * Math.max(0.0F, 1.0F - (this.age + partialTick) / 6.0F)) : 0;
        return SKY_LIGHT | heat << 4;
    }
}
