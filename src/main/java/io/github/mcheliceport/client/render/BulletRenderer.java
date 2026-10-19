package io.github.mcheliceport.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.mcheliceport.client.model.BulletModels;
import io.github.mcheliceport.client.model.McheliModel;
import io.github.mcheliceport.config.EffectQuality;
import io.github.mcheliceport.client.particle.EffectPerformance;
import io.github.mcheliceport.content.weapon.ArgbColor;
import io.github.mcheliceport.content.weapon.WeaponInfo;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.weapon.BaseBulletEntity;
import io.github.mcheliceport.entity.weapon.BombEntity;
import io.github.mcheliceport.entity.weapon.BulletEntity;
import io.github.mcheliceport.net.ModNetwork;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Draws projectiles with their bullet models, tinted with the definition's {@code Color} (its
 * {@code ColorInWater} near water), and missiles with their exhaust flame. Ported from upstream
 * {@code MCH_RenderBulletBase} and its subclasses; {@link Kind} picks which subclass's orientation
 * and extras apply.
 */
public class BulletRenderer<T extends BaseBulletEntity> extends EntityRenderer<T, BulletRenderer.State> {

    public static final Identifier TEX_FLAME = Identifier.fromNamespaceAndPath("mcheli", "textures/exhaust_flame.png");
    public static final Identifier TEX_FLAME_1 =
            Identifier.fromNamespaceAndPath("mcheli", "textures/exhaust_flame_1.png");

    /** Upstream's renderer classes. */
    public enum Kind {
        /** {@code MCH_RenderBullet}: bullets, rockets, torpedoes, marker rockets; interpolated rotation. */
        BULLET,
        /** {@code MCH_RenderAAMissile}: turned to its velocity, with flame. */
        AA_MISSILE,
        /** {@code MCH_RenderASMissile}: with flame. */
        AS_MISSILE,
        /** {@code MCH_RenderTvMissile} (TV and AT missiles): hidden from its own operator's camera, with flame. */
        TV_MISSILE,
        /** {@code MCH_RenderBomb}: a cluster bomb's carrier is hidden once it releases its bomblets at once. */
        BOMB
    }

    /** What one frame draws of a projectile. */
    public static class State extends EntityRenderState {
        public @Nullable List<McheliModel.Group> groups;
        public @Nullable Identifier texture;
        public int color;
        public float yaw;
        public float pitch;
        public boolean flame;
        public double flameX;
        public double flameY;
        public double flameZ;
        public Identifier flameTexture = TEX_FLAME;
        /** M8: a sparse tracer round with a compact luminous streak. */
        public boolean tracer;
        /** The round's motion this frame, blocks per tick, for the tracer's streak. */
        public double motionX;
        public double motionY;
        public double motionZ;
        /** M8: a glow at the missile's flame. */
        public boolean flameGlow;
        public float tracerAlpha;
    }

    private final Kind kind;

