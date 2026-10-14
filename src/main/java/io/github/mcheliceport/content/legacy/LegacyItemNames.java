package io.github.mcheliceport.content.legacy;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Maps Minecraft 1.12 item names with metadata, as content recipes write them ({@code wool},
 * {@code dye, 1}, {@code planks, 2}), to the item IDs of the flattened game ({@code white_wool},
 * {@code red_dye}, {@code birch_planks}).
 *
 * <p>Covers the vanilla items whose 1.12 name or metadata changed when 1.13 flattened IDs, plus later
 * renames. Names that did not change are not listed: {@link #flatten} returns them unchanged and the
 * caller checks that such an item exists. Metadata on other items was a damage value and is
 * ignored, except where it selected what is now item data (a potion's effect, for example): those
 * have no equivalent.
 */
public final class LegacyItemNames {

    /** 1.12's colour order for wool, carpets, stained glass, terracotta, concrete and beds. */
    private static final String[] COLORS = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink",
            "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};
    private static final String[] WOODS = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak"};

    private static final Map<String, String> RENAMED = new HashMap<>();
    private static final Map<String, String[]> VARIANTS = new HashMap<>();
    /** Items whose 1.12 metadata selected something that is now item data (potion type, colour...). */
    private static final Set<String> METADATA_NOW_DATA = Set.of("potion", "splash_potion", "lingering_potion",
            "tipped_arrow", "spawn_egg", "banner", "filled_map", "enchanted_book");

    static {
        String[][] renamed = {
                {"boat", "oak_boat"}, {"reeds", "sugar_cane"}, {"noteblock", "note_block"},
                {"golden_rail", "powered_rail"}, {"web", "cobweb"}, {"deadbush", "dead_bush"},
                {"brick_block", "bricks"}, {"mob_spawner", "spawner"}, {"wooden_door", "oak_door"},
                {"wooden_pressure_plate", "oak_pressure_plate"}, {"wooden_button", "oak_button"},
                {"fence", "oak_fence"}, {"fence_gate", "oak_fence_gate"}, {"trapdoor", "oak_trapdoor"},
                {"pumpkin", "carved_pumpkin"}, {"lit_pumpkin", "jack_o_lantern"}, {"melon_block", "melon"},
                {"melon", "melon_slice"}, {"speckled_melon", "glistering_melon_slice"},
                {"netherbrick", "nether_brick"}, {"nether_brick", "nether_bricks"},
                {"quartz_ore", "nether_quartz_ore"}, {"hardened_clay", "terracotta"}, {"grass", "grass_block"},
                {"grass_path", "dirt_path"}, {"waterlily", "lily_pad"}, {"snow_layer", "snow"},
                {"snow", "snow_block"}, {"chorus_fruit_popped", "popped_chorus_fruit"},
                {"firework_charge", "firework_star"}, {"fireworks", "firework_rocket"},
                {"stone_stairs", "cobblestone_stairs"}, {"magma", "magma_block"},
                {"red_nether_brick", "red_nether_bricks"}, {"slime", "slime_block"},
                {"end_bricks", "end_stone_bricks"}, {"silver_shulker_box", "light_gray_shulker_box"},
                {"silver_glazed_terracotta", "light_gray_glazed_terracotta"}, {"yellow_flower", "dandelion"},
                {"sign", "oak_sign"}, {"lit_redstone_lamp", "redstone_lamp"}, {"lit_furnace", "furnace"},
                {"unpowered_repeater", "repeater"}, {"powered_repeater", "repeater"},
                {"unpowered_comparator", "comparator"}, {"powered_comparator", "comparator"},
                {"unlit_redstone_torch", "redstone_torch"}, {"stone_slab2", "red_sandstone_slab"},
        };
        for (String[] pair : renamed) {
            RENAMED.put(pair[0], pair[1]);
        }
        for (String disc : new String[] {"13", "cat", "blocks", "chirp", "far", "mall", "mellohi", "stal", "strad",
                "ward", "11", "wait"}) {
            RENAMED.put("record_" + disc, "music_disc_" + disc);
        }

        colored("wool", "_wool");
        colored("carpet", "_carpet");
        colored("stained_glass", "_stained_glass");
        colored("stained_glass_pane", "_stained_glass_pane");
        colored("stained_hardened_clay", "_terracotta");
        colored("concrete", "_concrete");
        colored("concrete_powder", "_concrete_powder");
        colored("bed", "_bed");
        VARIANTS.put("dye", new String[] {"ink_sac", "red_dye", "green_dye", "cocoa_beans", "lapis_lazuli",
                "purple_dye", "cyan_dye", "light_gray_dye", "gray_dye", "pink_dye", "lime_dye", "yellow_dye",
                "light_blue_dye", "magenta_dye", "orange_dye", "bone_meal"});
        woods("planks", "_planks", 6);
        woods("sapling", "_sapling", 6);
        woods("wooden_slab", "_slab", 6);
        woods("log", "_log", 4);
        woods("leaves", "_leaves", 4);
        VARIANTS.put("log2", new String[] {"acacia_log", "dark_oak_log"});
        VARIANTS.put("leaves2", new String[] {"acacia_leaves", "dark_oak_leaves"});
        VARIANTS.put("stone", new String[] {"stone", "granite", "polished_granite", "diorite", "polished_diorite",
                "andesite", "polished_andesite"});
        VARIANTS.put("sand", new String[] {"sand", "red_sand"});
        VARIANTS.put("dirt", new String[] {"dirt", "coarse_dirt", "podzol"});
        VARIANTS.put("sandstone", new String[] {"sandstone", "chiseled_sandstone", "cut_sandstone"});
        VARIANTS.put("red_sandstone", new String[] {"red_sandstone", "chiseled_red_sandstone",
                "cut_red_sandstone"});
        VARIANTS.put("stonebrick", new String[] {"stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks",
                "chiseled_stone_bricks"});
        VARIANTS.put("quartz_block", new String[] {"quartz_block", "chiseled_quartz_block", "quartz_pillar"});
        VARIANTS.put("prismarine", new String[] {"prismarine", "prismarine_bricks", "dark_prismarine"});
        VARIANTS.put("stone_slab", new String[] {"smooth_stone_slab", "sandstone_slab", "petrified_oak_slab",
                "cobblestone_slab", "brick_slab", "stone_brick_slab", "nether_brick_slab", "quartz_slab"});
        VARIANTS.put("cobblestone_wall", new String[] {"cobblestone_wall", "mossy_cobblestone_wall"});
        VARIANTS.put("red_flower", new String[] {"poppy", "blue_orchid", "allium", "azure_bluet", "red_tulip",
                "orange_tulip", "white_tulip", "pink_tulip", "oxeye_daisy"});
        VARIANTS.put("double_plant", new String[] {"sunflower", "lilac", "tall_grass", "large_fern", "rose_bush",
                "peony"});
        VARIANTS.put("tallgrass", new String[] {"dead_bush", "short_grass", "fern"});
        VARIANTS.put("coal", new String[] {"coal", "charcoal"});
        VARIANTS.put("fish", new String[] {"cod", "salmon", "tropical_fish", "pufferfish"});
        VARIANTS.put("cooked_fish", new String[] {"cooked_cod", "cooked_salmon"});
        VARIANTS.put("golden_apple", new String[] {"golden_apple", "enchanted_golden_apple"});
        VARIANTS.put("skull", new String[] {"skeleton_skull", "wither_skeleton_skull", "zombie_head", "player_head",
                "creeper_head", "dragon_head"});
        VARIANTS.put("anvil", new String[] {"anvil", "chipped_anvil", "damaged_anvil"});
        VARIANTS.put("sponge", new String[] {"sponge", "wet_sponge"});
        VARIANTS.put("monster_egg", new String[] {"infested_stone", "infested_cobblestone", "infested_stone_bricks",
                "infested_mossy_stone_bricks", "infested_cracked_stone_bricks", "infested_chiseled_stone_bricks"});
    }

    private LegacyItemNames() {
    }

    private static void colored(String name, String suffix) {
        String[] ids = new String[COLORS.length];
        for (int i = 0; i < COLORS.length; i++) {
            ids[i] = COLORS[i] + suffix;
        }
        VARIANTS.put(name, ids);
    }

    private static void woods(String name, String suffix, int count) {
        String[] ids = new String[count];
        for (int i = 0; i < count; i++) {
            ids[i] = WOODS[i] + suffix;
        }
        VARIANTS.put(name, ids);
    }

    /** Whether the 1.12 item (in the minecraft namespace) had variants selected by metadata. */
    public static boolean hasVariants(String name) {
        return VARIANTS.containsKey(stripMinecraft(name));
    }

    /**
     * The flattened item ID ({@code namespace:path}) for a vanilla 1.12 item name and metadata, or
     * null if this name and metadata have no flattened equivalent here. Names outside the
     * {@code minecraft} namespace are returned unchanged.
     */
    public static @Nullable String flatten(String name, int meta) {
        String lower = name.trim().toLowerCase(Locale.ROOT);
        int colon = lower.indexOf(':');
        if (colon >= 0 && !lower.startsWith("minecraft:")) {
            return lower;
        }
        String path = stripMinecraft(lower);
        if (meta != 0 && METADATA_NOW_DATA.contains(path)) {
            return null;
        }
        String[] variants = VARIANTS.get(path);
        if (variants != null) {
            // Logs kept their orientation in the upper metadata bits.
            int index = path.equals("log") || path.equals("log2") || path.equals("leaves") || path.equals("leaves2")
                    ? meta & 3 : meta;
            return index >= 0 && index < variants.length ? "minecraft:" + variants[index] : null;
        }
        return "minecraft:" + RENAMED.getOrDefault(path, path);
    }

    private static String stripMinecraft(String name) {
        return name.startsWith("minecraft:") ? name.substring("minecraft:".length()) : name;
    }
}
