package io.github.mcheliceport.client;

import io.github.mcheliceport.client.camera.AircraftCameraClient;
import io.github.mcheliceport.client.hud.AircraftHudLayer;
import io.github.mcheliceport.client.input.AircraftClientControl;
import io.github.mcheliceport.client.input.MouseFlight;
import io.github.mcheliceport.client.input.WeaponClientControl;
import io.github.mcheliceport.client.model.AircraftModels;
import io.github.mcheliceport.client.sound.AircraftEngineSound;
import io.github.mcheliceport.client.tool.RangeFinderClient;
import io.github.mcheliceport.client.tool.TargetMarkers;
import io.github.mcheliceport.multiplay.SpotClientHooks;
import io.github.mcheliceport.client.weapon.WeaponClient;
import io.github.mcheliceport.client.weapon.WeaponEffects;
import io.github.mcheliceport.config.ServerSettings;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.seat.SeatEntity;
import io.github.mcheliceport.weather.FlightWeather;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** The client's game-bus hooks for riding vehicles: controls, camera, camera roll and the hidden hand. */
public final class AircraftClientEvents {

    private AircraftClientEvents() {
    }

    public static void register() {
        AircraftEntity.setClientHooks(new AircraftEntity.ClientHooks() {
            @Override
            public void onPassengerTurned(AircraftEntity aircraft, Entity passenger) {
                MouseFlight.onPassengerTurned(aircraft, passenger);
            }

            @Override
            public void sendToServer(net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
                io.github.mcheliceport.platform.Platform.get().sendToServer(payload);
            }
        });
        io.github.mcheliceport.platform.ClientEvents.TICK_POST.add(() -> {
            AircraftCameraClient.handleKeys();
            io.github.mcheliceport.client.hud.TeamListOverlay.handleKeys();
            WeaponClientControl.onClientTick();
            io.github.mcheliceport.client.hud.WingmanKeyHintLayer.handleKey();
            AircraftClientControl.onClientTick();
            AircraftCameraClient.tick();
            io.github.mcheliceport.client.camera.GltdCameraClient.tick();
            AircraftModels.tick();
            AircraftEngineSound.tickAll();
            WeaponClient.tick();
            RangeFinderClient.tick();
            io.github.mcheliceport.client.tool.LightWeaponClient.tick();
            io.github.mcheliceport.weapon.GpsPosition.clientTick(Minecraft.getInstance().player);
            io.github.mcheliceport.client.render.ToolItemModel.WrenchSwing.tick();
            TargetMarkers.INSTANCE.tick();
        });
        io.github.mcheliceport.platform.ClientEvents.SUBMIT_GEOMETRY.add(WeaponEffects::onSubmitGeometry);
        io.github.mcheliceport.platform.ClientEvents.RENDER_FRAME_PRE.add(() -> {
            MouseFlight.onFrameWithoutTurn();
            io.github.mcheliceport.client.render.SearchLightRenderer.beginFrame();
        });
        io.github.mcheliceport.platform.ClientEvents.RENDER_FRAME_POST.add(() -> AircraftClientControl.applyRotLimit());
        io.github.mcheliceport.platform.ClientEvents.CAMERA_ANGLES.add(AircraftCameraClient::onCameraAngles);
        io.github.mcheliceport.platform.ClientEvents.CAMERA_ANGLES.add(AircraftClientEvents::onCameraAngles);
        io.github.mcheliceport.platform.ClientEvents.COMPUTE_FOV.add(AircraftCameraClient::onComputeFov);
        io.github.mcheliceport.platform.ClientEvents.COMPUTE_FOV.add(RangeFinderClient::onComputeFov);
        io.github.mcheliceport.platform.ClientEvents.COMPUTE_FOV.add(io.github.mcheliceport.client.tool.LightWeaponClient::onComputeFov);
        io.github.mcheliceport.platform.ClientEvents.RENDER_HAND.add(io.github.mcheliceport.client.tool.LightWeaponClient::onRenderHand);
        io.github.mcheliceport.platform.ClientEvents.COMPUTE_FOV.add(io.github.mcheliceport.client.camera.GltdCameraClient::onComputeFov);
        io.github.mcheliceport.platform.ClientEvents.CAMERA_ANGLES.add(io.github.mcheliceport.client.camera.GltdCameraClient::onCameraAngles);
        io.github.mcheliceport.platform.ClientEvents.RENDER_HAND.add(io.github.mcheliceport.client.camera.GltdCameraClient::onRenderHand);
        io.github.mcheliceport.platform.ClientEvents.INTERACTION_KEY.add(io.github.mcheliceport.client.camera.GltdCameraClient::onInteractionKey);
        io.github.mcheliceport.platform.ClientEvents.NAME_TAG.add(io.github.mcheliceport.client.camera.GltdCameraClient::onNameTag);
        io.github.mcheliceport.platform.ClientEvents.RENDER_HAND.add(io.github.mcheliceport.client.hud.ToolHudLayer::onRenderHand);
        io.github.mcheliceport.platform.ClientEvents.DETACHED_CAMERA_DISTANCE.add(AircraftCameraClient::onDetachedDistance);
        io.github.mcheliceport.platform.ClientEvents.NAME_TAG.add(AircraftCameraClient::onNameTag);
        io.github.mcheliceport.platform.ClientEvents.RENDER_HAND.add(AircraftClientEvents::onRenderHand);
        io.github.mcheliceport.platform.ClientEvents.INTERACTION_KEY.add(WeaponClientControl::onInteractionKey);
        io.github.mcheliceport.platform.ClientEvents.KEY.add(AircraftClientControl::onKey);
        io.github.mcheliceport.platform.ClientEvents.KEY.add(io.github.mcheliceport.client.hud.TeamListOverlay::onKey);
        io.github.mcheliceport.platform.ClientEvents.MOUSE_SCROLL.add(WeaponClientControl::onMouseScroll);
        io.github.mcheliceport.platform.ClientEvents.RENDER_GUI_LAYER.add(AircraftHudLayer::onRenderLayer);
        io.github.mcheliceport.platform.ClientEvents.LOGGING_OUT.add(() -> {
            ServerSettings.setClient(ServerSettings.Values.DEFAULT);
            io.github.mcheliceport.multiplay.MultiplayManager.setClientState(true, false);
            MouseFlight.reset();
            AircraftCameraClient.reset();
            AircraftEngineSound.clear();
            io.github.mcheliceport.client.render.FlightLighting.clear();
            WeaponClient.reset();
            io.github.mcheliceport.client.render.WeaponMechanisms.clear();
            TargetMarkers.INSTANCE.reset();
            io.github.mcheliceport.client.camera.GltdCameraClient.reset();
            io.github.mcheliceport.client.tool.LightWeaponClient.reset();
            io.github.mcheliceport.radar.EntityInfoClientTracker.clear();
            io.github.mcheliceport.weapon.GpsPosition.setClient(null);
        });
        io.github.mcheliceport.platform.ClientEvents.LOGGING_IN.add(() -> {
            RangeFinderClient.onLogin();
            TargetMarkers.INSTANCE.reset();
        });
        SpotClientHooks.set(TargetMarkers.INSTANCE);
        io.github.mcheliceport.wingman.WingmanClientHooks.setMarkerScreen(
                p -> Minecraft.getInstance().gui.setScreen(new io.github.mcheliceport.client.gui.MarkerConfigScreen(p)));
        io.github.mcheliceport.wingman.WingmanClientHooks.setWingmanPanel(
                p -> Minecraft.getInstance().gui.setScreen(new io.github.mcheliceport.client.gui.WingmanPanelScreen(p)));
        io.github.mcheliceport.wingman.WingmanClientHooks.setBaseScreen(
                p -> Minecraft.getInstance().gui.setScreen(new io.github.mcheliceport.client.gui.BaseConfigScreen(p)));
        io.github.mcheliceport.wingman.WingmanClientHooks.setPlanner(
                p -> Minecraft.getInstance().gui.setScreen(new io.github.mcheliceport.client.gui.WingmanPlannerScreen(p)));
    }

