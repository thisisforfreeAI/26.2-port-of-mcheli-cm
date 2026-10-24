package io.github.mcheliceport.client.particle;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Three block-textured faces, one damped bounce on the sampled birth plane, then a settled fade. */
final class BlastFragmentParticle extends BlockDustParticle {
    private final float radius, spinX, spinY, spinZ, phase;
    private final double floor;
    private final boolean hot;
    private boolean bounced, settled;
    private int settledAge;
    private final Quaternionf body = new Quaternionf(), face = new Quaternionf();
    private final Vector3f view = new Vector3f(), normal = new Vector3f();
    private final BlockPos.MutableBlockPos lightPos = new BlockPos.MutableBlockPos();
    private long sampledBlock = Long.MIN_VALUE;
    private int lightAge = -4, ambient;

    BlastFragmentParticle(ClientLevel level, double x, double y, double z, double mx, double my, double mz,
            BlockState block, BlockPos pos, double floor, float radius, int life, boolean hot) {
        super(level, x, y, z, mx, my, mz, block, pos);
        this.floor = floor;
        this.radius = radius;
        this.lifetime = Math.clamp(life, 8, 60);
        this.hot = hot;
        this.hasPhysics = false;
        this.rCol *= 1.3F; this.gCol *= 1.3F; this.bCol *= 1.3F;
        this.spinX = (this.random.nextFloat() - 0.5F) * 0.5F;
        this.spinY = (this.random.nextFloat() - 0.5F) * 0.4F;
        this.spinZ = (this.random.nextFloat() - 0.5F) * 0.5F;
        this.phase = this.random.nextFloat() * (float) Math.PI * 2;
    }

    @Override public void tick() {
        this.xo = this.x; this.yo = this.y; this.zo = this.z;
        if (++this.age >= this.lifetime) { this.remove(); return; }
        if (this.settled) return;
        if (this.yd < 0 && this.y + this.yd <= this.floor + this.radius) {
            this.setPos(this.x, this.floor + this.radius, this.z);
            if (!this.bounced) {
                this.bounced = true; this.yd = -this.yd * 0.28;
                this.xd *= 0.45; this.zd *= 0.45;
            } else {
                this.settled = true; this.settledAge = this.age;
                this.xd = this.yd = this.zd = 0;
            }
            return;
        }
        this.move(this.xd, this.yd, this.zd);
        this.xd *= 0.98; this.zd *= 0.98;
        this.yd -= 0.045;
    }

    @Override public void extract(QuadParticleRenderState state, Camera camera, float partialTick) {
        float age = this.age + partialTick;
        float size = this.getQuadSize(partialTick);
        if (size <= 0) return;
        var cameraPos = camera.position();
        float x = (float) (Mth.lerp(partialTick, this.xo, this.x) - cameraPos.x);
        float y = (float) (Mth.lerp(partialTick, this.yo, this.y) - cameraPos.y);
        float z = (float) (Mth.lerp(partialTick, this.zo, this.z) - cameraPos.z);
        float spinAge = this.settled ? this.settledAge : age;
        this.body.rotationXYZ(this.phase + spinAge * this.spinX, spinAge * this.spinY, spinAge * this.spinZ);
        this.body.transformInverse(this.view.set(-x, -y, -z));
        int light = this.getLightCoords(partialTick);
        float heat = this.hot ? ExplosionSequence.fireHeat(age, 8) : 0;
        for (int axis = 0; axis < 3; axis++) {
            float sign = this.view.get(axis) >= 0 ? 1 : -1;
            this.normal.set(0, 0, 0).setComponent(axis, sign);
            this.body.transform(this.normal);
            this.face.set(this.body);
            if (axis == 0) this.face.rotateY(sign * (float) Math.PI / 2);
            else if (axis == 1) this.face.rotateX(-sign * (float) Math.PI / 2);
            else if (sign < 0) this.face.rotateY((float) Math.PI);
            float shade = 0.6F + 0.3F * Math.max(0, this.normal.y) + 0.1F * Math.max(0, this.normal.z);
            int colour = ARGB.colorFromFloat(1, EffectLook.fireBlend(this.rCol * shade, 1, heat),
                    EffectLook.fireBlend(this.gCol * shade, 0.58F, heat), EffectLook.fireBlend(this.bCol * shade, 0.2F, heat));
            state.add(this.getLayer(), x + this.normal.x * size, y + this.normal.y * size, z + this.normal.z * size,
                    this.face.x, this.face.y, this.face.z, this.face.w, size,
                    this.getU0(), this.getU1(), this.getV0(), this.getV1(), colour, light);
        }
    }

    @Override public float getQuadSize(float partialTick) {
        return this.radius * BlastGroundLook.fragmentScale(this.age + partialTick, this.lifetime);
    }

    @Override protected int getLightCoords(float partialTick) {
        this.lightPos.set(this.x, this.y, this.z);
        long key = this.lightPos.asLong();
        if (EffectLook.refreshLight(this.age, this.lightAge, key != this.sampledBlock)) {
            this.lightAge = this.age; this.sampledBlock = key;
            this.ambient = this.level.hasChunkAt(this.lightPos)
                    ? net.minecraft.util.LightCoordsUtil.getLightCoords(this.level, this.lightPos) : 0;
        }
        return EffectLook.smokeLight(this.ambient, this.hot ? ExplosionSequence.fireHeat(this.age + partialTick, 8) : 0);
    }
}
