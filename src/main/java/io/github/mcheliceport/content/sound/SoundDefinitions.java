package io.github.mcheliceport.content.sound;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The sound events McHeli content uses, and which sound files each one plays.
 *
 * <p>Ported from upstream {@code com.norwood.mcheli.sound.SoundRegistry} and
 * {@code ClientSoundRegistry}. Upstream had no {@code sounds.json}: it registered sound events
 * while content loaded and patched the client's sound handler. The port collects the same
 * definitions here, on both sides, and turns them into a generated {@code sounds.json} on the
 * client. Event names are paths in the {@code mcheli} namespace.
 *
 * <p>Differences from upstream:
 * <ul>
 *   <li>An event defined with an explicit sound list (the YAML map form) keeps that list. Upstream
 *       replaced it with the default single-file definition when a weapon's {@code validate()} ran,
 *       so explicit lists never worked for weapon sounds.</li>
 *   <li>Explicit variants clamp {@code Volume} and {@code Pitch} to 0..1. Upstream swapped the
 *       bounds, which forced both to 1.</li>
 *   <li>{@code iron_curtain}, {@code aps_activate} and {@code aps_deactivate} get definitions;
 *       upstream registered the events without any sound to play.</li>
 * </ul>
 */
public final class SoundDefinitions {

    /** Suffix of the fallback file an event plays when no file has its own name. */
    public static final String SND = "_snd";
    private static final String NAMESPACE_PREFIX = "mcheli:";

    private static final Map<String, Definition> DEFINITIONS = new LinkedHashMap<>();

    static {
        reset();
    }

    private SoundDefinitions() {
    }

    /** How an event picks its sound files. */
    public sealed interface Definition permits Auto, Explicit {
    }

    /**
     * Upstream's default for events named in content: play {@code <name>.ogg}, or
     * {@code <name>_snd.ogg} if there is no such file and the name does not already end in
     * {@code _snd}. The file check happens when {@code sounds.json} is generated.
     */
    public record Auto(String name) implements Definition {

        /** The file to play, given a check for whether a sound file exists. */
        public String resolveFile(Predicate<String> fileExists) {
            if (this.name.endsWith(SND) || fileExists.test(this.name)) {
                return this.name;
            }
            return this.name + SND;
        }
    }

    /** A fixed list of sound files to choose from. */
    public record Explicit(List<Variant> variants) implements Definition {
    }

    /**
     * One sound file of an event.
     *
     * @param location sound file as {@code namespace:path}, without {@code sounds/} and {@code .ogg}
     * @param weight   relative chance of this variant
     * @param stream   whether the file is streamed, as long sounds should be
     */
    public record Variant(String location, float volume, float pitch, int weight, boolean stream) {

        /** A McHeli sound file with default settings. */
        public static Variant of(String name) {
            return new Variant(NAMESPACE_PREFIX + name, 1f, 1f, 1, false);
        }

        /**
         * A sound file named in a YAML sound list. As with 1.12's resource locations, a name
         * without a namespace is in the {@code minecraft} namespace.
         */
        static Variant fromFileName(String name, float volume, float pitch, int weight, boolean stream) {
            String lower = name.trim().toLowerCase(Locale.ROOT);
            return new Variant(lower.indexOf(':') >= 0 ? lower : "minecraft:" + lower, volume, pitch, weight,
                    stream);
        }
    }

