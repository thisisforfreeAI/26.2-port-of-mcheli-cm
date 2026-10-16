package io.github.mcheliceport.client.input;

import io.github.mcheliceport.config.ClientConfig;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.entity.aircraft.WingedAircraftEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Flying with the mouse: upstream's {@code MouseInputHandler} for the pilot of a helicopter, plane or ship.
 *
 * <p>Upstream read the mouse once per rendered frame. The port does the same from
 * {@link AircraftEntity#onPassengerTurned}, which vanilla calls every frame while the mouse is
 * grabbed, right after it turned the player and before the frame's camera is set up. When no
 * screen-free frame turns the player (a screen is open), {@link #onFrameWithoutTurn()} keeps the
 * attitude rules running with the stick where it is.
 */
public final class MouseFlight {

    /** Upstream {@code MAX_STICK_LENGTH}. */
    public static final double MAX_STICK_LENGTH = 40.0;

    private static double prevMouseDeltaX;
    private static double prevMouseDeltaY;
    private static double mouseDeltaX;
    private static double mouseDeltaY;
    private static double mouseRollDeltaX;
    private static double mouseRollDeltaY;
    private static long prevNanoTime;
    private static boolean turnedThisFrame;

    private MouseFlight() {
    }

    public static double getStickX() {
        return mouseRollDeltaX;
    }

    public static double getStickY() {
        return mouseRollDeltaY;
    }

    private static boolean isStickMode(AircraftEntity aircraft) {
        return aircraft instanceof WingedAircraftEntity ? ClientConfig.MOUSE_CONTROL_STICK_MODE_PLANE.get()
                : aircraft instanceof HeliEntity && ClientConfig.MOUSE_CONTROL_STICK_MODE_HELI.get();
    }

    /** Vanilla turned the local player, who rides {@code aircraft} directly, by one frame of mouse input. */
    public static void onPassengerTurned(AircraftEntity aircraft, Entity passenger) {
        if (aircraft.isPassenger()) { return; }
        Minecraft mc = Minecraft.getInstance();
        if (passenger != mc.player || !aircraft.canMouseRot()) {
            return;
        }
        turnedThisFrame = true;
        Player player = mc.player;
        // The turn vanilla just applied, worked out the way MouseHandler#turnPlayer does.
        double s = mc.options.sensitivity().get() * 0.6 + 0.2;
        double sens = s * s * s * 8.0;
        double xo = mc.mouseHandler.getXVelocity() * sens;
        double yo = mc.mouseHandler.getYVelocity() * sens;
        if (mc.options.invertMouseX().get()) {
            xo = -xo;
        }
        boolean vanillaInvertY = mc.options.invertMouseY().get();
        double turnedPitch = (vanillaInvertY ? -yo : yo) * 0.15;
        float yawBefore = player.getYRot() - (float) (xo * 0.15);
        float pitchBefore = Mth.clamp(player.getXRot() - (float) turnedPitch, -90.0F, 90.0F);

        boolean stickMode = isStickMode(aircraft);
        float partialTicks = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        prevMouseDeltaX = mouseDeltaX;
        prevMouseDeltaY = mouseDeltaY;
        if (!stickMode) {
            if (Math.abs(mouseRollDeltaX) < MAX_STICK_LENGTH * 0.2) {
                mouseRollDeltaX *= 1.0F - 0.15F * partialTicks;
            }
            if (Math.abs(mouseRollDeltaY) < MAX_STICK_LENGTH * 0.2) {
                mouseRollDeltaY *= 1.0F - 0.15F * partialTicks;
            }
        }
        double ms = ClientConfig.MOUSE_SENSITIVITY.get() * 0.1;
        // 1.12's mouse Y grew upwards; GLFW's grows downwards.
        mouseDeltaX = ms * xo;
        mouseDeltaY = ms * -yo;
        int inv = vanillaInvertY ? -1 : 1;
        if (ClientConfig.INVERT_MOUSE.get()) {
            inv = -inv;
        }
        mouseRollDeltaX += mouseDeltaX;
        mouseRollDeltaY += mouseDeltaY * inv;
        clampStick();

        apply(aircraft, player, yawBefore, pitchBefore, vanillaInvertY ? -1 : 1);
        dampStick(stickMode);
    }

    /** A frame in which vanilla did not turn the player (a screen is open): the stick holds still. */
    public static void onFrameWithoutTurn() {
        boolean turned = turnedThisFrame;
        turnedThisFrame = false;
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        AircraftEntity aircraft = player != null ? io.github.mcheliceport.net.ModNetwork.riddenAircraft(player) : null;
        if (turned || player == null || aircraft == null
                || !aircraft.canMouseRot()) {
            if (!turned) {
                prevNanoTime = 0L;
            }
            return;
        }
        prevMouseDeltaX = mouseDeltaX;
        prevMouseDeltaY = mouseDeltaY;
        mouseDeltaX = 0.0;
        mouseDeltaY = 0.0;
        apply(aircraft, player, player.getYRot(), player.getXRot(), 1);
        dampStick(isStickMode(aircraft));
    }

    private static void clampStick() {
        double dist = mouseRollDeltaX * mouseRollDeltaX + mouseRollDeltaY * mouseRollDeltaY;
        if (dist > 1.0) {
            dist = Math.sqrt(dist);
            double d = Math.min(dist, MAX_STICK_LENGTH);
            mouseRollDeltaX = mouseRollDeltaX / dist * d;
            mouseRollDeltaY = mouseRollDeltaY / dist * d;
        }
    }

    /** Upstream {@code dampMouseRollIfNeeded}. */
    private static void dampStick(boolean stickMode) {
        double dist = Math.sqrt(mouseRollDeltaX * mouseRollDeltaX + mouseRollDeltaY * mouseRollDeltaY);
        if (!stickMode || dist < MAX_STICK_LENGTH * 0.1) {
            mouseRollDeltaX *= 0.95;
            mouseRollDeltaY *= 0.95;
        }
    }

    /** Upstream {@code setAngles} with its player half, then {@code updateRiderLastPositions}. */
    private static void apply(AircraftEntity aircraft, Player player, float yawBefore, float pitchBefore,
            int vanillaInvert) {
        long now = System.nanoTime();
        if (prevNanoTime == 0L) {
            prevNanoTime = now;
        }
        float deltaSeconds = Math.min((now - prevNanoTime) / 1.0E9F, 0.1F);
        prevNanoTime = now;

        float avgX = (float) (mouseDeltaX + prevMouseDeltaX) / 2.0F;
        float avgY = (float) (mouseDeltaY + prevMouseDeltaY) / 2.0F;
        if (aircraft.getAcInfo() == null) {
            return;
        }
        boolean ground = aircraft instanceof io.github.mcheliceport.entity.ground.GroundVehicleEntity;
        if (ground) {
            float limit = aircraft.getAcInfo().cameraRotationSpeed * 5;
            var weapon = aircraft.getCurrentWeapon(player).getInfo();
            if (weapon != null) { limit *= weapon.cameraRotationSpeedPitch; }
            avgX = Mth.clamp(avgX, -limit, limit); avgY = Mth.clamp(avgY, -limit, limit);
        }
        aircraft.setAngles((float) mouseRollDeltaX, (float) mouseRollDeltaY, deltaSeconds);

        if (!aircraft.isOverridePlayerYaw()) {
            float yaw = yawBefore + avgX * 0.15F;
            player.yRotO += yaw - player.getYRot();
            player.setYRot(yaw);
        } else {
            float yaw = aircraft.getYaw();
            if (aircraft.getVehicle() == null) {
                player.yRotO = yaw;
            } else {
                if (yaw - player.getYRot() > 180.0F) {
                    player.yRotO += 360.0F;
                }
                if (yaw - player.getYRot() < -180.0F) {
                    player.yRotO -= 360.0F;
                }
            }
            player.setYRot(yaw);
        }
        if (!aircraft.isOverridePlayerPitch()) {
            // Upstream turned the player by the averaged delta; the port keeps vanilla's invert setting.
            float pitch = Mth.clamp(pitchBefore - avgY * vanillaInvert * 0.15F, -90.0F, 90.0F);
            player.xRotO = pitch;
            player.setXRot(pitch);
        } else {
            player.xRotO = aircraft.getPitch();
            player.setXRot(aircraft.getPitch());
        }

        if (ground && Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
            float angle = Mth.wrapDegrees(aircraft.getYaw() - player.getYRot()) * Mth.DEG_TO_RAD;
            float base = aircraft.getPitch() * Mth.cos(angle) - aircraft.getRoll() * Mth.sin(angle);
            float pitch = Mth.clamp(player.getXRot(), base + aircraft.getAcInfo().minRotationPitch,
                    base + aircraft.getAcInfo().maxRotationPitch);
            player.setXRot(Mth.clamp(pitch, -90, 90)); player.xRotO = player.getXRot();
        }

        if (aircraft.isPilot(player) && !aircraft.isDestroyed()) {
            aircraft.lastRiderYaw = player.getYRot();
            aircraft.prevLastRiderYaw = player.yRotO;
            aircraft.lastRiderPitch = player.getXRot();
            aircraft.prevLastRiderPitch = player.xRotO;
        }
    }

    /** Forgets the stick when the player gets out (upstream resets it when not riding). */
    public static void reset() {
        prevMouseDeltaX = prevMouseDeltaY = mouseDeltaX = mouseDeltaY = 0.0;
        mouseRollDeltaX = mouseRollDeltaY = 0.0;
        prevNanoTime = 0L;
    }
}
