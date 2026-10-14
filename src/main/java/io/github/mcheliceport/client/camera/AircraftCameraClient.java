package io.github.mcheliceport.client.camera;

import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.client.AircraftClientEvents;
import io.github.mcheliceport.client.input.ModKeyMappings;
import io.github.mcheliceport.config.ServerSettings;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.entity.aircraft.AircraftCamera;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.util.McheliMath;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.TriState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.jspecify.annotations.Nullable;

/**
 * The client side of a vehicle's camera: the view from the camera position, the zoom, the
 * night-vision post effect and the third-person distance. Upstream spread this over
 * {@code MCH_ViewEntityDummy}, {@code MCH_ClientHeliTickHandler#onTick}, {@code MCH_CameraManager}
 * and {@code KeyboardInputHandler#handleCameraDistance}.
 *
 * <p>As upstream, the view comes from the camera in gunner mode, and for a pilot whose vehicle has
 * {@code AlwaysCameraView}; it is drawn from a client-only {@link Marker} given to
 * {@link Minecraft#setCameraEntity}, moved to the camera each tick and turned with the rider every
 * frame. The marker keeps the rider's eye height and sits that far below the camera, so vanilla's
 * eye-height smoothing does not slide the view when it switches. Leaving the vehicle gives the
 * view back to the player.
 */
public final class AircraftCameraClient {

