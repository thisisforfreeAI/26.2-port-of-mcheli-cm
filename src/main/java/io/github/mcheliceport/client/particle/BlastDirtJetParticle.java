package io.github.mcheliceport.client.particle;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.resources.Identifier;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** A dust stream with optional tapered world-space segments; no trail emitter or block scans. */
final class BlastDirtJetParticle extends SmokeParticle {
    static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("mcheli_ce_port", "m8_dirt_jet");
    private final float radius;
    private final boolean column;
    private final float opacity;
    private final Quaternionf rotation = new Quaternionf();
    private final Vector3f axis;

    BlastDirtJetParticle(ClientLevel level, double x, double y, double z, double mx, double my, double mz,
            float radius, float r, float g, float b, int life, float opacity) {
        this(level,x,y,z,mx,my,mz,radius,r,g,b,life,opacity,false);
    }
    BlastDirtJetParticle(ClientLevel level,double x,double y,double z,double mx,double my,double mz,
            float radius,float r,float g,float b,int life,float opacity,boolean column) {
        super(level,x,y,z,mx,my,mz);
        this.column=column;
        this.setSprite(sprite(TEXTURE));
        this.radius = radius;
        this.opacity = opacity;
        this.lifetime = Math.clamp(life, 12, 28);
        this.hasPhysics = false;
        this.axis = new Vector3f((float) mx, (float) my, (float) mz).normalize();
        this.setColor(r, g, b);
    }

    @Override public void tick() {
        this.xo = this.x; this.yo = this.y; this.zo = this.z;
        if (++this.age >= this.lifetime) { this.remove(); return; }
        this.move(this.xd, this.yd, this.zd);
        this.xd *= 0.90; this.zd *= 0.90; this.yd *= 0.91;
    }

    @Override public void extract(QuadParticleRenderState state, Camera camera, float partialTick) {
        float age = this.age + partialTick, progress = Math.clamp(age / this.lifetime, 0, 1);
        this.frame = Math.min(3, (int) (progress * 4));
        this.alpha = this.opacity * Math.min(1, age / 2) * (1 - progress) * (1 - progress);
        this.getFacingCameraMode().setRotation(this.rotation, camera, partialTick);
        // Project the fixed world-space launch axis onto the billboard, including near-vertical views.
        float ax = this.axis.x, ay = this.axis.y, az = this.axis.z;
        this.rotation.transformInverse(this.axis);
        float angle = (float) Math.atan2(-this.axis.x, this.axis.y);
        this.axis.set(ax, ay, az);
        this.rotation.rotateZ(angle);
        if (!column || !EffectAdmission.depthDetail(level,x,y,z)) {
            this.extractRotatedQuad(state, camera, this.rotation, partialTick);
            return;
        }
        var pos=camera.position();
        float px=(float)(net.minecraft.util.Mth.lerp(partialTick,xo,x)-pos.x);
        float py=(float)(net.minecraft.util.Mth.lerp(partialTick,yo,y)-pos.y);
        float pz=(float)(net.minecraft.util.Mth.lerp(partialTick,zo,z)-pos.z);
        float r=this.getQuadSize(partialTick);
        int color=net.minecraft.util.ARGB.colorFromFloat(alpha,rCol,gCol,bCol), light=getLightCoords(partialTick);
        for(int i=0;i<3;i++) {
            rotation.rotationY(i*(float)Math.PI/3).rotateZ(angle*.3F);
            float offset=i*.55F*r;
            state.add(getLayer(),px+axis.x*offset,py+axis.y*offset,pz+axis.z*offset,
                    rotation.x,rotation.y,rotation.z,rotation.w,r*BlastDepthLook.columnScale(i),
                    getU0(),getU1(),getV0(),getV1(),color,light);
        }
    }

    @Override public float getQuadSize(float partialTick) {
        return this.radius * ExplosionSequence.cloudGrowth(this.age + partialTick, 0, true, false);
    }
    @Override protected float getU0() { return this.frameU(this.frame % 2, 2, false); }
    @Override protected float getU1() { return this.frameU(this.frame % 2, 2, true); }
    @Override protected float getV0() { return this.sprite.getV(this.frame / 2 * 0.5F); }
    @Override protected float getV1() { return this.sprite.getV((this.frame / 2 + 1) * 0.5F); }
}
