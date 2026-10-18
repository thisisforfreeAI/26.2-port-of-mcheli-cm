package io.github.mcheliceport.client.input;

import io.github.mcheliceport.content.aircraft.SeatInfo;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.entity.aircraft.WingedAircraftEntity;
import io.github.mcheliceport.net.PlaneControlPayload;
import io.github.mcheliceport.net.ShipControlPayload;
import io.github.mcheliceport.entity.ship.ShipEntity;
import io.github.mcheliceport.entity.ground.GroundVehicleEntity;
import io.github.mcheliceport.entity.tank.TankEntity;
import io.github.mcheliceport.entity.vehicle.VehicleEntity;
import io.github.mcheliceport.net.GroundControlPayload;
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
 * Shared helicopter, plane and ship rider controls, read once per client tick: upstream's
 * {@code MCH_ClientHeliTickHandler#playerControl} and {@code commonPlayerControl}, limited to the
 * flight controls. Helicopter blades and plane nozzles/wings keep their type-specific switches.
 */
public final class AircraftClientControl {

    private static boolean wasRiding;
    /** Upstream {@code setRotLimitPitch}: the gunner's pitch range, held every frame until the next tick. */
    private static boolean limitPitch;
    private static float minPitch;
    private static float maxPitch;

    private AircraftClientControl() {
    }

    /** Vanilla handles Advancements before our post-tick controls; reserve a shared key for lamps while riding. */
    public static void onKey(net.neoforged.neoforge.client.event.InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        AircraftEntity aircraft = mc.player != null ? ModNetwork.riddenAircraft(mc.player) : null;
        if (mc.gui.screen() == null && aircraft != null && aircraft.canSwitchSearchLight(mc.player)
                && ModKeyMappings.SEARCH_LIGHT.matches(event.getKeyEvent())
                && mc.options.keyAdvancements.matches(event.getKeyEvent())) {
            while (mc.options.keyAdvancements.consumeClick()) { /* Handled by the vehicle light switch. */ }
        }
    }

    public static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        limitPitch = false;
        AircraftEntity aircraft = player != null ? ModNetwork.riddenAircraft(player) : null;
        boolean riding = aircraft != null && aircraft.getAcInfo() != null;
        if (!riding) {
            if (player != null && player.getVehicle() instanceof io.github.mcheliceport.entity.uav.UavStationEntity station
                    && mc.gui.screen() == null) {
                boolean exit = ModKeyMappings.UNMOUNT.consumeClick();
                if (exit || ModKeyMappings.GUI.consumeClick()) {
                    ClientPacketDistributor.sendToServer(new io.github.mcheliceport.net.UavStationControlPayload(station.getId(), exit));
                }
                ModKeyMappings.ALL.forEach(key -> { while (key.consumeClick()) { } });
            }
            if (wasRiding) {
                MouseFlight.reset();
            }
            wasRiding = false;
            return;
        }
        wasRiding = true;
        update(player, aircraft);
        if (mc.gui.screen() == null && !aircraft.isDestroyed()) {
            playerControl(player, aircraft, aircraft.isPilot(player));
        }
    }

    /**
     * Upstream {@code MCH_ClientHeliTickHandler#update}: the gunner's pitch is held in the seat's
     * range, the camera follows the rider's look, and the radar sweeps.
     */
    private static void update(LocalPlayer player, AircraftEntity aircraft) {
        if (aircraft.isGunnerMode(player)) {
            SeatInfo seat = aircraft.getSeatInfo(aircraft.getSeatIdByEntity(player));
            if (seat != null) {
                limitPitch = true;
                minPitch = seat.minPitch;
                maxPitch = seat.maxPitch;
                player.setXRot(Mth.clamp(player.getXRot(), minPitch, maxPitch));
            }
        }
        aircraft.camera.prevRotationYaw = aircraft.camera.rotationYaw;
        aircraft.camera.prevRotationPitch = aircraft.camera.rotationPitch;
        aircraft.camera.rotationYaw = player.getYRot();
        aircraft.camera.rotationPitch = player.getXRot();
        if (aircraft.isEntityRadarMounted()) {
            aircraft.radar.update(aircraft, aircraft instanceof ShipEntity ? 10 : 5);
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

    private static void playerControl(LocalPlayer player, AircraftEntity aircraft, boolean isPilot) {
        boolean send = false;
        HeliControlPayload.UnmountAction unmount = HeliControlPayload.UnmountAction.NONE;
        HeliControlPayload.ModeSwitch mode = HeliControlPayload.ModeSwitch.NONE;
        HeliControlPayload.HatchSwitch hatch = HeliControlPayload.HatchSwitch.NONE;
        HeliControlPayload.GearSwitch gear = HeliControlPayload.GearSwitch.NONE;
        HeliControlPayload.BladeStatus blades = HeliControlPayload.BladeStatus.NONE;
        PlayerControlSwitches switches = PlayerControlSwitches.NONE;
        byte freeLook = 0;
        byte cameraMode = 0;
        PlaneControlPayload.PartSwitch vtol = PlaneControlPayload.PartSwitch.NONE;
        PlaneControlPayload.PartSwitch wing = PlaneControlPayload.PartSwitch.NONE;
        boolean freeLookHeld = ModKeyMappings.isFreeLookHeld();
        boolean ropesClicked = ModKeyMappings.ROPES.consumeClick();
        if (ModKeyMappings.SEARCH_LIGHT.consumeClick()) {
            if (aircraft.canSwitchSearchLight(player)) {
                switches = switches.with(PlayerControlSwitches.SWITCH_SEARCH_LIGHT, true);
                send = true;
                playSoundOk();
            } else { playSoundNg(); }
        }

        // Upstream handleCameraAndModes: free look + camera mode toggles the gunner, else the camera mode.
        if (ModKeyMappings.CAMERA_MODE.consumeClick()) {
            if (freeLookHeld) {
                switches = switches.with(PlayerControlSwitches.SWITCH_GUNNER_STATUS, true);
                send = true;
                playSoundOk();
            } else if (aircraft.canSwitchCameraMode(aircraft.getSeatIdByEntity(player))) {
                int before = aircraft.getCameraMode(player);
                aircraft.switchCameraMode(player, before + 1);
                int newMode = aircraft.getCameraMode(player);
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

        if (ModKeyMappings.UNMOUNT.consumeClick()) {
            unmount = HeliControlPayload.UnmountAction.UNMOUNT_SELF;
            send = true;
        }
        boolean hoverOrEject = ModKeyMappings.SWITCH_HOVERING.consumeClick();
        if (hoverOrEject && aircraft instanceof WingedAircraftEntity) {
            if (aircraft.canEjectSeat(player)) {
                switches = switches.with(PlayerControlSwitches.EJECT_SEAT, true);
                send = true;
            } else {
                playSoundNg();
            }
        }

        if (isPilot) {
            if (ropesClicked) {
                unmount = HeliControlPayload.UnmountAction.UNMOUNT_CREW; send = true;
            }
            if (ModKeyMappings.PUT_TO_RACK.consumeClick()) {
                ClientPacketDistributor.sendToServer(new io.github.mcheliceport.net.RackControlPayload(aircraft.getId(),
                        aircraft.racks().canRide() ? io.github.mcheliceport.net.RackControlPayload.Action.RIDE
                                : io.github.mcheliceport.net.RackControlPayload.Action.LOAD));
            } else if (ModKeyMappings.DOWN_FROM_RACK.consumeClick()) {
                ClientPacketDistributor.sendToServer(new io.github.mcheliceport.net.RackControlPayload(aircraft.getId(),
                        aircraft.isPassenger() ? io.github.mcheliceport.net.RackControlPayload.Action.LEAVE
                                : io.github.mcheliceport.net.RackControlPayload.Action.UNLOAD));
            }
            if (ModKeyMappings.GEAR_UP_DOWN.consumeClick() && aircraft.getAcInfo().haveLandingGear()) {
                if (aircraft.canFoldLandingGear()) {
                    gear = HeliControlPayload.GearSwitch.FOLD;
                } else if (aircraft.canUnfoldLandingGear()) {
                    gear = HeliControlPayload.GearSwitch.UNFOLD;
                }
                send = true;
            }
            if (ModKeyMappings.FREE_LOOK.consumeClick() && !(aircraft instanceof GroundVehicleEntity)) {
                freeLook = (byte) (aircraft.isFreeLookMode() ? 2 : 1);
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
            if (aircraft.ropes().active()) { up = down = right = left = false; }
            if (aircraft instanceof TankEntity tank) {
                boolean brake = ModKeyMappings.SWITCH_HOVERING.isDown();
                if (brake != tank.getBrake()) { send = true; }
                tank.setBrake(brake);
                if (brake) {
                    up = down = false;
                    if (tank.getCurrentThrottle() <= 0.03 && tank.getDeltaMovement().horizontalDistanceSqr() < 0.01) { left = right = false; }
                }
            }
            if (up != aircraft.throttleUp || down != aircraft.throttleDown || right != aircraft.moveRight
                    || left != aircraft.moveLeft) {
                send = true;
            }
            aircraft.throttleUp = up;
            aircraft.throttleDown = down;
            aircraft.moveRight = right;
            aircraft.moveLeft = left;
            switches = switches.with(PlayerControlSwitches.THROTTLE_UP, up)
                    .with(PlayerControlSwitches.THROTTLE_DOWN, down)
                    .with(PlayerControlSwitches.MOVE_RIGHT, right)
                    .with(PlayerControlSwitches.MOVE_LEFT, left);

            if (extraClicked) {
                if (aircraft instanceof HeliEntity helicopter && helicopter.canSwitchFoldBlades()) {
                    if (helicopter.isFoldBlades()) {
                        helicopter.unfoldBlades();
                        blades = HeliControlPayload.BladeStatus.UNFOLD;
                    } else {
                        helicopter.foldBlades();
                        blades = HeliControlPayload.BladeStatus.FOLD;
                    }
                    send = true;
                    playSoundOk();
                } else if (aircraft instanceof WingedAircraftEntity plane && plane.canSwitchVtol()) {
                    vtol = plane.getVtolMode() == 0 ? PlaneControlPayload.PartSwitch.ON : PlaneControlPayload.PartSwitch.OFF;
                    send = true;
                    playSoundOk();
                } else {
                    playSoundNg();
                }
            }

            if (hoverOrEject && aircraft instanceof HeliEntity) {
                if (aircraft.canSwitchHoveringMode()) {
                    mode = aircraft.isHoveringMode() ? HeliControlPayload.ModeSwitch.HOVERING_OFF
                            : HeliControlPayload.ModeSwitch.HOVERING_ON;
                    aircraft.switchHoveringMode(!aircraft.isHoveringMode());
                    send = true;
                } else {
                    playSoundNg();
                }
            } else if (ModKeyMappings.SWITCH_GUNNER.consumeClick()) {
                if (aircraft instanceof WingedAircraftEntity plane) {
                    boolean wasGunner = plane.isGunnerMode();
                    if (!plane.cycleCameraView(player)) {
                        playSoundNg();
                    } else if (wasGunner != plane.isGunnerMode()) {
                        mode = plane.isGunnerMode() ? HeliControlPayload.ModeSwitch.GUNNER_ON
                                : HeliControlPayload.ModeSwitch.GUNNER_OFF;
                        send = true;
                    }
                } else if (aircraft instanceof GroundVehicleEntity ground) {
                    if (!ground.cycleCameraView(player)) { playSoundNg(); }
                } else if (aircraft.canSwitchGunnerMode()) {
                    mode = aircraft.isGunnerMode() ? HeliControlPayload.ModeSwitch.GUNNER_OFF
                            : HeliControlPayload.ModeSwitch.GUNNER_ON;
                    aircraft.switchGunnerMode(!aircraft.isGunnerMode());
                    send = true;
                } else {
                    playSoundNg();
                }
            }
        } else if (ModKeyMappings.SWITCH_GUNNER.consumeClick()) {
            if (aircraft.canSwitchGunnerModeOtherSeat(player)) {
                aircraft.switchGunnerModeOtherSeat();
                send = true;
            } else {
                playSoundNg();
            }
        }

        if (ModKeyMappings.ZOOM.consumeClick()) {
            boolean uav = aircraft.isUAV() && !aircraft.getAcInfo().haveHatch();
            if (aircraft.isGunnerMode(player) || uav || aircraft instanceof VehicleEntity) {
                aircraft.zoomCamera();
                Minecraft.getInstance().getSoundManager()
                        .play(SimpleSoundInstance.forUI(McheliSounds.event("zoom").value(), 1.0F, 0.5F));
            } else if (isPilot && aircraft.getAcInfo().haveHatch()) {
                // Upstream's hatch key sends FOLD to open a closed hatch and UNFOLD to close it.
                if (aircraft.canUnfoldHatch()) {
                    hatch = HeliControlPayload.HatchSwitch.FOLD;
                    send = true;
                } else if (aircraft.canFoldHatch()) {
                    hatch = HeliControlPayload.HatchSwitch.UNFOLD;
                    send = true;
                } else {
                    playSoundNg();
                }
            } else if (isPilot && aircraft instanceof WingedAircraftEntity plane) {
                if (plane.canFoldWing()) {
                    wing = PlaneControlPayload.PartSwitch.ON;
                    send = true;
                } else if (plane.canUnfoldWing()) {
                    wing = PlaneControlPayload.PartSwitch.OFF;
                    send = true;
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
            HeliControlPayload common = new HeliControlPayload(aircraft.getId(), unmount, mode, hatch, gear,
                    switches, freeLook, blades, cameraMode);
            ClientPacketDistributor.sendToServer(aircraft instanceof GroundVehicleEntity ground
                    ? new GroundControlPayload(common, ground.getBrake())
                    : aircraft instanceof ShipEntity ? new ShipControlPayload(common, vtol, wing)
                    : aircraft instanceof WingedAircraftEntity
                    ? new PlaneControlPayload(common, vtol, wing) : common);
        }
    }

    private static void playSoundOk() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private static void playSoundNg() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(McheliSounds.event("ng"), 1.0F));
    }
}
