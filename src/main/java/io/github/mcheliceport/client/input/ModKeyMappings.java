package io.github.mcheliceport.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.seat.SeatEntity;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * The vehicle keys ported so far, with upstream's default keys (upstream's
 * {@code Key*} lines in mcheli.cfg). They only act while the player rides a McHeli vehicle, so
 * they share keys with vanilla controls without conflicts.
 */
public final class ModKeyMappings {

    public static final KeyMapping.Category CATEGORY =
            new KeyMapping.Category(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "vehicles"));

    /** Active while the player rides a McHeli vehicle, in any seat, or sits at a laser designator. */
    public static final IKeyConflictContext RIDING = new IKeyConflictContext() {
        @Override
        public boolean isActive() {
            Minecraft mc = Minecraft.getInstance();
            return mc.gui.screen() == null && mc.player != null && (isRidingAircraft(mc.player.getVehicle())
                    || mc.player.getVehicle() instanceof io.github.mcheliceport.entity.gltd.GltdEntity);
        }

        @Override
        public boolean conflicts(IKeyConflictContext other) {
            // KeyModifier.NONE suppresses keys while Ctrl/Shift/Alt is held unless this context
            // conflicts with IN_GAME. Ctrl itself and Ctrl+C/I/F must all remain usable while riding.
            return other == this || other == KeyConflictContext.IN_GAME;
        }
    };

    /**
     * Upstream's multiplayer keys work outside vehicles too, with no screen open. Upstream kept them
     * off singleplayer (unless the debug log was on); here they work there too.
     */
    public static final IKeyConflictContext MULTIPLAY = new IKeyConflictContext() {
        @Override
        public boolean isActive() {
            Minecraft mc = Minecraft.getInstance();
            return mc.gui.screen() == null && mc.player != null;
        }

        @Override
        public boolean conflicts(IKeyConflictContext other) {
            return other == this || other == KeyConflictContext.IN_GAME;
        }
    };

    public static final KeyMapping UP = key("up", GLFW.GLFW_KEY_W);
    public static final KeyMapping DOWN = key("down", GLFW.GLFW_KEY_S);
    public static final KeyMapping RIGHT = key("right", GLFW.GLFW_KEY_D);
    public static final KeyMapping LEFT = key("left", GLFW.GLFW_KEY_A);
    public static final KeyMapping SWITCH_GUNNER = key("switch_gunner", GLFW.GLFW_KEY_H);
    public static final KeyMapping SWITCH_HOVERING = key("switch_hovering", GLFW.GLFW_KEY_SPACE);
    public static final KeyMapping ZOOM = key("zoom", GLFW.GLFW_KEY_Z);
    public static final KeyMapping UNMOUNT = key("unmount", GLFW.GLFW_KEY_Y);
    public static final KeyMapping EXTRA = key("extra", GLFW.GLFW_KEY_F);
    public static final KeyMapping FREE_LOOK = key("free_look", GLFW.GLFW_KEY_LEFT_CONTROL);
    public static final KeyMapping GUI = key("gui", GLFW.GLFW_KEY_I);
    public static final KeyMapping GEAR_UP_DOWN = key("gear_up_down", GLFW.GLFW_KEY_B);
    public static final KeyMapping PUT_TO_RACK = key("put_to_rack", GLFW.GLFW_KEY_J);
    public static final KeyMapping DOWN_FROM_RACK = key("down_from_rack", GLFW.GLFW_KEY_U);
    public static final KeyMapping ROPES = key("ropes", GLFW.GLFW_KEY_K);
    public static final KeyMapping SEARCH_LIGHT = key("search_light", GLFW.GLFW_KEY_L);
    /** Upstream {@code KeyRadarSwitch}. */
    public static final KeyMapping RADAR_SWITCH = key("radar_switch", GLFW.GLFW_KEY_GRAVE_ACCENT);
    /** Upstream {@code KeyOpenGPSPanel}; upstream's K is the port's ropes key, so N here. */
    public static final KeyMapping OPEN_GPS_PANEL = key("open_gps_panel", GLFW.GLFW_KEY_N);

    public static final KeyMapping CAMERA_MODE = key("camera_mode", GLFW.GLFW_KEY_C);
    public static final KeyMapping CAMERA_DISTANCE_UP = key("camera_distance_up", GLFW.GLFW_KEY_PAGE_UP);
    public static final KeyMapping CAMERA_DISTANCE_DOWN = key("camera_distance_down", GLFW.GLFW_KEY_PAGE_DOWN);

    /** Upstream {@code KeyScoreboard}: held, it shows the team lists. */
    public static final KeyMapping SCOREBOARD = new KeyMapping("key." + McheliCePort.MOD_ID + ".scoreboard",
            MULTIPLAY, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_L, CATEGORY);
    /** Upstream {@code KeyMultiplayManager}: opens the multiplay manager. */
    public static final KeyMapping MULTIPLAY_MANAGER = new KeyMapping(
            "key." + McheliCePort.MOD_ID + ".multiplay_manager", MULTIPLAY, InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_M, CATEGORY);

    /** Upstream's fixed {@code KeyUseWeapon} (-100, the left mouse button); bindable here. */
    public static final KeyMapping USE_WEAPON = mouse("use_weapon", GLFW.GLFW_MOUSE_BUTTON_LEFT);
    /** Upstream {@code KeySwitchWeapon1} (-98, the middle mouse button). */
    public static final KeyMapping SWITCH_WEAPON_1 = mouse("switch_weapon_1", GLFW.GLFW_MOUSE_BUTTON_MIDDLE);
    public static final KeyMapping SWITCH_WEAPON_2 = key("switch_weapon_2", GLFW.GLFW_KEY_G);
    public static final KeyMapping SWITCH_WEAPON_MODE = key("switch_weapon_mode", GLFW.GLFW_KEY_X);
    public static final KeyMapping RELOAD_WEAPON = key("reload_weapon", GLFW.GLFW_KEY_R);
    public static final KeyMapping FLARE = key("flare", GLFW.GLFW_KEY_V);
    public static final KeyMapping CHAFF = key("chaff", GLFW.GLFW_KEY_V);
    public static final KeyMapping APS = key("aps", GLFW.GLFW_KEY_V);
    public static final KeyMapping ECM_JAMMER = key("ecm_jammer", GLFW.GLFW_KEY_V);
    /** Upstream {@code KeyAirburstDistReset} (-99, the right mouse button). */
    public static final KeyMapping AIRBURST_RESET = mouse("airburst_reset", GLFW.GLFW_MOUSE_BUTTON_RIGHT);

    /** M10b: a commander marks what they look at for the crew. */
    public static final KeyMapping MARK_TARGET = key("mark_target", GLFW.GLFW_KEY_M);

    /** Upstream's wingman category, with its one key. */
    public static final KeyMapping.Category WINGMAN_CATEGORY =
            new KeyMapping.Category(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "wingman"));
    /** Upstream {@code WingmanKeyHandler.KEY_PANEL}: opens the wingman panel while piloting. */
    public static final KeyMapping WINGMAN_PANEL = new KeyMapping("key." + McheliCePort.MOD_ID + ".wingman_panel",
            RIDING, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_KP_0, WINGMAN_CATEGORY);

    public static final List<KeyMapping> ALL = List.of(UP, DOWN, RIGHT, LEFT, SWITCH_GUNNER, SWITCH_HOVERING, ZOOM,
            UNMOUNT, EXTRA, FREE_LOOK, GUI, GEAR_UP_DOWN, CAMERA_MODE, CAMERA_DISTANCE_UP, CAMERA_DISTANCE_DOWN,
            USE_WEAPON, SWITCH_WEAPON_1, SWITCH_WEAPON_2, SWITCH_WEAPON_MODE, RELOAD_WEAPON, FLARE, CHAFF, APS, ECM_JAMMER,
            AIRBURST_RESET, MARK_TARGET, PUT_TO_RACK, DOWN_FROM_RACK, ROPES, SEARCH_LIGHT, RADAR_SWITCH, OPEN_GPS_PANEL, SCOREBOARD,
            MULTIPLAY_MANAGER, WINGMAN_PANEL);

    private ModKeyMappings() {
    }

    private static KeyMapping key(String name, int keyCode) {
        return new KeyMapping("key." + McheliCePort.MOD_ID + "." + name, RIDING, InputConstants.Type.KEYSYM, keyCode,
                CATEGORY);
    }

    private static KeyMapping mouse(String name, int button) {
        return new KeyMapping("key." + McheliCePort.MOD_ID + "." + name, RIDING, InputConstants.Type.MOUSE, button,
                CATEGORY);
    }

    public static boolean isRidingAircraft(Entity vehicle) {
        return vehicle instanceof AircraftEntity || vehicle instanceof io.github.mcheliceport.entity.uav.UavStationEntity
                || vehicle instanceof SeatEntity seat && seat.getParent() != null;
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.registerCategory(WINGMAN_CATEGORY);
        ALL.forEach(event::register);
    }

    /** Whether the free-look key is held down right now (upstream {@code Keyboard.isKeyDown(KeyFreeLook)}). */
    public static boolean isFreeLookHeld() {
        return FREE_LOOK.isDown();
    }
}
