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

    /** Flesh-bearing mobs eligible for MC Heli vehicle-kill blood. NTM namespaces stay excluded. */
    public static final TagKey<net.minecraft.world.entity.EntityType<?>> VEHICLE_BLOOD_MOBS = TagKey.create(
            Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "vehicle_blood_mobs"));

    private ModTags() {
    }

    private static TagKey<Block> block(String path) {
        return TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, path));
    }
}
