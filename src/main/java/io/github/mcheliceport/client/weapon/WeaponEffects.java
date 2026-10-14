package io.github.mcheliceport.client.weapon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.mcheliceport.client.model.BulletModels;
import io.github.mcheliceport.client.model.McheliModel;
import io.github.mcheliceport.client.render.AircraftRenderTypes;
import io.github.mcheliceport.client.render.ModelVertices;
import io.github.mcheliceport.config.ClientConfig;
import io.github.mcheliceport.content.weapon.CartridgeInfo;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.jspecify.annotations.Nullable;

/**
 * Client-only weapon effects drawn in the world: laser beams (upstream {@code MCH_RenderLaser}) and
 * spent cartridges (upstream {@code MCH_EntityCartridge} and {@code MCH_RenderCartridge}).
 *
 * <p>Upstream's cartridges were client-side entities; here they are a plain list ticked and drawn by
 * this class, since they never touch the server. Upstream drew beams with depth testing off, so they
 * showed through walls; here they use vanilla's additive lightning type, which is depth tested.
 */
public final class WeaponEffects {

    private static final List<Beam> BEAMS = new ArrayList<>();
    private static final List<Cartridge> CARTRIDGES = new ArrayList<>();
    private static final RandomSource RAND = RandomSource.create();

    private WeaponEffects() {
    }

    public static void addBeam(Vec3 start, Vec3 end, int argb, float width, int lifeTicks, boolean pulsate,
            double renderStartDist) {
        BEAMS.add(new Beam(start, end, argb, width, pulsate, lifeTicks, renderStartDist));
    }

    /** Upstream {@code MCH_EntityCartridge#spawnCartridge}. */
    public static void spawnCartridge(Level level, CartridgeInfo info, double x, double y, double z, double mx,
            double my, double mz, float yaw, float pitch) {
        Cartridge c = new Cartridge(level, info, x, y, z, mx + (RAND.nextFloat() - 0.5) * 0.07, my,
                mz + (RAND.nextFloat() - 0.5) * 0.07);
        c.yaw = c.prevYaw = c.targetYaw = yaw;
        c.pitch = c.prevPitch = c.targetPitch = pitch;
        float cy = (yaw + info.yaw) * Mth.DEG_TO_RAD;
        float cp = (pitch + info.pitch) * Mth.DEG_TO_RAD;
        double tX = -Mth.sin(cy) * Mth.cos(cp);
        double tZ = Mth.cos(cy) * Mth.cos(cp);
        double tY = -Mth.sin(cp);
        double d = Math.sqrt(tX * tX + tY * tY + tZ * tZ);
        if (Math.abs(d) > 0.001) {
            c.mx += tX * info.acceleration / d;
            c.my += tY * info.acceleration / d;
            c.mz += tZ * info.acceleration / d;
        }
        CARTRIDGES.add(c);
    }

    public static void clear() {
        BEAMS.clear();
        CARTRIDGES.clear();
    }

    /** Upstream {@code MCH_RenderLaser#onClientTick} and {@code MCH_EntityCartridge#onUpdate}. */
    static void tick(@Nullable Level level) {
        BEAMS.removeIf(beam -> --beam.lifeTicks <= 0);
        int aliveTime = ClientConfig.get(ClientConfig.ALIVE_TIME_OF_CARTRIDGE);
        for (Iterator<Cartridge> it = CARTRIDGES.iterator(); it.hasNext(); ) {
            Cartridge c = it.next();
            if (c.level != level || c.age >= aliveTime) {
                it.remove();
                continue;
            }
            c.tick();
        }
    }

