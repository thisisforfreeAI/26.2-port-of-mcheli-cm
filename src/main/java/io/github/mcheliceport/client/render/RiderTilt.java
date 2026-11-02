package io.github.mcheliceport.client.render;

import io.github.mcheliceport.platform.loader.RenderData;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.config.ClientConfig;
import io.github.mcheliceport.client.camera.AircraftCameraClient;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.seat.SeatEntity;
import io.github.mcheliceport.util.McheliMath;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * Draws the crew of a vehicle tilted with its pitch and roll and scaled by its
 * {@code EntityWidth}/{@code EntityHeight} (upstream {@code renderEntitySimple}, config
 * {@code EnableModEntityRender}): a render state modifier records the tilt, and the living
 * renderer's pre and post events wrap the rider's drawing in it.
 */
public final class RiderTilt {

    /** The transform about the rider's origin, in upstream's call order. */
    public record Tilt(float yaw, float pitch, float roll, float width, float height) {
        public void apply(PoseStack poseStack) {
            GlMatrixStack gl = new GlMatrixStack();
            gl.rotate(this.yaw, 0.0F, -1.0F, 0.0F);
            gl.rotate(this.pitch, 1.0F, 0.0F, 0.0F);
            gl.rotate(this.roll, 0.0F, 0.0F, 1.0F);
            gl.scale(this.width, this.height, this.width);
            gl.rotate(-this.yaw, 0.0F, -1.0F, 0.0F);
            poseStack.mulPose(gl.current());
        }
    }

    public static final RenderData.Key<Tilt> TILT =
            RenderData.key(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "rider_tilt"));
    private static final RenderData.Key<Boolean> HIDDEN =
            RenderData.key(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "rider_hidden"));

    private RiderTilt() {
    }

    public static void registerModifiers() {
        io.github.mcheliceport.platform.ClientEvents.LIVING_RENDER_STATE.add((LivingEntity entity, LivingEntityRenderState state, float partialTick) -> {
            boolean hidden = hidden(entity);
            RenderData.set(state, HIDDEN, hidden);
            RenderData.set(state, TILT, hidden ? null : tilt(entity, partialTick));
        });
    }

    /** After every other pre listener (NeoForge's lowest priority): {@code ThermalVision} goes first. */
    public static void registerRenderHooks() {
        io.github.mcheliceport.platform.ClientEvents.RENDER_LIVING_PRE.add(RiderTilt::onPre);
        io.github.mcheliceport.platform.ClientEvents.RENDER_LIVING_POST.add(RiderTilt::onPost);
    }

    private static boolean hidden(Entity rider) {
        Entity vehicle = rider.getVehicle();
        AircraftEntity aircraft = vehicle instanceof AircraftEntity a ? a
                : vehicle instanceof SeatEntity seat ? seat.getParent() : null;
        if (aircraft == null || aircraft.getAcInfo() == null) { return false; }
        if (aircraft.getAcInfo().hideEntity && aircraft.isPilot(rider)) { return true; }
        Minecraft mc = Minecraft.getInstance();
        // A marker camera makes vanilla treat the local rider as a third-person entity.
        // Upstream's renderEntitySimple skips that rider in the primary first-person view.
        return rider == mc.player && mc.options.getCameraType().isFirstPerson()
                && AircraftCameraClient.viewEntity() != null && aircraft.getCameraId() == 0;
    }

    /** The tilt of a rider of a vehicle, or null. */
    static @Nullable Tilt tilt(Entity rider, float partialTick) {
        if (!ClientConfig.get(ClientConfig.TILT_RIDERS)) {
            return null;
        }
        Entity vehicle = rider.getVehicle();
        AircraftEntity aircraft = vehicle instanceof AircraftEntity a ? a
                : vehicle instanceof SeatEntity seat ? seat.getParent() : null;
        if (aircraft == null) {
            return null;
        }
        AircraftInfo info = aircraft.getAcInfo();
        if (info == null) {
            return null;
        }
        float[] ypr = McheliMath.slerpOrientationYPR(aircraft.yRotO, aircraft.xRotO, aircraft.prevRoll,
                aircraft.getYaw(), aircraft.getPitch(), aircraft.getRoll(), partialTick);
        return new Tilt(ypr[0], ypr[1] + info.entityPitch, ypr[2] + info.entityRoll, info.entityWidth,
                info.entityHeight);
    }

    private static void onPre(io.github.mcheliceport.platform.ClientEvents.RenderLiving event) {
        if (event.isCanceled()) {
            return;
        }
        if (Boolean.TRUE.equals(RenderData.get(event.getRenderState(), HIDDEN))) {
            event.setCanceled(true);
            return;
        }
        Tilt tilt = RenderData.get(event.getRenderState(), TILT);
        if (tilt != null) {
            event.getPoseStack().pushPose();
            tilt.apply(event.getPoseStack());
        }
    }

    private static void onPost(io.github.mcheliceport.platform.ClientEvents.RenderLiving event) {
        if (RenderData.get(event.getRenderState(), TILT) != null) {
            event.getPoseStack().popPose();
        }
    }
}