    public static @Nullable AircraftEntity riddenAircraft(@Nullable LocalPlayer player) {
        if (player == null) {
            return null;
        }
        Entity vehicle = player.getVehicle();
        if (vehicle instanceof AircraftEntity aircraft) {
            return aircraft;
        }
        if (vehicle instanceof io.github.mcheliceport.entity.uav.UavStationEntity) {
            return io.github.mcheliceport.net.ModNetwork.riddenAircraft(player);
        }
        return vehicle instanceof SeatEntity seat ? seat.getParent() : null;
    }

    /** Upstream {@code updateCameraRoll}: the view takes the vehicle's roll, less when looking sideways. */
    private static void onCameraAngles(io.github.mcheliceport.platform.ClientEvents.CameraAngles event) {
        LocalPlayer player = Minecraft.getInstance().player;
        AircraftEntity aircraft = riddenAircraft(player);
        Entity view = event.getCamera().entity();
        if (aircraft == null || view != player && view != AircraftCameraClient.viewEntity()) {
            return;
        }
        float partial = (float) event.getPartialTick();
        float roll = Mth.wrapDegrees(aircraft.getRoll(partial));
        float yaw = Mth.wrapDegrees(aircraft.getViewYRot(partial) - player.getViewYRot(partial));
        roll *= Mth.cos((float) (yaw * Math.PI / 180.0));
        // Upstream: no roll while steering one's own TV missile from the gunner view.
        if (aircraft.getTVMissile() != null && aircraft.getTVMissile().shootingEntity == player
                && aircraft.isGunnerMode(player)) {
            roll = 0.0F;
        }
        // M10e: a thunderstorm shakes the view of a vehicle in flight.
        if (FlightWeather.gust(aircraft) != Vec3.ZERO) {
            roll += FlightWeather.turbulenceRoll(
                    FlightWeather.at(aircraft.level(), aircraft.position()),
                    aircraft.level().getGameTime() + partial, aircraft.getUUID().hashCode());
        }
        event.setRoll(event.getRoll() + roll);
    }

    /** The hand is hidden for the pilot and in camera view (upstream {@code disableFirstPersonItemRender}). */
    private static void onRenderHand(io.github.mcheliceport.platform.ClientEvents.Cancelable event) {
        LocalPlayer player = Minecraft.getInstance().player;
        AircraftEntity aircraft = riddenAircraft(player);
        if (aircraft != null && (aircraft.isPilot(player) || AircraftCameraClient.viewEntity() != null)) {
            event.setCanceled(true);
        }
    }
}
