package io.github.mcheliceport.client.particle;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.resources.Identifier;

/** A cohesive fire lobe becomes a shaded cloud; no block collision or entity wind scans. */
final class BlastCloudParticle extends SmokeParticle {
    static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("mcheli_ce_port", "m8_blast_cloud");
    static final Identifier MODERN_TEXTURE = Identifier.fromNamespaceAndPath("mcheli_ce_port", "m8_modern_blast_cloud");
    private final boolean modern;
    private final float radius;
    private final int cards;
    private final int fireTicks;
    private final float opacity;
    private final int backlightTicks;
    private final org.joml.Quaternionf rotation = new org.joml.Quaternionf();
    private final float angle;
    private final float smokeShade;
    private final int delay;
    private final boolean base;
    private final double turnSin, turnCos;
    private final int fadeTicks;

    BlastCloudParticle(ClientLevel level, double x, double y, double z, double mx, double my, double mz,
            float radius, int life, int fireTicks, float opacity) {
        this(level, x, y, z, mx, my, mz, radius, life, fireTicks, opacity, 0, false);
    }

    BlastCloudParticle(ClientLevel level, double x, double y, double z, double mx, double my, double mz,
            float radius, int life, int fireTicks, float opacity, int delay, boolean base) {
        this(level, x, y, z, mx, my, mz, radius, life, fireTicks, opacity, delay, base, 0);
    }

    BlastCloudParticle(ClientLevel level, double x, double y, double z, double mx, double my, double mz,
            float radius, int life, int fireTicks, float opacity, int delay, boolean base, int backlightTicks) {
        this(level, x, y, z, mx, my, mz, radius, life, fireTicks, opacity, delay, base, backlightTicks, 1);
    }

    BlastCloudParticle(ClientLevel level, double x, double y, double z, double mx, double my, double mz,
            float radius, int life, int fireTicks, float opacity, int delay, boolean base, int backlightTicks, int cards) {
        this(level,x,y,z,mx,my,mz,radius,life,fireTicks,opacity,delay,base,backlightTicks,cards,false);
    }

    BlastCloudParticle(ClientLevel level,double x,double y,double z,double mx,double my,double mz,
            float radius,int life,int fireTicks,float opacity,int delay,boolean base,int backlightTicks,int cards,boolean modern) {
        super(level, x, y, z, mx, my, mz);
        var art=modern?sprite(MODERN_TEXTURE):sprite(TEXTURE);
        this.modern=modern && art.contents().name().equals(MODERN_TEXTURE);
        this.cards = Math.clamp(cards, 1, 3);
        this.setSprite(modern && !this.modern ? sprite(TEXTURE) : art);
        this.radius = radius;
        this.delay = Math.clamp(delay, 0, 12);
        this.base = base;
        this.lifetime = Math.min(128, life + this.delay);
        this.fireTicks = fireTicks;
        this.backlightTicks = Math.clamp(backlightTicks, 0, 56);
        this.opacity = opacity;
        this.hasPhysics = false;
        this.angle = this.modern ? (this.random.nextFloat()-.5F)*.5F : this.random.nextInt(4) * (float) (Math.PI / 2);
        this.smokeShade = 0.78F + this.random.nextFloat() * 0.22F;
        // The dedicated heat curve below controls fire lighting; inherited sampling stays ambient.
        this.heat = 0;
        double turn = (this.random.nextBoolean() ? 1 : -1) * (0.018 + this.random.nextDouble() * 0.025);
        this.turnSin = Math.sin(turn);
        this.turnCos = Math.cos(turn);
        this.fadeTicks = 24 + this.random.nextInt(24);
    }

    @Override public void tick() {
        this.xo = this.x; this.yo = this.y; this.zo = this.z;
        if (++this.age >= this.lifetime) { this.remove(); return; }
        if (this.age <= this.delay) return;
        this.move(this.xd, this.yd, this.zd);
        // A fast impulse settles into a slow curl. Base smoke stays at the impact instead of
        // joining every other puff in a detached rising ball.
        double drag = this.fireTicks > 0 ? 0.82 : this.base ? 0.91 : 0.95;
        double curl = BlastSmokeMotion.curlShare(this.age - this.delay);
        double dx = this.xd * this.turnCos - this.zd * this.turnSin;
        double dz = this.xd * this.turnSin + this.zd * this.turnCos;
        this.xd += (dx - this.xd) * curl;
        this.zd += (dz - this.zd) * curl;
        this.yd = this.yd * (this.base ? 0.90 : 0.97) + (this.base ? 0.001 : 0.002);
        this.xd = BlastSmokeMotion.advect(this.xd, BlastSmokeMotion.windX(this.level.getGameTime()),
                drag, this.age - this.delay);
        this.zd = BlastSmokeMotion.advect(this.zd, BlastSmokeMotion.windZ(this.level.getGameTime()),
                drag, this.age - this.delay);
    }

