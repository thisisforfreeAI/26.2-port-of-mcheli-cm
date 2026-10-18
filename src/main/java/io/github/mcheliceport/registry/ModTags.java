package io.github.mcheliceport.registry;

import io.github.mcheliceport.McheliCePort;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/** The port's tags. */
public final class ModTags {

    /** Blocks of 1.12's iron material, which the wrench mines at 20.5 and harvests. */
    public static final TagKey<Block> WRENCH_METAL = block("wrench_metal");
    /** Blocks of 1.12's "logic" materials (circuits, plants, vines, snow layers, carpets): 5.5. */
    public static final TagKey<Block> WRENCH_LOGIC = block("wrench_logic");

    private ModTags() {
    }

    private static TagKey<Block> block(String path) {
        return TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, path));
    }
}
