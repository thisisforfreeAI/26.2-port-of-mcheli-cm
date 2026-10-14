package io.github.mcheliceport.client;

import io.github.mcheliceport.client.input.HeliClientControl;
import io.github.mcheliceport.client.input.MouseFlight;
import io.github.mcheliceport.client.model.AircraftModels;
import io.github.mcheliceport.client.sound.AircraftEngineSound;
import io.github.mcheliceport.config.ServerSettings;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.seat.SeatEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;
import org.jspecify.annotations.Nullable;

/** The client's game-bus hooks for riding vehicles: controls, camera roll and the hidden hand. */
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
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
            HeliClientControl.onClientTick();
            AircraftModels.tick();
            AircraftEngineSound.tickAll();
        });
        NeoForge.EVENT_BUS.addListener((RenderFrameEvent.Pre event) -> MouseFlight.onFrameWithoutTurn());
        NeoForge.EVENT_BUS.addListener(AircraftClientEvents::onCameraAngles);
        NeoForge.EVENT_BUS.addListener(AircraftClientEvents::onRenderHand);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> {
            ServerSettings.setClient(ServerSettings.Values.DEFAULT);
            MouseFlight.reset();
        });
    }

    public static @Nullable AircraftEntity riddenAircraft(@Nullable LocalPlayer player) {
        if (player == null) {
            return null;
        }
        Entity vehicle = player.getVehicle();
        if (vehicle instanceof AircraftEntity aircraft) {
            return aircraft;
        }
        return vehicle instanceof SeatEntity seat ? seat.getParent() : null;
    }

    /** Upstream {@code updateCameraRoll}: the view takes the vehicle's roll, less when looking sideways. */
    private static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        LocalPlayer player = Minecraft.getInstance().player;
        AircraftEntity aircraft = riddenAircraft(player);
        if (aircraft == null || event.getCamera().entity() != player) {
            return;
        }
        float partial = (float) event.getPartialTick();
        float roll = Mth.wrapDegrees(aircraft.getRoll(partial));
        float yaw = Mth.wrapDegrees(aircraft.getViewYRot(partial) - player.getViewYRot(partial));
        roll *= Mth.cos((float) (yaw * Math.PI / 180.0));
        event.setRoll(event.getRoll() + roll);
    }

    /** The pilot's hand is hidden (upstream {@code disableFirstPersonItemRender}). */
    private static void onRenderHand(RenderHandEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && player.getVehicle() instanceof AircraftEntity aircraft && aircraft.isPilot(player)) {
            event.setCanceled(true);
        }
    }
}
