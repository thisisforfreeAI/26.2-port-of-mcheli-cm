package io.github.mcheliceport.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.config.ClientConfig;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.net.ModNetwork;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWGamepadState;
import org.lwjgl.system.MemoryStack;

/**
 * Joystick, HOTAS and gamepad flight through GLFW's joystick API, read on the client thread only.
 *
 * <p>The pitch and roll axes set the control stick that {@link MouseFlight} otherwise moves with the
 * mouse, every frame, so the vehicle turns exactly as it does with a mouse stick: nothing new is
 * sent to the server. The rudder, throttle and ground-vehicle axes become the same held up, down,
 * left and right inputs as the keys, and buttons press whichever key a control is bound to.
 */
public final class JoystickInput {

    private static final int MAX_AXES = 16;
    private static final int MAX_BUTTONS = 64;
    private static int jid = -1;
    private static boolean gamepad;
    private static String deviceName = "";
    private static int rescanCooldown;
    private static boolean announced;
    private static final float[] AXES = new float[MAX_AXES];
    private static int axisCount;
    private static final boolean[] BUTTONS = new boolean[MAX_BUTTONS];
    private static int buttonCount;
    /** Keys currently held down by a joystick button, to release when it lets go or the rider gets out. */
    private static final Set<InputConstants.Key> HELD = new HashSet<>();
    private static final Set<String> WARNED = new HashSet<>();
    private static List<? extends String> parsedFrom;
    private static List<JoystickMapping.Button> bindings = List.of();

    /** The rider's digital inputs from the joystick this tick. */
    public record Digital(boolean up, boolean down, boolean left, boolean right) {
        static final Digital NONE = new Digital(false, false, false, false);
    }

    private JoystickInput() {
    }

    private static boolean enabled() {
        return ClientConfig.SPEC.isLoaded() && ClientConfig.JOYSTICK_ENABLED.get();
    }

    /** Whether a configured joystick is connected and in use. */
    public static boolean active() {
        return jid >= 0 && enabled();
    }