    /** Draws beams and cartridges; registered on the game bus. */
    public static void onSubmitGeometry(SubmitCustomGeometryEvent event) {
        if (BEAMS.isEmpty() && CARTRIDGES.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        CameraRenderState camera = event.getLevelRenderState().cameraRenderState;
        float partialTicks = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        PoseStack poseStack = event.getPoseStack();
        SubmitNodeCollector collector = event.getSubmitNodeCollector();
        Vec3 cam = camera.pos;
        if (!BEAMS.isEmpty()) {
            Vec3 look = Vec3.directionFromRotation(camera.xRot, camera.yRot);
            long worldTime = mc.level.getGameTime();
            List<Beam> beams = List.copyOf(BEAMS);
            poseStack.pushPose();
            poseStack.translate(-cam.x, -cam.y, -cam.z);
            collector.submitCustomGeometry(poseStack, RenderTypes.lightning(), (pose, buffer) -> {
                for (Beam beam : beams) {
                    renderBeam(beam, pose, buffer, look, worldTime, partialTicks);
                }
            });
            poseStack.popPose();
        }
        for (Cartridge c : CARTRIDGES) {
            submitCartridge(c, poseStack, collector, cam, partialTicks);
        }
    }

    /** Upstream {@code MCH_RenderLaser#renderBeam}: a wide faint glow and a bright core, facing the view. */
    private static void renderBeam(Beam beam, PoseStack.Pose pose, VertexConsumer buffer, Vec3 look, long worldTime,
            float partialTicks) {
        double sx = beam.start.x;
        double sy = beam.start.y;
        double sz = beam.start.z;
        double ex = beam.end.x;
        double ey = beam.end.y;
        double ez = beam.end.z;
        double dx = ex - sx;
        double dy = ey - sy;
        double dz = ez - sz;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0E-8) {
            return;
        }
        if (beam.renderStartDist > 0.0) {
            double ratio = Math.min(beam.renderStartDist, length) / length;
            sx += dx * ratio;
            sy += dy * ratio;
            sz += dz * ratio;
            dx = ex - sx;
            dy = ey - sy;
            dz = ez - sz;
            length = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (length < 1.0E-8) {
                return;
            }
        }
        float width = beam.width;
        if (beam.pulsate) {
            width *= (float) (0.7 + 0.3 * Math.sin((worldTime + partialTicks) * 0.4));
        }
        double dirX = dx / length;
        double dirY = dy / length;
        double dirZ = dz / length;
        double crossX = dirY * look.z - dirZ * look.y;
        double crossY = dirZ * look.x - dirX * look.z;
        double crossZ = dirX * look.y - dirY * look.x;
        double crossLen = Math.sqrt(crossX * crossX + crossY * crossY + crossZ * crossZ);
        if (crossLen < 1.0E-8) {
            crossX = -dirZ;
            crossY = 0.0;
            crossZ = dirX;
            crossLen = Math.sqrt(crossX * crossX + crossZ * crossZ);
            if (crossLen < 1.0E-8) {
                return;
            }
        }
        double half = width * 0.5 / crossLen;
        double hwX = crossX * half;
        double hwY = crossY * half;
        double hwZ = crossZ * half;
        int r = beam.argb >> 16 & 0xFF;
        int g = beam.argb >> 8 & 0xFF;
        int b = beam.argb & 0xFF;
        int a = beam.argb >>> 24;
        int glow = Math.min((int) (a * 0.4F), 255) << 24 | Math.min((int) (r * 1.5F), 255) << 16
                | Math.min((int) (g * 1.5F), 255) << 8 | Math.min((int) (b * 1.5F), 255);
        quad(pose, buffer, sx, sy, sz, ex, ey, ez, hwX * 2.0, hwY * 2.0, hwZ * 2.0, glow);
        quad(pose, buffer, sx, sy, sz, ex, ey, ez, hwX, hwY, hwZ, beam.argb);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer buffer, double sx, double sy, double sz, double ex,
            double ey, double ez, double wx, double wy, double wz, int argb) {
        buffer.addVertex(pose, (float) (sx - wx), (float) (sy - wy), (float) (sz - wz)).setColor(argb);
        buffer.addVertex(pose, (float) (sx + wx), (float) (sy + wy), (float) (sz + wz)).setColor(argb);
        buffer.addVertex(pose, (float) (ex + wx), (float) (ey + wy), (float) (ez + wz)).setColor(argb);
        buffer.addVertex(pose, (float) (ex - wx), (float) (ey - wy), (float) (ez - wz)).setColor(argb);
    }

    /** Upstream {@code MCH_RenderCartridge#doRender}. */
    private static void submitCartridge(Cartridge c, PoseStack poseStack, SubmitNodeCollector collector, Vec3 cam,
            float partialTicks) {
        McheliModel model = BulletModels.bullet(c.name);
        if (model == null || c.name.isEmpty()) {
            return;
        }
        double x = Mth.lerp(partialTicks, c.prevX, c.x);
        double y = Mth.lerp(partialTicks, c.prevY, c.y);
        double z = Mth.lerp(partialTicks, c.prevZ, c.z);
        float yaw = Mth.rotLerp(partialTicks, c.prevYaw, c.yaw);
        float pitch = Mth.lerp(partialTicks, c.prevPitch, c.pitch);
        BlockPos pos = BlockPos.containing(x, y, z);
        int light = LightCoordsUtil.pack(c.level.getBrightness(LightLayer.BLOCK, pos),
                c.level.getBrightness(LightLayer.SKY, pos));
        Identifier texture = Identifier.fromNamespaceAndPath("mcheli",
                "textures/bullets/" + c.name.toLowerCase(Locale.ROOT) + ".png");
        List<McheliModel.Group> groups = model.groups;
        poseStack.pushPose();
        poseStack.translate(x - cam.x, y - cam.y, z - cam.z);
        poseStack.scale(c.scale, c.scale, c.scale);
        poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
        collector.submitCustomGeometry(poseStack, AircraftRenderTypes.aircraft(texture),
                (pose, buffer) -> ModelVertices.emit(buffer, pose, groups, 0xFFFFFFFF, light, false));
        poseStack.popPose();
    }