    /** Forgets all content-defined events, keeping only the built-in ones. */
    public static synchronized void reset() {
        DEFINITIONS.clear();
        for (String name : new String[] {
                "a-10_snd", "a10gau8_snd", "alert", "boat", "bomb_snd", "cannon_1_snd", "cannon_2_snd",
                "cannon_3_snd", "cannon_4_snd", "chain", "chain_ct", "eject_seat", "fim92_reload", "fim92_snd",
                "gau-8_snd", "gltd", "gun_h1_snd", "gun_h2_snd", "gun_h3_snd", "gun_h4_snd", "gun_h5_snd",
                "gun_h6_snd", "gun_h7_snd", "gun_l1_snd", "gun_l2_snd", "gun_l3_snd", "gun_l4_snd", "hawk_snd",
                "heli", "helidmg", "heli_k", "hit", "locked", "lockon", "mbtrun", "mbt_run", "missile_1_snd",
                "missile_2_snd", "missile_3_snd", "missile_4_snd", "mk19_l_snd", "mk19_r_snd", "mw-1_snd", "ng",
                "pi", "plane", "plane_cc", "plane_cv", "plastic_bomb_snd", "prop", "r44_heli", "radicon_heli",
                "rocket", "rocket_snd", "rr_griffon", "rr_merlin", "sa-2_snd", "smoke_snd", "tank", "tank_gte",
                "turboprop", "vehicle_drive", "vehicle_run", "xm301_snd", "zoom", "iron_curtain", "aps_activate",
                "aps_deactivate", "turbine_spool"}) {
            DEFINITIONS.put(name, new Explicit(List.of(Variant.of(name))));
        }
        DEFINITIONS.put("wrench",
                new Explicit(List.of(Variant.of("wrench1"), Variant.of("wrench2"), Variant.of("wrench3"))));
    }

    /**
     * Registers an event named in content, such as {@code cannon_1_snd} or {@code mcheli:heli}, and
     * returns its name. Upstream {@code SoundRegistry#parseSound(String)}.
     */
    public static synchronized String parseSound(String loc) {
        String name = toEventName(loc);
        if (!(DEFINITIONS.get(name) instanceof Explicit)) {
            DEFINITIONS.put(name, new Auto(name));
        }
        return name;
    }

    /**
     * Registers an event from the YAML map form ({@code Name} plus {@code Sounds}, a file name or a
     * list of file names and variant maps) and returns its name. Upstream
     * {@code SoundRegistry#parseSound(Map)}.
     */
    @SuppressWarnings("unchecked")
    public static synchronized String parseSound(Map<String, Object> ymlEntry) {
        Object nameValue = ymlEntry.get("Name");
        if (!(nameValue instanceof String rawName)) {
            throw new IllegalArgumentException("Sound definition needs a Name");
        }
        String name = toEventName(rawName);
        List<Variant> variants = new ArrayList<>();
        Object sounds = ymlEntry.get("Sounds");
        if (sounds instanceof String s) {
            variants.add(Variant.fromFileName(s, 1f, 1f, 1, false));
        } else if (sounds instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof String s) {
                    variants.add(Variant.fromFileName(s, 1f, 1f, 1, false));
                } else if (o instanceof Map<?, ?> map) {
                    variants.add(parseVariant((Map<String, Object>) map));
                }
            }
        }
        DEFINITIONS.put(name, new Explicit(List.copyOf(variants)));
        return name;
    }

    private static Variant parseVariant(Map<String, Object> map) {
        String name = "";
        float volume = 1f;
        float pitch = 1f;
        int weight = 1;
        boolean streaming = false;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            switch (entry.getKey()) {
                case "Name" -> name = (String) entry.getValue();
                case "Vol", "Volume" -> volume = clamp01(((Number) entry.getValue()).floatValue());
                case "Pitch" -> pitch = clamp01(((Number) entry.getValue()).floatValue());
                case "Weight" -> weight = ((Number) entry.getValue()).intValue();
                case "isStreamed", "Stream" -> streaming = (Boolean) entry.getValue();
                default -> {
                }
            }
        }
        return Variant.fromFileName(name, volume, pitch, weight, streaming);
    }

    private static float clamp01(float f) {
        return Math.max(0f, Math.min(1f, f));
    }

    /** Strips the {@code mcheli:} prefix and lowercases, as upstream's 1.12 resource locations did. */
    public static String toEventName(String loc) {
        String lower = loc.trim().toLowerCase(Locale.ROOT);
        return lower.startsWith(NAMESPACE_PREFIX) ? lower.substring(NAMESPACE_PREFIX.length()) : lower;
    }

    /** All registered events and their definitions, in registration order. */
    public static synchronized Map<String, Definition> snapshot() {
        return new LinkedHashMap<>(DEFINITIONS);
    }
}
