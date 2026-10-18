package io.github.mcheliceport.datagen;

import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.registry.ModBlocks;
import io.github.mcheliceport.registry.ModTags;
import net.minecraft.world.level.block.Blocks;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.neoforged.neoforge.common.data.BlockTagsProvider;

/**
 * Mining tags. Upstream's blocks used 1.12's iron material: a pickaxe mines them fastest, and the
 * charger and marker block (which do not override harvesting, unlike the drafting table) need a
 * stone pickaxe or better to drop.
 */
public class ModBlockTagsProvider extends BlockTagsProvider {

    public ModBlockTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries, McheliCePort.MOD_ID);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        this.tag(BlockTags.MINEABLE_WITH_PICKAXE)
                .add(ModBlocks.DRAFTING_TABLE.getKey())
                .add(ModBlocks.LIT_DRAFTING_TABLE.getKey())
                .add(ModBlocks.CHARGER.getKey())
                .add(ModBlocks.WINGMAN_MARKER.getKey());
        this.tag(BlockTags.NEEDS_STONE_TOOL)
                .add(ModBlocks.CHARGER.getKey())
                .add(ModBlocks.WINGMAN_MARKER.getKey());
        // The wrench's fast blocks: what 1.12 made of Material.IRON...
        this.tag(ModTags.WRENCH_METAL)
                .add(key(Blocks.IRON_BLOCK), key(Blocks.GOLD_BLOCK), key(Blocks.DIAMOND_BLOCK),
                        key(Blocks.EMERALD_BLOCK), key(Blocks.REDSTONE_BLOCK), key(Blocks.IRON_DOOR),
                        key(Blocks.IRON_TRAPDOOR), key(Blocks.IRON_BARS), key(Blocks.HOPPER), key(Blocks.CAULDRON),
                        key(Blocks.WATER_CAULDRON), key(Blocks.LAVA_CAULDRON), key(Blocks.POWDER_SNOW_CAULDRON),
                        key(Blocks.BREWING_STAND), key(Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE),
                        key(Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE))
                .add(ModBlocks.CHARGER.getKey(), ModBlocks.WINGMAN_MARKER.getKey());
        // ...and of its MaterialLogic kinds: circuits, plants, vines, snow layers and carpets.
        this.tag(ModTags.WRENCH_LOGIC)
                .add(key(Blocks.REDSTONE_WIRE), key(Blocks.REPEATER), key(Blocks.COMPARATOR), key(Blocks.LEVER),
                        key(Blocks.TORCH), key(Blocks.WALL_TORCH), key(Blocks.REDSTONE_TORCH),
                        key(Blocks.REDSTONE_WALL_TORCH), key(Blocks.TRIPWIRE), key(Blocks.TRIPWIRE_HOOK),
                        key(Blocks.FLOWER_POT), key(Blocks.VINE), key(Blocks.SNOW))
                .addTag(BlockTags.BUTTONS).addTag(BlockTags.RAILS).addTag(BlockTags.WOOL_CARPETS)
                .addTag(BlockTags.SMALL_FLOWERS);
    }

    private static net.minecraft.resources.ResourceKey<net.minecraft.world.level.block.Block> key(
            net.minecraft.world.level.block.Block block) {
        return block.builtInRegistryHolder().key();
    }
}