    private static final class Beam {
        final Vec3 start;
        final Vec3 end;
        final int argb;
        final float width;
        final boolean pulsate;
        final double renderStartDist;
        int lifeTicks;

        Beam(Vec3 start, Vec3 end, int argb, float width, boolean pulsate, int lifeTicks, double renderStartDist) {
            this.start = start;
            this.end = end;
            this.argb = argb;
            this.width = width;
            this.pulsate = pulsate;
            this.lifeTicks = lifeTicks;
            this.renderStartDist = renderStartDist;
        }
    }

    /** Upstream {@code MCH_EntityCartridge}: a spent case that falls, bounces and tumbles. */
    private static final class Cartridge {
        final Level level;
        final String name;
        final float bound;
        final float gravity;
        final float scale;
        double x;
        double y;
        double z;
        double prevX;
        double prevY;
        double prevZ;
        double mx;
        double my;
        double mz;
        float yaw;
        float pitch;
        float prevYaw;
        float prevPitch;
        float targetYaw;
        float targetPitch;
        int age;

        Cartridge(Level level, CartridgeInfo info, double x, double y, double z, double mx, double my, double mz) {
            this.level = level;
            this.name = info.name;
            this.bound = info.bound;
            this.gravity = info.gravity;
            this.scale = info.scale;
            this.x = this.prevX = x;
            this.y = this.prevY = y;
            this.z = this.prevZ = z;
            this.mx = mx;
            this.my = my;
            this.mz = mz;
        }

        void tick() {
            this.prevX = this.x;
            this.prevY = this.y;
            this.prevZ = this.z;
            this.prevYaw = this.yaw;
            this.prevPitch = this.pitch;
            this.age++;
            this.mx *= 0.98;
            this.mz *= 0.98;
            this.my += this.gravity;
            this.move();
        }

        private void rotation() {
            if (this.yaw < this.targetYaw - 3.0F) {
                this.yaw = Math.min(this.yaw + 10.0F, this.targetYaw);
            } else if (this.yaw > this.targetYaw + 3.0F) {
                this.yaw = Math.max(this.yaw - 10.0F, this.targetYaw);
            }
            if (this.pitch < this.targetPitch) {
                this.pitch = Math.min(this.pitch + 10.0F, this.targetPitch);
            } else if (this.pitch > this.targetPitch) {
                this.pitch = Math.max(this.pitch - 10.0F, this.targetPitch);
            }
        }

        private void move() {
            Vec3 from = new Vec3(this.x, this.y, this.z);
            Vec3 to = new Vec3(this.x + this.mx, this.y + this.my, this.z + this.mz);
            BlockHitResult m = this.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
            double d = Math.max(Math.max(Math.abs(this.mx), Math.abs(this.my)), Math.abs(this.mz));
            if (m.getType() != HitResult.Type.BLOCK) {
                this.x += this.mx;
                this.y += this.my;
                this.z += this.mz;
                if (d > 0.05F) {
                    this.rotation();
                }
                return;
            }
            Vec3 hit = m.getLocation();
            this.x = hit.x;
            this.y = hit.y;
            this.z = hit.z;
            this.mx += d * (RAND.nextFloat() - 0.5F) * 0.1F;
            this.my += d * (RAND.nextFloat() - 0.5F) * 0.1F;
            this.mz += d * (RAND.nextFloat() - 0.5F) * 0.1F;
            if (d > 0.1F) {
                this.targetYaw += (float) (d * (RAND.nextFloat() - 0.5F) * 720.0);
                this.targetPitch = (float) (d * (RAND.nextFloat() - 0.5F) * 720.0);
            } else {
                this.targetPitch = 0.0F;
            }
            switch (m.getDirection()) {
                case DOWN -> {
                    if (this.my > 0.0) {
                        this.my = -this.my * this.bound;
                    }
                }
                case UP -> {
                    if (this.my < 0.0) {
                        this.my = -this.my * this.bound;
                    }
                    this.targetPitch *= 0.3F;
                }
                case NORTH -> {
                    if (this.mz > 0.0) {
                        this.mz = -this.mz * this.bound;
                    } else {
                        this.z += this.mz;
                    }
                }
                case SOUTH -> {
                    if (this.mz < 0.0) {
                        this.mz = -this.mz * this.bound;
                    } else {
                        this.z += this.mz;
                    }
                }
                case WEST -> {
                    if (this.mx > 0.0) {
                        this.mx = -this.mx * this.bound;
                    } else {
                        this.x += this.mx;
                    }
                }
                case EAST -> {
                    if (this.mx < 0.0) {
                        this.mx = -this.mx * this.bound;
                    } else {
                        this.x += this.mx;
                    }
                }
            }
        }
    }
}
