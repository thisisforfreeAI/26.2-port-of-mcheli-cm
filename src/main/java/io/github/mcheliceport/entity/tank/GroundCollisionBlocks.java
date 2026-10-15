package io.github.mcheliceport.entity.tank;

import io.github.mcheliceport.config.CommonConfig;
import io.github.mcheliceport.weapon.BlockList;
import java.util.ArrayList;
import java.util.List;

/** Modern block/tag equivalents of upstream's car/tank block and Material lists. */
public final class GroundCollisionBlocks {
    private GroundCollisionBlocks() { }

    // 26.2 has no block Material. Flatten the old categories into editable block/tag selectors.
    public static final List<String> CAR = List.of(
            "#c:glass_panes", "#minecraft:tall_flowers", "minecraft:cactus", "minecraft:cake",
            "minecraft:pumpkin", "minecraft:carved_pumpkin", "minecraft:jack_o_lantern", "minecraft:melon",
            "#minecraft:leaves", "minecraft:vine", "#minecraft:flowers", "#minecraft:saplings",
            "minecraft:short_grass", "minecraft:tall_grass", "minecraft:fern", "minecraft:large_fern",
            "minecraft:dead_bush", "minecraft:brown_mushroom", "minecraft:red_mushroom", "minecraft:wheat",
            "minecraft:carrots", "minecraft:potatoes", "minecraft:beetroots", "minecraft:nether_wart",
            "minecraft:sugar_cane", "minecraft:pumpkin_stem", "minecraft:melon_stem",
            "minecraft:attached_pumpkin_stem", "minecraft:attached_melon_stem", "minecraft:cocoa",
            "minecraft:lily_pad", "minecraft:chorus_plant", "minecraft:chorus_flower");
    public static final List<String> TANK;
    static {
        List<String> selectors = new ArrayList<>(CAR);
        selectors.addAll(List.of("minecraft:nether_brick_fence", "#minecraft:wool_carpets",
                "#c:glass_blocks", "minecraft:glowstone", "minecraft:sea_lantern", "minecraft:beacon",
                "#minecraft:rails", "#minecraft:buttons", "minecraft:lever", "minecraft:tripwire",
                "minecraft:tripwire_hook", "minecraft:redstone_wire", "minecraft:repeater", "minecraft:comparator",
                "minecraft:torch", "minecraft:wall_torch", "minecraft:redstone_torch", "minecraft:redstone_wall_torch",
                "minecraft:end_rod", "#minecraft:logs", "#minecraft:planks", "#minecraft:wooden_stairs",
                "#minecraft:wooden_slabs", "#minecraft:wooden_fences", "#minecraft:fence_gates",
                "#minecraft:wooden_doors", "#minecraft:wooden_trapdoors", "#minecraft:wooden_pressure_plates",
                "#minecraft:signs", "#minecraft:all_hanging_signs", "minecraft:chest", "minecraft:trapped_chest",
                "minecraft:crafting_table", "minecraft:bookshelf", "minecraft:chiseled_bookshelf",
                "minecraft:note_block", "minecraft:jukebox", "minecraft:ladder", "minecraft:daylight_detector",
                "minecraft:barrel", "minecraft:loom", "minecraft:fletching_table", "minecraft:cartography_table",
                "minecraft:composter", "minecraft:lectern", "minecraft:bee_nest", "minecraft:beehive",
                "minecraft:brown_mushroom_block", "minecraft:red_mushroom_block", "minecraft:mushroom_stem",
                "#minecraft:banners", "#minecraft:flower_pots", "#c:skulls"));
        TANK = List.copyOf(selectors);
    }

    public record Rules(BlockList breakable, BlockList protectedBlocks) { }

    public static Rules rules(int weight) {
        return switch (weight) {
            case 1 -> new Rules(BlockList.of(CommonConfig.get(CommonConfig.CAR_BREAKABLE_BLOCKS)),
                    BlockList.of(CommonConfig.get(CommonConfig.CAR_PROTECTED_BLOCKS)));
            case 2 -> new Rules(BlockList.of(CommonConfig.get(CommonConfig.TANK_BREAKABLE_BLOCKS)),
                    BlockList.of(CommonConfig.get(CommonConfig.TANK_PROTECTED_BLOCKS)));
            default -> new Rules(BlockList.of(List.of()), BlockList.of(List.of()));
        };
    }
}
