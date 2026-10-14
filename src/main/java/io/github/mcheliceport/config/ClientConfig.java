package io.github.mcheliceport.config;

import io.github.mcheliceport.registry.ModTab;
import java.util.EnumMap;
import java.util.Map;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * CLIENT config ({@code config/mcheli_ce_port-client.toml}): per-player settings that never leave the
 * client. Registered by {@link io.github.mcheliceport.client.McheliCePortClient}.
 *
 * <p>Upstream's mouse, HUD and render keys from {@code mcheli.cfg} move here as they are ported
 * (PORTING_PLAN.md section 5.6).
 */
public final class ClientConfig {
    /** Decision D7: {@code /mcheli sendss} screen capture is opt-in. */
    public static final ModConfigSpec.BooleanValue ALLOW_SCREEN_CAPTURE;
    public static final ModConfigSpec.BooleanValue INVERT_MOUSE;
    public static final ModConfigSpec.DoubleValue MOUSE_SENSITIVITY;
    public static final ModConfigSpec.BooleanValue MOUSE_CONTROL_STICK_MODE_HELI;
    public static final ModConfigSpec.BooleanValue MOUSE_CONTROL_FLIGHT_SIM_MODE;
    private static final Map<ModTab, ModConfigSpec.ConfigValue<String>> TAB_ICONS = new EnumMap<>(ModTab.class);
    public static final ModConfigSpec SPEC;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("privacy");
        ALLOW_SCREEN_CAPTURE = builder
                .comment("Allow server operators to capture your screen with /mcheli sendss.",
                        "Requests are refused while this is false, and you get a chat notice whenever a capture happens.",
                        "Has no effect yet: /mcheli sendss is not ported.")
                .define("allowScreenCapture", false);
        builder.pop();

        builder.comment("Flying with the mouse (upstream's lines of the same names in mcheli.cfg).").push("mouse");
        INVERT_MOUSE = builder
                .comment("Invert the pitch of vehicles flown with the mouse. Upstream: InvertMouse.")
                .define("invertMouse", false);
        MOUSE_SENSITIVITY = builder
                .comment("How fast the mouse turns a vehicle. Upstream: MouseSensitivity.")
                .defineInRange("mouseSensitivity", 10.0, 0.0, 1000.0);
        MOUSE_CONTROL_STICK_MODE_HELI = builder
                .comment("The mouse moves a control stick that stays where it is put, instead of turning the helicopter directly.",
                        "Upstream: MouseControlStickModeHeli.")
                .define("mouseControlStickModeHeli", false);
        MOUSE_CONTROL_FLIGHT_SIM_MODE = builder
                .comment("Flight simulator mouse mode for planes and ships. Has no effect yet: planes and ships are not ported.",
                        "Upstream: MouseControlFlightSimMode.")
                .define("mouseControlFlightSimMode", false);
        builder.pop();

        builder.comment("Icons of the creative tabs (upstream's CreativeTabIcon* lines in mcheli.cfg).",
                "Give an item name such as 'ah-64' (an item of this mod) or 'minecraft:iron_block'.",
                "If the item does not exist, the tab shows its first item. Takes effect after a restart.")
                .push("creativeTabIcons");
        for (ModTab tab : ModTab.values()) {
            if (!tab.defaultIcon.isEmpty()) {
                TAB_ICONS.put(tab, builder.define(tab.id + "Icon", tab.defaultIcon));
            }
        }
        builder.pop();

        SPEC = builder.build();
    }

    private ClientConfig() {}

    /** The configured icon item name of a tab, or the default if the config is not loaded (servers). */
    public static String tabIcon(ModTab tab) {
        ModConfigSpec.ConfigValue<String> value = TAB_ICONS.get(tab);
        if (value == null) {
            return "";
        }
        return SPEC.isLoaded() ? value.get() : tab.defaultIcon;
    }
}
