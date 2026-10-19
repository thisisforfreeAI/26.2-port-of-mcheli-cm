package io.github.mcheliceport.client.camera;

import io.github.mcheliceport.client.input.ModKeyMappings;
import io.github.mcheliceport.entity.aircraft.AircraftCamera;
import io.github.mcheliceport.entity.gltd.GltdEntity;
import io.github.mcheliceport.net.GltdControlPayload;
import io.github.mcheliceport.util.McheliSounds;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.TriState;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * The laser designator operator's client (upstream {@code MCH_ClientGLTDTickHandler}): the view
 * from the designator's optics, the zoom and the controls. The attack key calls in the strike, the
 * zoom key and the weapon-mode key zoom in and out, the camera-mode key switches night vision and
 * the unmount key (or sneaking) leaves.
 */
public final class GltdCameraClient {

    private static AircraftCameraClient.@Nullable ViewMarker viewEntity;
    private static boolean riding;
    private static boolean useWeaponWasDown;

    private GltdCameraClient() {
    }

    public static void reset() {
        viewEntity = null;
        riding = false;
        useWeaponWasDown = false;
    }

    /** The designator the player sits at, or null. */
    public static @Nullable GltdEntity riddenGltd() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && player.getVehicle() instanceof GltdEntity gltd ? gltd : null;
    }

    /** The view entity while the player sits at a designator, or null. */
    public static @Nullable Entity viewEntity() {
        return viewEntity != null && Minecraft.getInstance().getCameraEntity() == viewEntity ? viewEntity : null;
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        GltdEntity gltd = riddenGltd();
        if (player == null || gltd == null) {
            if (riding) {
                leave(mc, player);
            }
            return;
        }
        gltd.clampView(player);
        gltd.updateCameraPosition(true);
        if (viewEntity == null || viewEntity.level() != mc.level) {
            viewEntity = new AircraftCameraClient.ViewMarker(mc.level, player.getEyeHeight());
        }
        AircraftCameraClient.ViewMarker view = viewEntity;
        view.xo = view.getX();
        view.yo = view.getY();
        view.zo = view.getZ();
        view.setPosRaw(gltd.camera.posX, gltd.camera.posY - view.getEyeHeight(), gltd.camera.posZ);
        turnWithRider(view, player);
        if (!riding) {
            view.xo = view.getX();
            view.yo = view.getY();
            view.zo = view.getZ();
        }
        if (mc.gui.screen() == null) {
            control(player, gltd);
        }
        if (mc.getCameraEntity() != view) {
            mc.setCameraEntity(view);
        }
        AircraftCameraClient.updatePostEffect(mc, gltd.camera.getMode(0));
        riding = true;
    }

    private static void leave(Minecraft mc, @Nullable LocalPlayer player) {
        if (viewEntity != null && mc.getCameraEntity() == viewEntity) {
            mc.setCameraEntity(player);
        }
        viewEntity = null;
        riding = false;
        useWeaponWasDown = false;
        AircraftCameraClient.updatePostEffect(mc, AircraftCamera.MODE_NORMAL);
    }

    /** Upstream {@code playerControl}. */
    private static void control(LocalPlayer player, GltdEntity gltd) {
        boolean unmount = ModKeyMappings.UNMOUNT.consumeClick();
        boolean cameraKey = ModKeyMappings.CAMERA_MODE.consumeClick();
        boolean useDown = ModKeyMappings.USE_WEAPON.isDown();
        boolean usePressed = useDown && !useWeaponWasDown;
        useWeaponWasDown = useDown;
        // The designator uses none of the other vehicle keys; drop their clicks so none fire later.
        for (KeyMapping key : ModKeyMappings.ALL) {
            while (key.consumeClick()) {
                // discard
            }
        }

        boolean useWeapon = false;
        boolean switchHeld = ModKeyMappings.SWITCH_WEAPON_1.isDown() || ModKeyMappings.SWITCH_WEAPON_2.isDown();
        if (!switchHeld && useDown) {
            if (gltd.useCurrentWeapon()) {
                useWeapon = true;
            } else if (usePressed) {
                play("ng", 1.0F, 1.0F);
            }
        }

        float prevZoom = gltd.camera.getCameraZoom();
        boolean zoomIn = ModKeyMappings.ZOOM.isDown();
        boolean zoomOut = ModKeyMappings.SWITCH_WEAPON_MODE.isDown();
        if (zoomIn && !zoomOut) {
            gltd.zoomCamera(0.1F * prevZoom);
        } else if (zoomOut && !zoomIn) {
            gltd.zoomCamera(-0.1F * prevZoom);
        }
        float zoom = gltd.camera.getCameraZoom();
        if (zoom != prevZoom) {
            play("zoom", 0.1F, prevZoom < zoom ? 1.0F : 0.85F);
        }

        byte cameraMode = -1;
        if (cameraKey) {
            int before = gltd.camera.getMode(0);
            gltd.camera.setMode(0, before + 1);
            int after = gltd.camera.getMode(0);
            if (after != before) {
                cameraMode = (byte) after;
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            }
        }
        if (unmount || useWeapon || cameraMode >= 0) {
            ClientPacketDistributor.sendToServer(new GltdControlPayload(cameraMode, useWeapon, unmount));
        }
    }

    private static void play(String name, float volume, float pitch) {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(McheliSounds.event(name).value(), pitch, volume));
    }

    private static void turnWithRider(Entity view, Entity rider) {
        view.setYRot(rider.getYRot());
        view.setXRot(rider.getXRot());
        view.yRotO = rider.yRotO;
        view.xRotO = rider.xRotO;
    }

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

    /** The view narrows by the zoom. */
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        Entity view = viewEntity();
        GltdEntity gltd = riddenGltd();
        if (view != null && gltd != null && event.getCamera().entity() == view) {
            event.setFOV(event.getFOV() / gltd.camera.getCameraZoom());
        }
    }

    /** Upstream {@code disableFirstPersonItemRender}: no hand at the designator. */
    public static void onRenderHand(RenderHandEvent event) {
        if (riddenGltd() != null) {
            event.setCanceled(true);
        }
    }

    /** The attack and use keys do nothing else at the designator. */
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if ((event.isAttack() || event.isUseItem()) && riddenGltd() != null) {
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }

    public static void onNameTag(RenderNameTagEvent.CanRender event) {
        if (viewEntity() != null && event.getEntity() == Minecraft.getInstance().player) {
            event.setCanRender(TriState.FALSE);
        }
    }
}