    public BulletRenderer(EntityRendererProvider.Context context, Kind kind) {
        super(context);
        this.kind = kind;
        this.shadowRadius = kind == Kind.BULLET || kind == Kind.BOMB ? 0.0F : 0.5F;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(T entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.groups = null;
        state.texture = null;
        state.flame = false;
        state.tracer = false;
        state.flameGlow = false;
        WeaponInfo info = entity.getInfo();
        if (info == null || !this.isVisible(entity, info)) {
            return;
        }
        String name = entity.getBulletModelName();
        McheliModel model = name != null ? BulletModels.bullet(name) : null;
        if (model == null) {
            return;
        }
        state.groups = model.groups;
        state.texture = Identifier.fromNamespaceAndPath("mcheli",
                "textures/bullets/" + name.toLowerCase(Locale.ROOT) + ".png");
        state.color = colorOf(entity, info).toARGB();
        double mx = entity.prevMotionX + (entity.getDeltaMovement().x - entity.prevMotionX) * partialTicks;
        double my = entity.prevMotionY + (entity.getDeltaMovement().y - entity.prevMotionY) * partialTicks;
        double mz = entity.prevMotionZ + (entity.getDeltaMovement().z - entity.prevMotionZ) * partialTicks;
        boolean enhanced = EffectQuality.enhanced();
        // A stable sparse visual belt; ordinary rounds retain ambient-lit models. No synced/gameplay state.
        state.tracer = enhanced && this.kind == Kind.BULLET && entity instanceof BulletEntity
                && TracerLook.selected(entity.getUUID().getLeastSignificantBits());
        state.tracerAlpha = TracerLook.alpha(entity.level().getSkyDarken(), state.color);
        state.motionX = mx;
        state.motionY = my;
        state.motionZ = mz;
        switch (this.kind) {
            case BULLET -> {
                state.yaw = Mth.rotLerp(partialTicks, entity.yRotO, entity.getYRot());
                state.pitch = Mth.lerp(partialTicks, entity.xRotO, entity.getXRot());
            }
            case AA_MISSILE -> {
                // Upstream getYawPitchFromVec, then rotate(yaw - 90, 0, -1, 0) and rotate(pitch, -1, 0, 0).
                double p = Math.sqrt(mx * mx + mz * mz);
                float vyaw = (float) Math.toDegrees(Math.atan2(mz, mx));
                float vpitch = (float) Math.toDegrees(Math.atan2(my, p));
                state.yaw = vyaw - 90.0F;
                state.pitch = -vpitch;
            }
            default -> {
                state.yaw = entity.getYRot();
                state.pitch = entity.getXRot();
            }
        }
        if (this.kind == Kind.AA_MISSILE || this.kind == Kind.AS_MISSILE || this.kind == Kind.TV_MISSILE) {
            // Upstream renderMissileFlame(entity, ..., 0.8, 4.0): a camera-facing quad behind the missile.
            double len = Math.sqrt(mx * mx + my * my + mz * mz);
            state.flame = true;
            state.flameX = len > 1.0E-4 ? -mx / len * 0.8 : 0.0;
            state.flameY = len > 1.0E-4 ? -my / len * 0.8 : 0.0;
            state.flameZ = len > 1.0E-4 ? -mz / len * 0.8 : 0.0;
            state.flameTexture = entity.tickCount / 5 % 2 == 0 ? TEX_FLAME : TEX_FLAME_1;
            state.flameGlow = enhanced && EffectPerformance.optionalGlows();
        }
    }

    private boolean isVisible(T entity, WeaponInfo info) {
        if (this.kind == Kind.BOMB && entity instanceof BombEntity
                && !(entity.isBomblet > 0 || info.bomblet <= 0 || info.bombletSTime > 0)) {
            return false;
        }
        if (this.kind == Kind.TV_MISSILE) {
            // Upstream isRenderBullet: the operator watching through this missile does not see it.
            var player = Minecraft.getInstance().player;
            AircraftEntity ac = player != null ? ModNetwork.riddenAircraft(player) : null;
            if (ac != null && ac.isCameraView(player) && ac.getTVMissile() == entity
                    && entity.shootingEntity == player) {
                return false;
            }
        }
        return true;
    }

    /** Upstream: {@code ColorInWater} when water is at, above or below the projectile. */
    private static ArgbColor colorOf(BaseBulletEntity entity, WeaponInfo info) {
        BlockPos base = BlockPos.containing(entity.getX(), entity.getY(), entity.getZ());
        for (int offset = 0; offset < 3; offset++) {
            if (entity.level().getFluidState(base.above(1 - offset)).is(FluidTags.WATER)) {
                return info.colorInWater;
            }
        }
        return info.color;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.groups != null && state.texture != null) {
            List<McheliModel.Group> groups = state.groups;
            int color = state.color;
            // Only the small tracer streak emits; the projectile body uses actual ambient light.
            int light = state.lightCoords;
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(-state.yaw));
            poseStack.mulPose(Axis.XP.rotationDegrees(state.pitch));
            collector.submitCustomGeometry(poseStack, AircraftRenderTypes.aircraft(state.texture),
                    (pose, buffer) -> ModelVertices.emit(buffer, pose, groups, color, light, false));
            poseStack.popPose();
        }
        if (state.tracer) {
            submitTracerGlow(state, poseStack, collector, camera);
        }
        if (state.flameGlow) {
            poseStack.pushPose();
            poseStack.translate(state.flameX, state.flameY, state.flameZ);
            GlowGeometry.disc(poseStack, collector, camera, 1.6F, 0xFFA850, 0.55F);
            poseStack.popPose();
        }
        if (state.flame) {
            submitFlame(poseStack, collector, camera, state.flameX, state.flameY, state.flameZ, state.flameTexture,
                    4.0F);
        }
        super.submit(state, poseStack, collector, camera);
    }

    /**
     * A thin fraction-of-a-tick exposure behind selected rounds, with no spherical halo.
     * Keep explicit weapon colours; neutral definitions get a warm tracer rather than white bloom.
     */
    private static void submitTracerGlow(State state, PoseStack poseStack, SubmitNodeCollector collector,
            CameraRenderState camera) {
        Vec3 motion = new Vec3(state.motionX, state.motionY, state.motionZ);
        double speed = motion.length();
        if (!Double.isFinite(speed) || speed < 1.0E-3) {
            return;
        }
        Vec3 tail = motion.scale(-TracerLook.length(speed) / speed);
        Vec3 toCamera = camera.pos.subtract(state.x, state.y, state.z);
        double distance = toCamera.length();
        if (!Double.isFinite(distance)) return;
        double rearCosine = distance > 1.0E-4 ? -motion.dot(toCamera) / (speed * distance) : 1;
        float alpha = state.tracerAlpha * TracerLook.viewFactor(rearCosine);
        GlowGeometry.tracer(poseStack, collector, camera, tail, toCamera, TracerLook.width(distance),
                TracerLook.color(state.color), alpha);
    }

    /** Upstream {@code renderMissileFlame}: a full-bright quad of {@code size} facing the camera. */
    static void submitFlame(PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, double x,
            double y, double z, Identifier texture, float size) {
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.mulPose(camera.orientation);
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucentEmissive(texture), (pose, buffer) -> {
            float[][] corners = {{-size, -size, 0, 1}, {size, -size, 1, 1}, {size, size, 1, 0}, {-size, size, 0, 0}};
            for (float[] c : corners) {
                buffer.addVertex(pose, c[0], c[1], 0.0F)
                        .setColor(0xFFFFFFFF)
                        .setUv(c[2], c[3])
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(LightCoordsUtil.FULL_BRIGHT)
                        .setNormal(pose, 0.0F, 1.0F, 0.0F);
            }
        });
        poseStack.popPose();
    }
}
