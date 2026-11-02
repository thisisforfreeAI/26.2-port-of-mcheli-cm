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
import io.github.mcheliceport.platform.registry.DeferredRegister;

/**
 * Every deferred register the port uses, all in the {@code mcheli_ce_port} namespace. The entries
 * live in {@link ModBlocks}, {@link ModItems}, {@link ModEntityTypes} and {@link ModCreativeTabs};
 * content-driven items are added from the parsed content packs (PORTING_PLAN.md section 5.1).
 */
public final class ModRegistries {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(McheliCePort.MOD_ID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(McheliCePort.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, McheliCePort.MOD_ID);
    public static final DeferredRegister.Entities ENTITY_TYPES = DeferredRegister.createEntities(McheliCePort.MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, McheliCePort.MOD_ID);
    public static final DeferredRegister.DataComponents DATA_COMPONENT_TYPES =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, McheliCePort.MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, McheliCePort.MOD_ID);
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, McheliCePort.MOD_ID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, McheliCePort.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, McheliCePort.MOD_ID);

    /** In dependency order: Fabric registers each register's entries straight away. */
    private static final List<DeferredRegister<?>> ALL = List.of(
            DATA_COMPONENT_TYPES, SOUND_EVENTS, PARTICLE_TYPES, BLOCKS, ModFluids.FLUIDS, ITEMS, BLOCK_ENTITY_TYPES,
            ENTITY_TYPES, MENU_TYPES, RECIPE_SERIALIZERS, CREATIVE_MODE_TABS, ModCriteriaTriggers.TRIGGERS);

    private ModRegistries() {}

    /** Adds all entries, including one item per vehicle and throwable of {@code content}, and registers. */
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
        ALL.forEach(DeferredRegister::register);
    }

    /** Logs how many entries each register holds, e.g. {@code item=0, block=0, ...}. */
    public static void logSummary() {
        String summary = ALL.stream()
                .map(register -> register.getRegistryName().getPath() + "=" + register.getEntries().size())
                .collect(Collectors.joining(", "));
        McheliCePort.LOGGER.info("Registered entries: {}", summary);
    }
}
