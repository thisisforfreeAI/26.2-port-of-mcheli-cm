package io.github.mcheliceport.registry;

import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.content.ContentRegistries;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Every register the port uses, all in the {@code mcheli_ce_port} namespace. The entries
 * live in {@link ModBlocks}, {@link ModItems}, {@link ModEntityTypes} and {@link ModCreativeTabs};
 * content-driven items are added from the parsed content packs (PORTING_PLAN.md section 5.1).
 */
public final class ModRegistries {
    public static final ModRegister.Items ITEMS = ModRegister.createItems();
    public static final ModRegister.Blocks BLOCKS = ModRegister.createBlocks();
    public static final ModRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = ModRegister.create(Registries.BLOCK_ENTITY_TYPE);
    public static final ModRegister.Entities ENTITY_TYPES = ModRegister.createEntities();
    public static final ModRegister<MenuType<?>> MENU_TYPES = ModRegister.create(Registries.MENU);
    public static final ModRegister.DataComponents DATA_COMPONENT_TYPES = ModRegister.createDataComponents();
    public static final ModRegister<SoundEvent> SOUND_EVENTS = ModRegister.create(Registries.SOUND_EVENT);
    public static final ModRegister<ParticleType<?>> PARTICLE_TYPES = ModRegister.create(Registries.PARTICLE_TYPE);
    public static final ModRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = ModRegister.create(Registries.RECIPE_SERIALIZER);
    public static final ModRegister<CreativeModeTab> CREATIVE_MODE_TABS = ModRegister.create(Registries.CREATIVE_MODE_TAB);

    private static final List<ModRegister<?>> ALL = List.of(
            ITEMS, BLOCKS, ModFluids.FLUIDS, BLOCK_ENTITY_TYPES, ENTITY_TYPES, MENU_TYPES,
            DATA_COMPONENT_TYPES, SOUND_EVENTS, PARTICLE_TYPES, RECIPE_SERIALIZERS, CREATIVE_MODE_TABS,
            ModCriteriaTriggers.TRIGGERS);

    private ModRegistries() {}

    /**
     * Adds all entries, including one item per vehicle and throwable of {@code content}. NeoForge
     * registers them in its registry events; Fabric registers each one as it is added.
     */
    public static void register(ContentRegistries.Snapshot content) {
        ModBlocks.init();
        ModItems.init();
        ModFluids.init();
        ModItems.registerContentItems(content);
        ModEntityTypes.init();
        ModDataComponents.init();
        ModMenuTypes.init();
        ModRecipeSerializers.init();
        ModCreativeTabs.init();
        ModCriteriaTriggers.init();
    }

    /** Logs how many entries each register holds, e.g. {@code item=0, block=0, ...}. */
    public static void logSummary() {
        String summary = ALL.stream()
                .map(register -> register.getRegistryName().getPath() + "=" + register.getEntries().size())
                .collect(Collectors.joining(", "));
        McheliCePort.LOGGER.info("Registered entries: {}", summary);
    }
}