    /**
     * Once per client tick, before the vehicle controls read the keys: finds the device, reads it,
     * and presses or releases the keys its buttons are bound to while the player rides with no screen open.
     */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        AircraftEntity aircraft = player != null ? ModNetwork.riddenAircraft(player) : null;
        if (!enabled()) {
            if (jid >= 0) {
                releaseAll();
                jid = -1;
            }
            return;
        }
        findDevice();
        if (jid < 0 || aircraft == null) {
            releaseAll();
            announced = false;
            return;
        }
        read();
        if (!announced) {
            announced = true;
            player.sendOverlayMessage(Component.translatable("mcheli_ce_port.joystick.connected", deviceName));
        }
        if (mc.gui.screen() != null) {
            releaseAll();
            return;
        }
        pressButtons();
        if (ClientConfig.JOYSTICK_SHOW_INPUTS.get() && player.tickCount % 4 == 0) {
            player.sendOverlayMessage(Component.literal(describeInputs()));
        }
    }

    private static void findDevice() {
        int wanted = ClientConfig.JOYSTICK_DEVICE.get();
        if (jid >= 0 && (wanted < 0 || wanted == jid) && GLFW.glfwJoystickPresent(jid)) {
            return;
        }
        if (jid >= 0) {
            McheliCePort.LOGGER.info("Joystick {} ({}) disconnected", jid, deviceName);
            releaseAll();
            jid = -1;
            announced = false;
        }
        if (rescanCooldown-- > 0) {
            return;
        }
        rescanCooldown = 40;
        for (int id = wanted < 0 ? GLFW.GLFW_JOYSTICK_1 : wanted; id <= (wanted < 0 ? GLFW.GLFW_JOYSTICK_LAST : wanted); id++) {
            if (GLFW.glfwJoystickPresent(id)) {
                jid = id;
                gamepad = GLFW.glfwJoystickIsGamepad(id);
                String name = gamepad ? GLFW.glfwGetGamepadName(id) : GLFW.glfwGetJoystickName(id);
                deviceName = name != null ? name : "joystick " + id;
                McheliCePort.LOGGER.info("Using joystick {}: {}{}", id, deviceName, gamepad ? " (gamepad)" : "");
                return;
            }
        }
    }

    private static boolean useGamepadLayout() {
        return gamepad && ClientConfig.JOYSTICK_GAMEPAD_LAYOUT.get();
    }

    /** Reads every axis and button into the arrays. */
    private static void read() {
        axisCount = buttonCount = 0;
        if (jid < 0) {
            return;
        }
        if (useGamepadLayout()) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                GLFWGamepadState state = GLFWGamepadState.malloc(stack);
                if (GLFW.glfwGetGamepadState(jid, state)) {
                    FloatBuffer a = state.axes();
                    axisCount = Math.min(a.remaining(), MAX_AXES);
                    for (int i = 0; i < axisCount; i++) {
                        AXES[i] = a.get(a.position() + i);
                    }
                    ByteBuffer b = state.buttons();
                    buttonCount = Math.min(b.remaining(), MAX_BUTTONS);
                    for (int i = 0; i < buttonCount; i++) {
                        BUTTONS[i] = b.get(b.position() + i) == GLFW.GLFW_PRESS;
                    }
                }
            }
            return;
        }
        FloatBuffer a = GLFW.glfwGetJoystickAxes(jid);
        if (a != null) {
            axisCount = Math.min(a.remaining(), MAX_AXES);
            for (int i = 0; i < axisCount; i++) {
                AXES[i] = a.get(a.position() + i);
            }
        }
        ByteBuffer b = GLFW.glfwGetJoystickButtons(jid);
        if (b != null) {
            buttonCount = Math.min(b.remaining(), MAX_BUTTONS);
            for (int i = 0; i < buttonCount; i++) {
                BUTTONS[i] = b.get(b.position() + i) == GLFW.GLFW_PRESS;
            }
        }
    }

    private static float raw(int axis) {
        return axis >= 0 && axis < axisCount ? AXES[axis] : 0.0F;
    }

    /** An axis after the dead zone and curve, inverted when asked; 0 for an axis set to -1 or missing. */
    private static float shaped(int axis, boolean invert) {
        float v = JoystickMapping.shape(raw(axis), ClientConfig.JOYSTICK_DEADZONE.get().floatValue(),
                ClientConfig.JOYSTICK_CURVE.get().floatValue());
        return invert ? -v : v;
    }

    /**
     * Each frame while the pilot flies with the joystick: reads it and returns the control stick
     * position for {@link MouseFlight} (x roll, y pitch, each within the stick's length), or null
     * when the joystick is not in use.
     */
    static double[] stick(double maxLength) {
        if (!active()) {
            return null;
        }
        read();
        double range = ClientConfig.JOYSTICK_STICK_RANGE.get() * maxLength;
        float roll = shaped(ClientConfig.JOYSTICK_ROLL_AXIS.get(), ClientConfig.JOYSTICK_INVERT_ROLL.get());
        // GLFW reports a stick pushed forward as -1; a positive stick y puts the nose down.
        float pitch = -shaped(ClientConfig.JOYSTICK_PITCH_AXIS.get(), ClientConfig.JOYSTICK_INVERT_PITCH.get());
        return new double[] {roll * range, pitch * range};
    }

    /** The joystick's held inputs for this tick; {@code throttle} is the vehicle's synced throttle. */
    public static Digital digital(boolean groundVehicle, double throttle) {
        if (!active() || Minecraft.getInstance().gui.screen() != null) {
            return Digital.NONE;
        }
        float threshold = ClientConfig.JOYSTICK_DIGITAL_THRESHOLD.get().floatValue();
        int yaw = JoystickMapping.digital(shaped(ClientConfig.JOYSTICK_YAW_AXIS.get(), ClientConfig.JOYSTICK_INVERT_YAW.get()), threshold);
        int throttleKeys = throttleKeys(throttle, threshold);
        if (groundVehicle) {
            int drive = JoystickMapping.digital(-shaped(ClientConfig.JOYSTICK_PITCH_AXIS.get(),
                    ClientConfig.JOYSTICK_INVERT_PITCH.get()), threshold);
            int steer = JoystickMapping.digital(shaped(ClientConfig.JOYSTICK_ROLL_AXIS.get(),
                    ClientConfig.JOYSTICK_INVERT_ROLL.get()), threshold);
            if (steer == 0) {
                steer = yaw;
            }
            // A ground vehicle's stick drives; a throttle axis in RATE mode can drive too.
            if (drive == 0 && rateThrottle()) {
                drive = throttleKeys;
            }
            return new Digital(drive > 0, drive < 0, steer < 0, steer > 0);
        }
        return new Digital(throttleKeys > 0, throttleKeys < 0, yaw < 0, yaw > 0);
    }

    private static boolean rateThrottle() {
        return switch (ClientConfig.JOYSTICK_THROTTLE_MODE.get()) {
            case RATE -> true;
            case LEVER -> false;
            case AUTO -> useGamepadLayout();
        };
    }

    private static int throttleKeys(double throttle, float threshold) {
        int axis = ClientConfig.JOYSTICK_THROTTLE_AXIS.get();
        if (axis < 0 || axis >= axisCount) {
            return 0;
        }
        boolean invert = ClientConfig.JOYSTICK_INVERT_THROTTLE.get();
        if (rateThrottle()) {
            return JoystickMapping.digital(-shaped(axis, invert), threshold);
        }
        return JoystickMapping.chaseLever(JoystickMapping.lever(raw(axis), invert), throttle);
    }

    private static List<JoystickMapping.Button> bindings() {
        List<? extends String> config = useGamepadLayout() ? ClientConfig.JOYSTICK_GAMEPAD_BUTTONS.get()
                : ClientConfig.JOYSTICK_BUTTONS.get();
        if (config != parsedFrom) {
            List<JoystickMapping.Button> list = new ArrayList<>();
            for (String s : config) {
                JoystickMapping.Button b = JoystickMapping.parseButton(s);
                if (b != null) {
                    list.add(b);
                }
            }
            bindings = List.copyOf(list);
            parsedFrom = config;
        }
        return bindings;
    }

    private static void pressButtons() {
        Set<InputConstants.Key> down = new HashSet<>();
        for (JoystickMapping.Button b : bindings()) {
            if (b.button() >= buttonCount || !BUTTONS[b.button()]) {
                continue;
            }
            KeyMapping mapping = KeyMapping.get(b.key());
            if (mapping == null || mapping.isUnbound()) {
                if (WARNED.add(b.key())) {
                    McheliCePort.LOGGER.warn("Joystick button {} names {}, which is {}", b.button(), b.key(),
                            mapping == null ? "not a control" : "not bound to a key");
                }
                continue;
            }
            down.add(mapping.getKey());
        }
        for (InputConstants.Key key : down) {
            if (HELD.add(key)) {
                KeyMapping.click(key);
            }
            KeyMapping.set(key, true);
        }
        HELD.removeIf(key -> {
            if (!down.contains(key)) {
                KeyMapping.set(key, false);
                return true;
            }
            return false;
        });
    }

    private static void releaseAll() {
        for (InputConstants.Key key : HELD) {
            KeyMapping.set(key, false);
        }
        HELD.clear();
    }

    private static String describeInputs() {
        StringBuilder s = new StringBuilder(useGamepadLayout() ? "Gamepad" : "Joystick").append(" axes");
        for (int i = 0; i < axisCount; i++) {
            s.append(' ').append(i).append(':').append(String.format(Locale.ROOT, "%+.2f", AXES[i]));
        }
        s.append("  buttons");
        boolean any = false;
        for (int i = 0; i < buttonCount; i++) {
            if (BUTTONS[i]) {
                s.append(' ').append(i);
                any = true;
            }
        }
        return any ? s.toString() : s.append(" -").toString();
    }
}
