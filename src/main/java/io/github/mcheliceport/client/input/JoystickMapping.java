package io.github.mcheliceport.client.input;

import org.jspecify.annotations.Nullable;

/**
 * The arithmetic of joystick flight, kept free of GLFW and of client classes so the config (loaded
 * on servers too) and the unit tests can use it.
 */
public final class JoystickMapping {

    /** How the throttle axis drives the vehicle's throttle. */
    public enum ThrottleMode {
        /** RATE for gamepads, LEVER for other devices. */
        AUTO,
        /** The axis position is the wanted throttle. */
        LEVER,
        /** Pushing the axis holds throttle up; pulling it holds throttle down. */
        RATE
    }

    /** One configured button: GLFW button number and the key mapping name it presses. */
    public record Button(int button, String key) {
    }

    /** Throttle steps the synced throttle may be off from the lever before the keys move it. */
    static final double LEVER_TOLERANCE = 0.05;

    private JoystickMapping() {
    }

    /**
     * A raw axis value (-1 to 1) after the dead zone and response curve: 0 inside the dead zone,
     * then rising from 0 to 1 at full deflection, keeping the sign.
     */
    public static float shape(float raw, float deadzone, float curve) {
        if (!Float.isFinite(raw)) {
            return 0.0F;
        }
        float a = Math.min(Math.abs(raw), 1.0F);
        if (a <= deadzone || deadzone >= 1.0F) {
            return 0.0F;
        }
        float t = (a - deadzone) / (1.0F - deadzone);
        return Math.copySign((float) Math.pow(t, Math.max(curve, 1.0F)), raw);
    }

    /**
     * A throttle lever's position as 0 (idle) to 1 (full). GLFW reports a lever pushed forward, like a
     * stick pushed forward, as -1, so forward is full power unless inverted.
     */
    public static double lever(float raw, boolean invert) {
        if (!Float.isFinite(raw)) {
            return 0.0;
        }
        float v = Math.max(-1.0F, Math.min(1.0F, raw));
        return invert ? (v + 1.0) / 2.0 : (1.0 - v) / 2.0;
    }

    /** +1 to hold throttle up, -1 to hold it down, 0 to leave it, so the synced throttle follows the lever. */
    public static int chaseLever(double lever, double throttle) {
        if (lever > throttle + LEVER_TOLERANCE || lever >= 0.999 && throttle < 0.999) {
            return 1;
        }
        if (lever < throttle - LEVER_TOLERANCE || lever <= 0.001 && throttle > 0.001) {
            return -1;
        }
        return 0;
    }

    /** -1, 0 or +1: the axis is past the threshold one way or the other. */
    public static int digital(float shaped, float threshold) {
        return shaped >= threshold ? 1 : shaped <= -threshold ? -1 : 0;
    }

    /** Parses {@code "5=key.mcheli_ce_port.use_weapon"}; null when malformed. */
    public static @Nullable Button parseButton(String text) {
        int eq = text.indexOf('=');
        if (eq <= 0 || eq == text.length() - 1) {
            return null;
        }
        String key = text.substring(eq + 1).trim();
        if (key.isEmpty() || key.chars().anyMatch(Character::isWhitespace)) {
            return null;
        }
        try {
            int button = Integer.parseInt(text.substring(0, eq).trim());
            return button >= 0 && button < 64 ? new Button(button, key) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
