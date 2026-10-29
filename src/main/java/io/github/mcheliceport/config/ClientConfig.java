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
    public static final ModConfigSpec.BooleanValue MOUSE_CONTROL_STICK_MODE_PLANE;
    public static final ModConfigSpec.BooleanValue MOUSE_CONTROL_FLIGHT_SIM_MODE;
    public static final ModConfigSpec.BooleanValue TEST_MODE;
    public static final ModConfigSpec.BooleanValue SMOOTH_SHADING;
    public static final ModConfigSpec.BooleanValue TILT_RIDERS;
    public static final ModConfigSpec.BooleanValue ON_DEMAND_MODELS;
    public static final ModConfigSpec.IntValue ON_DEMAND_MODEL_LIFETIME_SECONDS;
    public static final ModConfigSpec.BooleanValue HUD_IN_THIRD_PERSON;
    public static final ModConfigSpec.BooleanValue HIDE_KEY_HINTS;
    public static final ModConfigSpec.BooleanValue LIGHT_WEAPON_AUTO_FIRE;
    public static final ModConfigSpec.BooleanValue HUD_AUTO_SCALE;
    public static final ModConfigSpec.ConfigValue<String> HIT_MARK_COLOR;
    public static final ModConfigSpec.BooleanValue DEFAULT_EXPLOSION_PARTICLE;
    public enum EffectStyle { CLASSIC, POLISHED }
    public enum EffectBudget { LOW, BALANCED, HIGH }
    public static final ModConfigSpec.EnumValue<EffectStyle> EFFECT_STYLE;
    public static final ModConfigSpec.EnumValue<EffectBudget> EFFECT_BUDGET;
    public static final ModConfigSpec.BooleanValue ADAPTIVE_EFFECTS;
    public static final ModConfigSpec.BooleanValue MODERN_EXPLOSIONS;
    public static final ModConfigSpec.BooleanValue BLAST_MARKS;
    public static final ModConfigSpec.BooleanValue VEHICLE_TRACKS;
    public static final ModConfigSpec.BooleanValue VEHICLE_SURFACE_SHADE;
    public static final ModConfigSpec.BooleanValue VEHICLE_CONTACT_SHADOWS;
    public static final ModConfigSpec.BooleanValue BLAST_DUST_RING;
    public static final ModConfigSpec.BooleanValue ENGINE_HEAT_HAZE;
    public static final ModConfigSpec.BooleanValue ROTOR_WASH_DEBRIS;
    public static final ModConfigSpec.BooleanValue ENGINE_STARTUP_EFFECTS;
    public static final ModConfigSpec.BooleanValue AIRCRAFT_CONDENSATION;
    public static final ModConfigSpec.BooleanValue COCKPIT_FEEDBACK;
    public static final ModConfigSpec.BooleanValue SUPERSONIC_EFFECTS;
    public static final ModConfigSpec.BooleanValue FLYBY_AUDIO;
    public static final ModConfigSpec.BooleanValue LANDING_FEEDBACK;
    public static final ModConfigSpec.BooleanValue NIGHT_FLIGHT_LIGHTING;
    public static final ModConfigSpec.BooleanValue WEAPON_MECHANISMS;
    public static final ModConfigSpec.BooleanValue AIRCRAFT_CUSTOMIZATION;
    public static final ModConfigSpec.BooleanValue VEHICLE_BLOOD;
    public static final ModConfigSpec.BooleanValue VEHICLE_DESTRUCTION_SEQUENCES;
    public static final ModConfigSpec.BooleanValue VEHICLE_DAMAGE_DETAILS;
    public static final ModConfigSpec.BooleanValue WEAPON_HEAT;
    public static final ModConfigSpec.BooleanValue VEHICLE_SMALL_MOTION;
    public static final ModConfigSpec.BooleanValue VEHICLE_SURFACE_BUILDUP;
    public static final ModConfigSpec.BooleanValue VEHICLE_WEATHER;
    public static final ModConfigSpec.BooleanValue VEHICLE_WATER_DETAILS;
    public static final ModConfigSpec.BooleanValue WEAPON_GROUND_EFFECTS;
    public static final ModConfigSpec.IntValue EFFECT_TARGET_FPS;
    public static final ModConfigSpec.IntValue ALIVE_TIME_OF_CARTRIDGE;
    public static final ModConfigSpec.EnumValue<EffectQuality> EFFECT_QUALITY;
    public static final ModConfigSpec.BooleanValue GPU_MODEL_BUFFERS;
    public static final ModConfigSpec.BooleanValue SWITCH_WEAPON_WITH_MOUSE_WHEEL;
    public static final ModConfigSpec.BooleanValue DISPLAY_ENTITY_MARKER;
    public static final ModConfigSpec.DoubleValue ENTITY_MARKER_SIZE;
    public static final ModConfigSpec.DoubleValue BLOCK_MARKER_SIZE;
    private static final Map<ModTab, ModConfigSpec.ConfigValue<String>> TAB_ICONS = new EnumMap<>(ModTab.class);
    public static final ModConfigSpec SPEC;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("privacy");
        ALLOW_SCREEN_CAPTURE = builder
                .comment("Allow server operators to capture your screen with /mcheli sendss.",
                        "Requests are refused while this is false, and you get a chat notice whenever a capture happens.")
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
                .comment("Flight simulator mouse mode for planes (ships follow in M6).",
                        "Upstream: MouseControlFlightSimMode.")
                .define("mouseControlFlightSimMode", false);
        MOUSE_CONTROL_STICK_MODE_PLANE = builder
                .comment("Persistent mouse stick for planes. Upstream: MouseControlStickModePlane.")
                .define("mouseControlStickModePlane", false);
        SWITCH_WEAPON_WITH_MOUSE_WHEEL = builder
                .comment("The mouse wheel switches weapons while riding a vehicle. Upstream: SwitchWeaponWithMouseWheel.")
                .define("switchWeaponWithMouseWheel", true);
        builder.pop();

        builder.comment("Drawing vehicles (upstream's lines of the same names in mcheli.cfg).").push("render");
        SMOOTH_SHADING = builder
                .comment("Smooth the shading of models whose content asks for it. Upstream: SmoothShading.")
                .define("smoothShading", true);
        TILT_RIDERS = builder
                .comment("Draw the crew tilted with the pitch and roll of their vehicle. Upstream: EnableModEntityRender.")
                .define("tiltRiders", true);
        ON_DEMAND_MODELS = builder
                .comment("Load a vehicle's model on a background thread the first time the vehicle is seen, instead of loading",
                        "every model while resources load. Upstream: ExperimentalAsyncOnDemandModelLoading.")
                .define("onDemandModels", true);
        ON_DEMAND_MODEL_LIFETIME_SECONDS = builder
                .comment("Unload a model loaded on demand after this many seconds without being drawn; -1 keeps it.",
                        "Upstream: ExperimentalOnDemandModelLifetimeSeconds.")
                .defineInRange("onDemandModelLifetimeSeconds", 720, -1, 86400);
        GPU_MODEL_BUFFERS = builder
                .comment("Keep vehicle models on the graphics card and draw them from there, instead of sending every",
                        "triangle again each frame. Turn off if vehicles look wrong with your graphics driver. Not in upstream.")
                .define("gpuModelBuffers", true);
        builder.pop();

        builder.comment("The vehicle HUD (upstream's lines of the same names in mcheli.cfg).").push("hud");
        HUD_IN_THIRD_PERSON = builder
                .comment("Show the HUD in third person too. Upstream: DisplayHUDThirdPerson.")
                .define("displayInThirdPerson", false);
        HIDE_KEY_HINTS = builder
                .comment("Hide the key hints next to the HUD. Upstream: HideKeybind.")
                .define("hideKeyHints", false);
        LIGHT_WEAPON_AUTO_FIRE = builder
                .comment("Fire the hand-held Stinger and Javelin as soon as they lock on. Upstream: LWeaponAutoFire.")
                .define("lightWeaponAutoFire", false);
        HUD_AUTO_SCALE = builder
                .comment("Scale HUDs to the screen instead of drawing them at a fixed size. Upstream: ExperimentalAutoScaleAircraftGui.")
                .define("autoScale", false);
        HIT_MARK_COLOR = builder
                .comment("Colour of the hit marker as 'alpha, red, green, blue' (0 to 255 each). Upstream: HitMarkColor.")
                .define("hitMarkColor", "255, 255, 0, 0");
        DISPLAY_ENTITY_MARKER = builder
                .comment("Draw markers over teammates and spotted entities. Upstream: DisplayEntityMarker.")
                .define("displayEntityMarker", true);
        ENTITY_MARKER_SIZE = builder
                .comment("Size of the entity markers; 0 hides them. Upstream: EntityMarkerSize.")
                .defineInRange("entityMarkerSize", 10.0, 0.0, 100.0);
        BLOCK_MARKER_SIZE = builder
                .comment("Size of the mark point marker. Upstream: BlockMarkerSize.")
                .defineInRange("blockMarkerSize", 10.0, 0.0, 100.0);
        builder.pop();

        builder.comment("Weapon effects (upstream's lines of the same names in mcheli.cfg).").push("effects");
        DEFAULT_EXPLOSION_PARTICLE = builder
                .comment("Draw McHeli's explosions with vanilla's explosion particles. Upstream: DefaultExplosionParticle.")
                .define("defaultExplosionParticle", false);
        MODERN_EXPLOSIONS = builder.comment("Modern volumetric-looking explosion art and layered billows; client-only, opt-in.")
                .define("modernExplosions", false);
        EFFECT_STYLE = builder
                .comment("POLISHED adds shaded pixel smoke, warm flashes and flare glow. CLASSIC uses upstream sprites.",
                        "Both styles use Minecraft's supported Vulkan/OpenGL rendering pipelines.")
                .defineEnum("style", EffectStyle.POLISHED);
        EFFECT_BUDGET = builder
                .comment("Custom effect/flash budgets: LOW (96 blocks, 384 effects / 48 flashes), BALANCED (192 blocks, 1536 / 96),",
                        "HIGH (256 blocks, 3072 / 192). Also respects Minecraft's particle setting.",
                        "LOW disables flare halos. McHeli block dust shares the smoke budget; gameplay is unchanged.")
                .defineEnum("quality", EffectBudget.BALANCED);
        BLAST_MARKS = builder
                .comment("Weapon-scaled soot and disturbed-ground textures on surviving block surfaces; client-only and bounded.")
                .define("blastMarks", true);
        VEHICLE_CONTACT_SHADOWS = builder.comment("Size nearby vanilla vehicle shadows from wheel/body footprint; bounded by quality.")
                .define("vehicleContactShadows", true);
        VEHICLE_SURFACE_SHADE = builder.comment("Subtle model-space crease shadows and edge highlights on enhanced vehicles.")
                .define("vehicleSurfaceShade", true);
        VEHICLE_TRACKS = builder.comment("Temporary tank tread/tire imprints on surviving terrain; client-only and bounded.")
                .define("vehicleTracks", true);
        BLAST_DUST_RING = builder.comment("Broken ground-hugging dust fronts on substantial ground explosions; Low/load shed this layer.")
                .define("blastDustRing", true);
        ENGINE_HEAT_HAZE = builder.comment("Local exhaust refraction using one shared scene copy; off on Low or adaptive pressure.")
                .define("engineHeatHaze", true);
        ROTOR_WASH_DEBRIS = builder.comment("Loose grass, sand, snow and water droplets in enhanced rotor wash.")
                .define("rotorWashDebris", true);
        ENGINE_STARTUP_EFFECTS = builder.comment("Enhanced engine startup puffs, spooling turbine whine and gradual heat buildup.")
                .define("engineStartupEffects", true);
        AIRCRAFT_CONDENSATION = builder.comment("Wingtip vapor in suitable wet turns and high-altitude jet contrails. Disable to reduce optional aircraft detail.")
                .define("aircraftCondensation",true);
        COCKPIT_FEEDBACK = builder.comment("Pilot-only cockpit annunciators, restrained instrument vibration and closed-canopy rain. No camera or aiming shake.")
                .define("cockpitFeedback",true);
        SUPERSONIC_EFFECTS = builder.comment("Brief wet-air condensation and delayed flypast booms on explicitly supported aircraft. Cosmetic game-speed mapping; no flight changes.")
                .define("supersonicEffects",true);
        FLYBY_AUDIO = builder.comment("Smooth directional vehicle Doppler and bounded conventional rocket/cannon near-miss cues. NTM weapons keep their own audio.")
                .define("flybyAudio",true);
        LANDING_FEEDBACK = builder.comment("Cosmetic compression of supported deployed primary gear, surface-colored touchdown puffs and restrained hard-contact sparks. No handling changes.")
                .define("landingFeedback",true);
        NIGHT_FLIGHT_LIGHTING = builder.comment("Authored navigation lamps and strobes, restrained cockpit illumination and terrain-connected landing lights. Reuses existing render pipelines; no world light-engine updates.")
                .define("nightFlightLighting",true);
        AIRCRAFT_CUSTOMIZATION = builder.comment("Show supported intact-aircraft identification marks and restrained cosmetic wear.").define("aircraftCustomization",true);
        WEAPON_MECHANISMS = builder.comment("Smoother conventional barrel/recoil and bay presentation, with sized standard casings that bounce and settle. Existing weapon timing, custom models and ejection settings remain authoritative.")
                .define("weaponMechanisms",true);
        VEHICLE_WATER_DETAILS = builder.comment("Bounded V wakes, shallow-water spray and detached-panel splashes.").define("vehicleWaterDetails",true);
        VEHICLE_SMALL_MOTION = builder.comment("Flexible supported antennas, authored nozzle-cover vibration and hanging exposed cables.").define("vehicleSmallMotion",true);
        VEHICLE_SURFACE_BUILDUP = builder.comment("Mud and dust on lower panels, washed by water and rain; soot near exhausts.").define("vehicleSurfaceBuildup",true);
        VEHICLE_WEATHER = builder.comment("Rain darkens paint; cold precipitation collects on parked vehicles and sheds with movement.").define("vehicleWeather",true);
        WEAPON_HEAT = builder.comment("Cosmetic barrel discoloration, shared heat haze and cooling smoke after sustained gunfire.").define("weaponHeat",true);
        VEHICLE_BLOOD = builder.comment("Restrained blood on confirmed MC Heli vehicle kills of tagged mobs. Excludes NTM weapons/payloads and NTM mobs.")
                .define("vehicleBlood", true);
        VEHICLE_DESTRUCTION_SEQUENCES = builder.comment("Cosmetic staged turret/track/cover/airframe breakup, rotor strikes and smoldering interiors. Supported models only.").define("vehicleDestructionSequences",true);
        VEHICLE_DAMAGE_DETAILS = builder.comment("Cosmetic impact/blast scars, progressive glass cracks, fluid leaks, damaged lamps, loose panels and cooling wrecks.")
                .define("vehicleDamageDetails", true);
        WEAPON_GROUND_EFFECTS = builder.comment("Brief muzzle illumination and ground dust beneath large guns.")
                .define("weaponGroundEffects", true);
        ADAPTIVE_EFFECTS = builder
                .comment("Reduce cosmetic smoke, dust and optional glows during sustained slow frames; restore detail gradually.",
                        "Respects lower FPS caps and VSync. Weapon flash cores keep their reserved budget.")
                .define("adaptiveDetail", true);
        EFFECT_TARGET_FPS = builder
                .comment("Frame-rate goal for adaptive effects. This cannot guarantee FPS when terrain or other mods dominate.")
                .defineInRange("targetFps", 60, 30, 240);
        ALIVE_TIME_OF_CARTRIDGE = builder
                .comment("Ticks an ejected cartridge stays on the ground. Upstream: AliveTimeOfCartridge.")
                .defineInRange("aliveTimeOfCartridge", 200, 0, 1000);
        EFFECT_QUALITY = builder
                .comment("How explosions, smoke, flashes, flares, tracers, splashes and downwash look. CLASSIC is",
                        "upstream's look; ENHANCED adds smooth fading, fireballs and glows. Not in upstream.")
                .defineEnum("effectQuality", EffectQuality.ENHANCED);
        builder.pop();

        builder.push("debug");
        TEST_MODE = builder
                .comment("Draw vehicle hit boxes and the HUD's variables. Upstream: TestMode.")
                .define("testMode", false);
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

    /** A config value, or its default while the config is not loaded (tests, servers). */
    public static <T> T get(ModConfigSpec.ConfigValue<T> value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    /**
     * The hit marker colour as ARGB. Upstream {@code MCH_Config} read {@code HitMarkColor} as four
     * 0..255 values, alpha first; a malformed value gives upstream's default.
     */
    public static int hitMarkColor() {
        String[] split = get(HIT_MARK_COLOR).split("\\s*,\\s*");
        if (split.length == 4) {
            try {
                int argb = 0;
                for (String part : split) {
                    argb = argb << 8 | Math.clamp(Integer.parseInt(part.trim()), 0, 255);
                }
                return argb;
            } catch (NumberFormatException ignored) {
                // fall through to the default
            }
        }
        return 0xFFFF0000;
    }

    /** The configured icon item name of a tab, or the default if the config is not loaded (servers). */
    public static String tabIcon(ModTab tab) {
        ModConfigSpec.ConfigValue<String> value = TAB_ICONS.get(tab);
        if (value == null) {
            return "";
        }
        return SPEC.isLoaded() ? value.get() : tab.defaultIcon;
    }
}
