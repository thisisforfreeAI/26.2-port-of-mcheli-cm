package io.github.mcheliceport.client.particle;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.data.AtlasIds;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * Base of McHeli's own particles: upstream {@code MCH_EntityParticleBase} on 1.12's particle
 * behaviour. The texture is one sprite of the particle atlas holding a strip of frames; the
 * subclass picks the frame's part of it.
 */
abstract class McheliParticle extends SingleQuadParticle {

    public boolean isEffectedWind;
    public boolean diffusible;
    public boolean toWhite;
    public float particleScale;
    public float particleMaxScale;
    public float gravityFactor;
    public float motionYUpAge;

    McheliParticle(ClientLevel level, double x, double y, double z, double mx, double my, double mz,
            Identifier sprite) {
        super(level, x, y, z, sprite(sprite));
        // 1.12's Particle defaults, then upstream's exact motion.
        this.particleScale = (this.random.nextFloat() * 0.5F + 0.5F) * 2.0F;
        this.particleMaxScale = this.particleScale;
        this.xd = mx;
        this.yd = my;
        this.zd = mz;
    }

    static TextureAtlasSprite sprite(Identifier id) {
        return Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.PARTICLES)
                .getSprite(id);
    }

    public void setAlphaF(float a) {
        this.alpha = a;
    }

    public void setParticleMaxAge(int age) {
        this.lifetime = age;
    }

    /**
     * 1.12's {@code Particle#move}: collide with blocks and stop on the axes that hit. Vanilla 26.2
     * stops a particle for good once it lands, which would freeze smoke on the ground.
     */
    @Override
    public void move(double xa, double ya, double za) {
        double ox = xa;
        double oy = ya;
        double oz = za;
        if (this.hasPhysics && (xa != 0.0 || ya != 0.0 || za != 0.0)) {
            Vec3 movement = Entity.collideBoundingBox(CollisionContext.positionContext(this.y), new Vec3(xa, ya, za),
                    this.getBoundingBox(), this.level, List.of());
            xa = movement.x;
            ya = movement.y;
            za = movement.z;
        }
        if (xa != 0.0 || ya != 0.0 || za != 0.0) {
            AABB bb = this.getBoundingBox().move(xa, ya, za);
            this.setBoundingBox(bb);
            this.setLocationFromBoundingbox();
        }
        this.onGround = oy != ya && oy < 0.0;
        if (ox != xa) {
            this.xd = 0.0;
        }
        if (oz != za) {
            this.zd = 0.0;
        }
    }

    /** One of {@code frames} equal columns of the sprite, upstream's {@code particleTextureIndexX}. */
    protected float frameU(int frame, int frames, boolean end) {
        float f = (frame + (end ? 1.0F : 0.0F)) / frames;
        return this.sprite.getU0() + (this.sprite.getU1() - this.sprite.getU0()) * f;
    }

    @Override
    protected Layer getLayer() {
        return Layer.TRANSLUCENT;
    }
}
