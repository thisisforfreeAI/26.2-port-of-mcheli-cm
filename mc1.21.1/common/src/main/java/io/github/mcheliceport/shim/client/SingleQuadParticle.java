package io.github.mcheliceport.shim.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Optional;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.ParticleGroup;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * 26.2's {@code SingleQuadParticle} on 1.21.1's {@link TextureSheetParticle}: the particle is
 * built with its sprite, picks a {@link Layer}, and draws by {@link #extract}.
 */
public abstract class SingleQuadParticle extends TextureSheetParticle {

    protected SingleQuadParticle(ClientLevel level, double x, double y, double z, TextureAtlasSprite sprite) {
        super(level, x, y, z);
        this.setSprite(sprite);
    }

    protected SingleQuadParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd,
            TextureAtlasSprite sprite) {
        super(level, x, y, z, xd, yd, zd);
        this.setSprite(sprite);
    }

    /** Where the particle draws: blending and sheet. */
    public record Layer(ParticleRenderType type) {
        public static final Layer TRANSLUCENT = new Layer(ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT);
        public static final Layer OPAQUE = new Layer(ParticleRenderType.PARTICLE_SHEET_OPAQUE);
        public static final Layer TERRAIN = new Layer(ParticleRenderType.TERRAIN_SHEET);
    }

    protected Layer getLayer() {
        return Layer.TRANSLUCENT;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return this.getLayer().type();
    }

    /** 26.2's particle cap; 1.21.1 calls it a particle group. */
    public Optional<ParticleGroup> getParticleLimit() {
        return Optional.empty();
    }

    @Override
    public Optional<ParticleGroup> getParticleGroup() {
        return this.getParticleLimit();
    }

    /** Draws the particle; by default the camera-facing quad. */
    public void extract(QuadParticleRenderState state, Camera camera, float partialTick) {
        Quaternionf rotation = new Quaternionf();
        this.getFacingCameraMode().setRotation(rotation, camera, partialTick);
        if (this.roll != 0.0F) {
            rotation.rotateZ(Mth.lerp(partialTick, this.oRoll, this.roll));
        }
        this.extractRotatedQuad(state, camera, rotation, partialTick);
    }

    /** The state this particle's quads go through; 26.2's custom groups shape their quads there. */
    protected QuadParticleRenderState createRenderState(VertexConsumer buffer) {
        return new QuadParticleRenderState(buffer);
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTick) {
        this.extract(this.createRenderState(buffer), camera, partialTick);
    }

    protected void extractRotatedQuad(QuadParticleRenderState state, Camera camera, Quaternionf rotation,
            float partialTick) {
        Vec3 cam = camera.getPosition();
        float x = (float) (Mth.lerp(partialTick, this.xo, this.x) - cam.x());
        float y = (float) (Mth.lerp(partialTick, this.yo, this.y) - cam.y());
        float z = (float) (Mth.lerp(partialTick, this.zo, this.z) - cam.z());
        this.extractRotatedQuad(state, rotation, x, y, z, partialTick);
    }

    protected void extractRotatedQuad(QuadParticleRenderState state, Quaternionf rotation, float x, float y, float z,
            float partialTick) {
        state.add(this.getLayer(), x, y, z, rotation.x, rotation.y, rotation.z, rotation.w,
                this.getQuadSize(partialTick), this.getU0(), this.getU1(), this.getV0(), this.getV1(),
                ARGB.colorFromFloat(this.alpha, this.rCol, this.gCol, this.bCol), this.getLightColor(partialTick));
    }

    /** The sprite this particle draws; 26.2 exposes it to subclasses as {@code sprite}. */
    protected TextureAtlasSprite sprite() {
        return this.sprite;
    }
}
