package io.github.mcheliceport.client.particle;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import org.joml.Quaternionf;

/** A short low dust wisp carried by an expanding broken pressure front. */
final class BlastRingParticle extends SmokeParticle {
    private final Quaternionf rotation;
    private final boolean mesh;
    private final float radius, opacity, reach;
    private final double dx, dz;
    BlastRingParticle(ClientLevel level, double x, double y, double z, double dx, double dz,
            float reach, float radius, float[] tint, float opacity) {
        this(level,x,y,z,dx,dz,reach,radius,tint,opacity,false);
    }
    BlastRingParticle(ClientLevel level,double x,double y,double z,double dx,double dz,
            float reach,float radius,float[] tint,float opacity,boolean mesh) {
        super(level, x, y, z, 0, 0, 0);
        this.mesh=mesh;
        this.setSprite(sprite(BlastGroundDustParticle.TEXTURE));
        this.hasPhysics = false; this.radius = radius; this.opacity = opacity;
        this.dx = dx; this.dz = dz; this.reach = reach;
        this.lifetime = 18;
        this.rotation = new Quaternionf();
        this.setColor(tint[0], tint[1], tint[2]);
    }
    @Override public void tick() {
        xo = x; yo = y; zo = z;
        if (++age >= lifetime) { remove(); return; }
        double distance = reach * (BlastRingLook.travel(age) - BlastRingLook.travel(age - 1));
        move(dx * distance, 0, dz * distance);
    }
    @Override public void extract(QuadParticleRenderState state, Camera camera, float partialTick) {
        frame = Math.min(3, (int) ((age + partialTick) * 4 / lifetime));
        alpha = opacity * BlastRingLook.fade(age + partialTick, lifetime);
        if (mesh) {
            if (!EffectAdmission.depthDetail(level,x,y,z)) return;
            rotation.rotationY((float)Math.atan2(dx,dz));
        } else getFacingCameraMode().setRotation(rotation, camera, partialTick);
        extractRotatedQuad(state, camera, rotation, partialTick);
    }
    @Override public net.minecraft.client.particle.ParticleRenderType getGroup() {
        return mesh ? BlastDustWedges.TYPE : super.getGroup();
    }
    @Override public float getQuadSize(float partialTick) { return radius * (0.65F + 0.35F * Math.min(1, (age + partialTick) / 8)); }
    @Override protected float getU0() { return frameU(frame % 2, 2, false); }
    @Override protected float getU1() { return frameU(frame % 2, 2, true); }
    @Override protected float getV0() { return sprite.getV(frame / 2 * 0.5F); }
    @Override protected float getV1() { return sprite.getV((frame / 2 + 1) * 0.5F); }
}