    @Override public void extract(QuadParticleRenderState state, Camera camera, float partialTick) {
        float age = this.age + partialTick;
        // This render state accumulates quads for the whole group; hidden billows add none.
        if (age <= this.delay) return;
        float localAge = Math.max(0, age - this.delay);
        // Advance through fire promptly, then spend most of the lifetime as non-emissive smoke.
        if (localAge < this.fireTicks) this.frame = Math.min(11, (int) (localAge * 12 / this.fireTicks));
        else if (localAge < this.backlightTicks) this.frame = 8 + Math.min(3, (int) (localAge * 4 / this.backlightTicks));
        else this.frame = 12 + Math.min(3, (int) ((localAge - this.fireTicks) * 4
                / Math.max(1, this.lifetime - this.delay - this.fireTicks)));
        this.alpha = this.opacity * ExplosionSequence.cloudAlpha(age, this.lifetime, this.delay, this.fireTicks > 0, this.fadeTicks);
        if (this.modern) this.alpha *= this.fireTicks>0 ? 0.92F : 0.84F;
        float shade = localAge >= this.fireTicks ? this.smokeShade : 1;
        this.setColor(shade, shade, shade);
        // Skip SmokeParticle's fire tint/fade; the dedicated sheet contains that transition.
        this.getFacingCameraMode().setRotation(this.rotation, camera, partialTick);
        this.rotation.rotateZ(this.angle + (this.fireTicks > 0 ? localAge * 0.018F : 0));
        boolean depth = this.cards > 1 && EffectAdmission.depthDetail(this.level, this.x, this.y, this.z);
        float alpha = this.alpha;
        if (depth && this.cards == 3) this.alpha *= 0.85F;
        this.extractRotatedQuad(state, camera, this.rotation, partialTick);
        if (depth && this.cards == 3) {
            this.alpha = alpha * 0.25F;
            this.rotation.rotationY(this.angle).rotateX(0.12F);
            this.extractRotatedQuad(state, camera, this.rotation, partialTick);
            this.rotation.rotateY((float) Math.PI / 2);
            this.extractRotatedQuad(state, camera, this.rotation, partialTick);
        } else if (depth && localAge < this.fireTicks) {
            var pos = camera.position();
            float r = this.getQuadSize(partialTick);
            this.alpha = alpha * 0.65F;
            this.extractRotatedQuad(state, this.rotation,
                    (float)(net.minecraft.util.Mth.lerp(partialTick,this.xo,this.x)-pos.x) + (float)Math.cos(this.angle)*r*0.18F,
                    (float)(net.minecraft.util.Mth.lerp(partialTick,this.yo,this.y)-pos.y) + r*0.16F,
                    (float)(net.minecraft.util.Mth.lerp(partialTick,this.zo,this.z)-pos.z) + (float)Math.sin(this.angle)*r*0.18F,
                    partialTick);
        }
        this.alpha = alpha;
    }

    @Override public float getQuadSize(float partialTick) {
        return this.radius * ExplosionSequence.cloudGrowth(this.age + partialTick, this.delay, this.fireTicks > 0, this.base)
                * ExplosionSequence.fireRoll(this.age + partialTick - this.delay, this.fireTicks, this.angle);
    }

    @Override protected float getU0() { return this.frameU(this.frame % 4, 4, false); }
    @Override protected float getU1() { return this.frameU(this.frame % 4, 4, true); }
    @Override protected float getV0() { return this.sprite.getV(this.frame / 4 * 0.25F); }
    @Override protected float getV1() { return this.sprite.getV((this.frame / 4 + 1) * 0.25F); }

    @Override protected int getLightCoords(float partialTick) {
        return EffectLook.smokeLight(super.getLightCoords(partialTick),
                Math.max(ExplosionSequence.fireHeat(this.age + partialTick - this.delay, this.fireTicks),
                        ExplosionSequence.backlight(this.age + partialTick - this.delay, this.backlightTicks)));
    }

}