    public static final Identifier NIGHT_VISION_EFFECT =
            Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "nightvision");
    private static final float DEFAULT_THIRD_PERSON_DISTANCE = 4.0F;
    private static final int CAM_DIST_STEP = 4;
    private static final int CAM_DIST_MIN = 4;
    private static final int CAM_DIST_MAX = 72;

    private static @Nullable ViewMarker viewEntity;
    private static boolean wasRiding;
    private static float thirdPersonDistance = DEFAULT_THIRD_PERSON_DISTANCE;

    private AircraftCameraClient() {
    }

    /** Forgets the view entity and distance when leaving a world. */
    public static void reset() {
        viewEntity = null;
        wasRiding = false;
        thirdPersonDistance = DEFAULT_THIRD_PERSON_DISTANCE;
    }

    /** The entity the view comes from in camera view, or null when the player's own view is used. */
    public static @Nullable Entity viewEntity() {
        Minecraft mc = Minecraft.getInstance();
        return viewEntity != null && mc.getCameraEntity() == viewEntity ? viewEntity : null;
    }

    public static float thirdPersonDistance() {
        return thirdPersonDistance;
    }

    /**
     * Upstream {@code setThirdPeasonCameraDistance}: kept between 4 and 60, so Page Up stops at 60
     * although its own limit is 72.
     */
    static void setThirdPersonDistance(float distance) {
        if (distance >= 0.1F) {
            thirdPersonDistance = Mth.clamp(distance, 4.0F, 60.0F);
        }
    }

    /** Upstream {@code handleCameraDistance}: Page Up and Page Down step the distance by 4. */
    static float stepThirdPersonDistance(float distance, boolean up) {
        int camDist = (int) distance;
        return up ? Math.min(camDist + CAM_DIST_STEP, CAM_DIST_MAX) : Math.max(camDist - CAM_DIST_STEP, CAM_DIST_MIN);
    }

    /**
     * Upstream's choice of view entity: the camera in gunner mode, or for a pilot whose vehicle has
     * {@code AlwaysCameraView} (upstream's inverted {@code isAlwaysCameraView}), else the player.
     */
    public static boolean usesCameraView(AircraftEntity aircraft, Entity rider) {
        AircraftInfo info = aircraft.getAcInfo();
        boolean alwaysCameraView = info != null && info.alwaysCameraView;
        return aircraft.isGunnerMode(rider) || aircraft.isPilot(rider) && alwaysCameraView;
    }

    /**
     * Upstream {@code updateCamera}: the camera sits at the content's camera position, turned with
     * the vehicle.
     */
    public static Vec3 cameraPosition(AircraftEntity aircraft, @Nullable Entity viewer) {
        AircraftInfo.CameraPosition cpi = aircraft.getCameraPosInfo(viewer);
        Vec3 cp = cpi != null ? cpi.pos() : Vec3.ZERO;
        Vec3 v = McheliMath.rotVec3(cp, -aircraft.getYaw(), -aircraft.getPitch(), -aircraft.getRoll());
        return aircraft.position().add(v);
    }

    /** Before the riding controls: the distance keys, which upstream read with or without a vehicle. */
    public static void handleKeys() {
        Minecraft mc = Minecraft.getInstance();
        boolean up = consumeAll(ModKeyMappings.CAMERA_DISTANCE_UP);
        boolean down = consumeAll(ModKeyMappings.CAMERA_DISTANCE_DOWN);
        if (mc.player == null || mc.gui.screen() != null || !ServerSettings.client().enableCamDistChange()) {
            return;
        }
        if (up || down) {
            setThirdPersonDistance(stepThirdPersonDistance(thirdPersonDistance, up));
        }
    }

    private static boolean consumeAll(KeyMapping key) {
        boolean clicked = false;
        while (key.consumeClick()) {
            clicked = true;
        }
        return clicked;
    }

    /** After the riding controls each client tick: moves the camera and picks the view entity. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        AircraftEntity aircraft = AircraftClientEvents.riddenAircraft(player);
        boolean riding = player != null && aircraft instanceof HeliEntity && aircraft.getAcInfo() != null;
        if (!riding) {
            if (wasRiding) {
                leave(mc, player);
            }
            wasRiding = false;
            return;
        }
        if (!wasRiding) {
            setThirdPersonDistance(aircraft.getAcInfo().thirdPersonDist);
        }
        wasRiding = true;

        Vec3 pos = cameraPosition(aircraft, player);
        aircraft.camera.setPosition(pos.x, pos.y, pos.z);
        ViewMarker view = viewEntity(mc, player);
        // MCH_ViewEntityDummy#update: last tick's camera is where this tick's view starts.
        view.xo = view.getX();
        view.yo = view.getY();
        view.zo = view.getZ();
        view.setPosRaw(pos.x, pos.y - view.getEyeHeight(), pos.z);
        turnWithRider(view, player);

        if (usesCameraView(aircraft, player)) {
            if (mc.getCameraEntity() != view) {
                view.xo = view.getX();
                view.yo = view.getY();
                view.zo = view.getZ();
                mc.setCameraEntity(view);
            }
        } else if (mc.getCameraEntity() == view) {
            mc.setCameraEntity(player);
        }
        updatePostEffect(mc, aircraft.getCameraMode(player) == AircraftCamera.MODE_NIGHTVISION);
    }

    private static ViewMarker viewEntity(Minecraft mc, LocalPlayer player) {
        if (viewEntity == null || viewEntity.level() != mc.level) {
            viewEntity = new ViewMarker(mc.level, player.getEyeHeight());
        }
        return viewEntity;
    }

    private static void turnWithRider(Entity view, Entity rider) {
        view.setYRot(rider.getYRot());
        view.setXRot(rider.getXRot());
        view.yRotO = rider.yRotO;
        view.xRotO = rider.xRotO;
    }

    private static void leave(Minecraft mc, @Nullable LocalPlayer player) {
        if (viewEntity != null && mc.getCameraEntity() == viewEntity) {
            mc.setCameraEntity(player);
        }
        viewEntity = null;
        thirdPersonDistance = DEFAULT_THIRD_PERSON_DISTANCE;
        updatePostEffect(mc, false);
    }

    /**
     * Upstream {@code MCH_Camera#setMode}: night vision draws through its post effect. Vanilla
     * clears post effects when the camera entity or perspective changes, so this runs every tick;
     * it only clears an effect that is its own.
     */
    private static void updatePostEffect(Minecraft mc, boolean nightVision) {
        Identifier current = mc.gameRenderer.currentPostEffect();
        if (nightVision) {
            if (!NIGHT_VISION_EFFECT.equals(current)) {
                mc.gameRenderer.setPostEffect(NIGHT_VISION_EFFECT);
            }
        } else if (NIGHT_VISION_EFFECT.equals(current)) {
            mc.gameRenderer.clearPostEffect();
        }
    }

    /** The view entity turns with the rider every frame, as upstream's render-tick hooks did. */
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Entity view = viewEntity();
        LocalPlayer player = Minecraft.getInstance().player;
        if (view == null || player == null || event.getCamera().entity() != view) {
            return;
        }
        turnWithRider(view, player);
        float partial = (float) event.getPartialTick();
        event.setYaw(player.getViewYRot(partial));
        event.setPitch(player.getViewXRot(partial));
    }

    /** Upstream {@code onFOVModifierEvent}: the camera view narrows by the zoom. */
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        Entity view = viewEntity();
        AircraftEntity aircraft = AircraftClientEvents.riddenAircraft(Minecraft.getInstance().player);
        if (view != null && aircraft != null && event.getCamera().entity() == view) {
            event.setFOV(event.getFOV() / aircraft.camera.getCameraZoom());
        }
    }

    /** Upstream's {@code CameraSetup} translation: third person sits at the vehicle's distance. */
    public static void onDetachedDistance(CalculateDetachedCameraDistanceEvent event) {
        if (AircraftClientEvents.riddenAircraft(Minecraft.getInstance().player) != null) {
            event.setDistance(thirdPersonDistance);
        }
    }

    /** The rider's own name tag would show over the cockpit seen from the camera; hide it. */
    public static void onNameTag(RenderNameTagEvent.CanRender event) {
        if (viewEntity() != null && event.getEntity() == Minecraft.getInstance().player) {
            event.setCanRender(TriState.FALSE);
        }
    }

    /** The view entity: a marker with the rider's eye height, so switching views does not slide. */
    private static final class ViewMarker extends Marker {
        private final EntityDimensions viewDimensions;

        ViewMarker(Level level, float eyeHeight) {
            super(EntityTypes.MARKER, level);
            this.viewDimensions = EntityDimensions.fixed(0.0F, 0.0F).withEyeHeight(eyeHeight);
            this.refreshDimensions();
        }

        @Override
        public EntityDimensions getDimensions(Pose pose) {
            return this.viewDimensions;
        }
    }
}
