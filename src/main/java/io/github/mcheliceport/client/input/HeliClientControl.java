package io.github.mcheliceport.client.input;

import io.github.mcheliceport.content.aircraft.SeatInfo;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.net.HeliControlPayload;
import io.github.mcheliceport.net.ModNetwork;
import io.github.mcheliceport.net.PlayerControlSwitches;
import io.github.mcheliceport.net.SeatControlPayload;
import io.github.mcheliceport.util.McheliSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The keys of a helicopter's rider, read once per client tick: upstream's
 * {@code MCH_ClientHeliTickHandler#playerControl} and {@code commonPlayerControl}, limited to the
 * M3 flight subset. The client applies mode switches at once, as upstream, and tells the server.
 */
public final class HeliClientControl {

    private static boolean wasRiding;
    /** Upstream {@code setRotLimitPitch}: the gunner's pitch range, held every frame until the next tick. */
    private static boolean limitPitch;
    private static float minPitch;
    private static float maxPitch;

    private HeliClientControl() {
    }

    public static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        limitPitch = false;
        AircraftEntity aircraft = player != null ? ModNetwork.riddenAircraft(player) : null;
        boolean riding = aircraft instanceof HeliEntity && aircraft.getAcInfo() != null;
        if (!riding) {
            if (wasRiding) {
                MouseFlight.reset();
            }
            wasRiding = false;
            return;
        }
        wasRiding = true;
        HeliEntity heli = (HeliEntity) aircraft;
        update(player, heli);
        if (mc.gui.screen() == null && !heli.isDestroyed()) {
            playerControl(player, heli, heli.isPilot(player));
        }
    }

    /**
     * Upstream {@code MCH_ClientHeliTickHandler#update}: the gunner's pitch is held in the seat's
     * range, the camera follows the rider's look, and the radar sweeps.
     */
    private static void update(LocalPlayer player, HeliEntity heli) {
        if (heli.isGunnerMode(player)) {
            SeatInfo seat = heli.getSeatInfo(heli.getSeatIdByEntity(player));
            if (seat != null) {
                limitPitch = true;
                minPitch = seat.minPitch;
                maxPitch = seat.maxPitch;
                player.setXRot(Mth.clamp(player.getXRot(), minPitch, maxPitch));
            }
        }
        heli.camera.prevRotationYaw = heli.camera.rotationYaw;
        heli.camera.prevRotationPitch = heli.camera.rotationPitch;
        heli.camera.rotationYaw = player.getYRot();
        heli.camera.rotationPitch = player.getXRot();
        if (heli.isEntityRadarMounted()) {
            heli.radar.update(heli, 5);
        }
    }

    /** Upstream {@code applyRotLimit}, after each frame: the mouse cannot take the gunner past the seat's pitch. */
    public static void applyRotLimit() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (!limitPitch || player == null) {
            return;
        }
        if (player.getXRot() < minPitch) {
            player.setXRot(minPitch);
            player.xRotO = minPitch;
        } else if (player.getXRot() > maxPitch) {
            player.setXRot(maxPitch);
            player.xRotO = maxPitch;
        }
    }

    private static void playerControl(LocalPlayer player, HeliEntity heli, boolean isPilot) {
        boolean send = false;
        HeliControlPayload.UnmountAction unmount = HeliControlPayload.UnmountAction.NONE;
        HeliControlPayload.ModeSwitch mode = HeliControlPayload.ModeSwitch.NONE;
        HeliControlPayload.HatchSwitch hatch = HeliControlPayload.HatchSwitch.NONE;
        HeliControlPayload.GearSwitch gear = HeliControlPayload.GearSwitch.NONE;
        HeliControlPayload.BladeStatus blades = HeliControlPayload.BladeStatus.NONE;
        PlayerControlSwitches switches = PlayerControlSwitches.NONE;
        byte freeLook = 0;
        byte cameraMode = 0;
        boolean freeLookHeld = ModKeyMappings.isFreeLookHeld();

        // Upstream handleCameraAndModes: free look + camera mode toggles the gunner, else the camera mode.
        if (ModKeyMappings.CAMERA_MODE.consumeClick()) {
            if (freeLookHeld) {
                switches = switches.with(PlayerControlSwitches.SWITCH_GUNNER_STATUS, true);
                send = true;
                playSoundOk();
            } else if (heli.canSwitchCameraMode(heli.getSeatIdByEntity(player))) {
                int before = heli.getCameraMode(player);
                heli.switchCameraMode(player, before + 1);
                int newMode = heli.getCameraMode(player);
                if (newMode != before) {
                    cameraMode = (byte) (newMode + 1);
                    send = true;
                    playSoundOk();
                } else {
                    playSoundNg();
                }
            } else {
                playSoundNg();
            }
        }

        // Upstream handleSeatControls: free look + GUI or extra switches seat.
        boolean guiClicked = ModKeyMappings.GUI.consumeClick();
        boolean extraClicked = ModKeyMappings.EXTRA.consumeClick();
        if (freeLookHeld && (guiClicked || extraClicked)) {
            ClientPacketDistributor.sendToServer(
                    new SeatControlPayload(guiClicked ? SeatControlPayload.Action.NEXT : SeatControlPayload.Action.PREV));
            guiClicked = false;
            extraClicked = false;
            send = true;
        }

        if (isPilot) {
            if (ModKeyMappings.UNMOUNT.consumeClick()) {
                unmount = HeliControlPayload.UnmountAction.UNMOUNT_SELF;
                send = true;
            }
            if (ModKeyMappings.GEAR_UP_DOWN.consumeClick() && heli.getAcInfo().haveLandingGear()) {
                if (heli.canFoldLandingGear()) {
                    gear = HeliControlPayload.GearSwitch.FOLD;
                } else if (heli.canUnfoldLandingGear()) {
                    gear = HeliControlPayload.GearSwitch.UNFOLD;
                }
                send = true;
            }
            if (ModKeyMappings.FREE_LOOK.consumeClick()) {
                freeLook = (byte) (heli.isFreeLookMode() ? 2 : 1);
                send = true;
            }
            if (guiClicked) {
                switches = switches.with(PlayerControlSwitches.OPEN_GUI, true);
                send = true;
            }

            boolean up = ModKeyMappings.UP.isDown();
            boolean down = ModKeyMappings.DOWN.isDown();
            boolean right = ModKeyMappings.RIGHT.isDown();
            boolean left = ModKeyMappings.LEFT.isDown();
            if (up != heli.throttleUp || down != heli.throttleDown || right != heli.moveRight
                    || left != heli.moveLeft) {
                send = true;
            }
            heli.throttleUp = up;
            heli.throttleDown = down;
            heli.moveRight = right;
            heli.moveLeft = left;
            switches = switches.with(PlayerControlSwitches.THROTTLE_UP, up)
                    .with(PlayerControlSwitches.THROTTLE_DOWN, down)
                    .with(PlayerControlSwitches.MOVE_RIGHT, right)
                    .with(PlayerControlSwitches.MOVE_LEFT, left);

            if (extraClicked) {
                if (heli.canSwitchFoldBlades()) {
                    if (heli.isFoldBlades()) {
                        heli.unfoldBlades();
                        blades = HeliControlPayload.BladeStatus.UNFOLD;
                    } else {
                        heli.foldBlades();
                        blades = HeliControlPayload.BladeStatus.FOLD;
                    }
                    send = true;
                    playSoundOk();
                } else {
                    playSoundNg();
                }
            }

            if (ModKeyMappings.SWITCH_HOVERING.consumeClick()) {
                if (heli.canSwitchHoveringMode()) {
                    mode = heli.isHoveringMode() ? HeliControlPayload.ModeSwitch.HOVERING_OFF
                            : HeliControlPayload.ModeSwitch.HOVERING_ON;
                    heli.switchHoveringMode(!heli.isHoveringMode());
                    send = true;
                } else {
                    playSoundNg();
                }
            } else if (ModKeyMappings.SWITCH_GUNNER.consumeClick()) {
                if (heli.canSwitchGunnerMode()) {
                    mode = heli.isGunnerMode() ? HeliControlPayload.ModeSwitch.GUNNER_OFF
                            : HeliControlPayload.ModeSwitch.GUNNER_ON;
                    heli.switchGunnerMode(!heli.isGunnerMode());
                    send = true;
                } else {
                    playSoundNg();
                }
            }
        } else if (ModKeyMappings.SWITCH_GUNNER.consumeClick()) {
            if (heli.canSwitchGunnerModeOtherSeat(player)) {
                heli.switchGunnerModeOtherSeat();
                send = true;
            } else {
                playSoundNg();
            }
        }

        if (ModKeyMappings.ZOOM.consumeClick()) {
            boolean uav = heli.isUAV() && !heli.getAcInfo().haveHatch();
            if (heli.isGunnerMode(player) || uav) {
                heli.zoomCamera();
                Minecraft.getInstance().getSoundManager()
                        .play(SimpleSoundInstance.forUI(McheliSounds.event("zoom").value(), 1.0F, 0.5F));
            } else if (isPilot && heli.getAcInfo().haveHatch()) {
                // Upstream's hatch key sends FOLD to open a closed hatch and UNFOLD to close it.
                if (heli.canUnfoldHatch()) {
                    hatch = HeliControlPayload.HatchSwitch.FOLD;
                    send = true;
                } else if (heli.canFoldHatch()) {
                    hatch = HeliControlPayload.HatchSwitch.UNFOLD;
                    send = true;
                } else {
                    playSoundNg();
                }
            }
        }

        // Keys of later milestones; clear their clicks so they do not pile up.
        ModKeyMappings.ALL.forEach(key -> {
            while (key.consumeClick()) {
                // Discard.
            }
        });

        if (send || player.tickCount % 100 == 0) {
            ClientPacketDistributor.sendToServer(new HeliControlPayload(heli.getId(), unmount, mode, hatch, gear,
                    switches, freeLook, blades, cameraMode));
        }
    }

    private static void playSoundOk() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private static void playSoundNg() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(McheliSounds.event("ng"), 1.0F));
    }
}
