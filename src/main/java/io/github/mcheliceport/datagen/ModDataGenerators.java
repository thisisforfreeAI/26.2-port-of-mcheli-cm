package io.github.mcheliceport.datagen;

import io.github.mcheliceport.McheliCePort;
import java.util.List;
import java.util.Set;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/**
 * Data generation ({@code ./gradlew runData}), writing to {@code src/generated/resources}: models and
 * block states, lang files, recipes, loot tables and block tags of the port's fixed items and
 * blocks.
 */
@EventBusSubscriber(modid = McheliCePort.MOD_ID, value = Dist.CLIENT)
public final class ModDataGenerators {

    private ModDataGenerators() {
    }

    @SubscribeEvent
    static void gatherData(GatherDataEvent.Client event) {
        event.createProvider(ModModelProvider::new);
        for (String locale : List.of("en_us", "ja_jp", "zh_cn")) {
            event.createProvider(output -> new ModLanguageProvider(output, locale));
        }
        event.createProvider(ModRecipeProvider.Runner::new);
        event.createProvider((output, registries) -> new LootTableProvider(output, Set.of(),
                List.of(new LootTableProvider.SubProviderEntry(ModBlockLootProvider::new, LootContextParamSets.BLOCK)),
                registries));
        event.createProvider(ModBlockTagsProvider::new);
    }
}
