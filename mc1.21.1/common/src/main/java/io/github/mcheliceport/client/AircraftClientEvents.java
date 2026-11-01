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
import io.github.mcheliceport.shim.client.event.ClientPlayerNetworkEvent;
import io.github.mcheliceport.shim.client.event.ClientTickEvent;
import io.github.mcheliceport.shim.client.event.RenderFrameEvent;
import io.github.mcheliceport.shim.client.event.RenderHandEvent;
import io.github.mcheliceport.shim.client.event.ViewportEvent;
import io.github.mcheliceport.platform.network.ClientPacketDistributor;
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
                ClientPacketDistributor.sendToServer(payload);
            }
        });
        io.github.mcheliceport.shim.client.event.ClientBus.addListener(ClientTickEvent.Post.class, (ClientTickEvent.Post event) -> {
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
        io.github.mcheliceport.shim.client.event.ClientBus.listen(WeaponEffects.class, "onSubmitGeometry");
        io.github.mcheliceport.shim.client.event.ClientBus.addListener(RenderFrameEvent.Pre.class, (RenderFrameEvent.Pre event) -> {
            MouseFlight.onFrameWithoutTurn();
            io.github.mcheliceport.client.render.SearchLightRenderer.beginFrame();
        });
        io.github.mcheliceport.shim.client.event.ClientBus.addListener(RenderFrameEvent.Post.class, (RenderFrameEvent.Post event) -> AircraftClientControl.applyRotLimit());
        io.github.mcheliceport.shim.client.event.ClientBus.listen(AircraftCameraClient.class, "onCameraAngles");
        io.github.mcheliceport.shim.client.event.ClientBus.listen(AircraftClientEvents.class, "onCameraAngles");
        io.github.mcheliceport.shim.client.event.ClientBus.listen(AircraftCameraClient.class, "onComputeFov");
        io.github.mcheliceport.shim.client.event.ClientBus.listen(RangeFinderClient.class, "onComputeFov");
        io.github.mcheliceport.shim.client.event.ClientBus.listen(io.github.mcheliceport.client.tool.LightWeaponClient.class, "onComputeFov");
        io.github.mcheliceport.shim.client.event.ClientBus.listen(io.github.mcheliceport.client.tool.LightWeaponClient.class, "onRenderHand");
        io.github.mcheliceport.shim.client.event.ClientBus.listen(io.github.mcheliceport.client.camera.GltdCameraClient.class, "onComputeFov");
        io.github.mcheliceport.shim.client.event.ClientBus.listen(io.github.mcheliceport.client.camera.GltdCameraClient.class, "onCameraAngles");
        io.github.mcheliceport.shim.client.event.ClientBus.listen(io.github.mcheliceport.client.camera.GltdCameraClient.class, "onRenderHand");
        io.github.mcheliceport.shim.client.event.ClientBus.listen(io.github.mcheliceport.client.camera.GltdCameraClient.class, "onInteractionKey");
        io.github.mcheliceport.shim.client.event.ClientBus.listen(io.github.mcheliceport.client.camera.GltdCameraClient.class, "onNameTag");
        io.github.mcheliceport.shim.client.event.ClientBus.listen(io.github.mcheliceport.client.hud.ToolHudLayer.class, "onRenderHand");
        io.github.mcheliceport.shim.client.event.ClientBus.listen(AircraftCameraClient.class, "onDetachedDistance");
        io.github.mcheliceport.shim.client.event.ClientBus.listen(AircraftCameraClient.class, "onNameTag");
        io.github.mcheliceport.shim.client.event.ClientBus.listen(AircraftClientEvents.class, "onRenderHand");
        io.github.mcheliceport.shim.client.event.ClientBus.listen(WeaponClientControl.class, "onInteractionKey");
        io.github.mcheliceport.shim.client.event.ClientBus.listen(AircraftClientControl.class, "onKey");
        io.github.mcheliceport.shim.client.event.ClientBus.listen(io.github.mcheliceport.client.hud.TeamListOverlay.class, "onKey");
        io.github.mcheliceport.shim.client.event.ClientBus.listen(WeaponClientControl.class, "onMouseScroll");
        io.github.mcheliceport.shim.client.event.ClientBus.listen(AircraftHudLayer.class, "onRenderLayer");
        io.github.mcheliceport.shim.client.event.ClientBus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, (ClientPlayerNetworkEvent.LoggingOut event) -> {
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
        io.github.mcheliceport.shim.client.event.ClientBus.addListener(ClientPlayerNetworkEvent.LoggingIn.class, (ClientPlayerNetworkEvent.LoggingIn event) -> {
            RangeFinderClient.onLogin();
            TargetMarkers.INSTANCE.reset();
        });
        SpotClientHooks.set(TargetMarkers.INSTANCE);
        io.github.mcheliceport.wingman.WingmanClientHooks.setMarkerScreen(
                p -> Minecraft.getInstance().setScreen(new io.github.mcheliceport.client.gui.MarkerConfigScreen(p)));
        io.github.mcheliceport.wingman.WingmanClientHooks.setWingmanPanel(
                p -> Minecraft.getInstance().setScreen(new io.github.mcheliceport.client.gui.WingmanPanelScreen(p)));
        io.github.mcheliceport.wingman.WingmanClientHooks.setBaseScreen(
                p -> Minecraft.getInstance().setScreen(new io.github.mcheliceport.client.gui.BaseConfigScreen(p)));
        io.github.mcheliceport.wingman.WingmanClientHooks.setPlanner(
                p -> Minecraft.getInstance().setScreen(new io.github.mcheliceport.client.gui.WingmanPlannerScreen(p)));
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
    private static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
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
    private static void onRenderHand(RenderHandEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        AircraftEntity aircraft = riddenAircraft(player);
        if (aircraft != null && (aircraft.isPilot(player) || AircraftCameraClient.viewEntity() != null)) {
            event.setCanceled(true);
        }
    }
}
