package io.github.mcheliceport.resources;

import com.mojang.logging.LogUtils;
import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.ModContentLoader;
import io.github.mcheliceport.content.ContentLocation;
import io.github.mcheliceport.content.ContentRegistries;
import io.github.mcheliceport.content.addon.AddonPack;
import io.github.mcheliceport.content.addon.BuiltinAddonPack;
import io.github.mcheliceport.content.addon.PackFiles;
import io.github.mcheliceport.registry.ModItems;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.flag.FeatureFlagSet;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import org.slf4j.Logger;

/**
 * Adds McHeli's packs to the game's resource and data pack lists (PORTING_PLAN.md section 5.5):
 * <ul>
 *   <li>a generated resource pack with the content items' models, names and sounds
 *       ({@link ContentAssets}), below every other pack so that resource packs can replace it;</li>
 *   <li>a generated data pack with the content items' recipes ({@link ContentRecipes});</li>
 *   <li>every addon pack in {@code mcheli_addons/} except the extracted stock pack, as an always-on
 *       resource pack (and data pack if it has a {@code data} folder), so addon textures, models and
 *       sounds load. The port supplies the pack metadata, so old {@code pack_format: 3} packs load
 *       without a "made for an older version" warning. As in upstream, only {@code assets} at the
 *       root of a pack are seen as resources.</li>
 * </ul>
 */
@EventBusSubscriber(modid = McheliCePort.MOD_ID)
public final class ModPackFinders {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final PackSource ADDON_SOURCE = PackSource.create(name -> Component.translatable(
            "pack.nameAndSource", name, Component.translatable("pack.source.mcheli_ce_port.addon"))
            .withStyle(ChatFormatting.GRAY), true);

    private ModPackFinders() {
    }

    @SubscribeEvent
    static void addPackFinders(AddPackFindersEvent event) {
        PackType type = event.getPackType();
        if (type == PackType.CLIENT_RESOURCES) {
            event.addRepositorySource(packs -> {
                packs.accept(GeneratedPack.create(McheliCePort.MOD_ID + "/generated",
                        Component.literal("McHeli CE content"), type, Pack.Position.BOTTOM,
                        () -> ContentAssets.build(ModItems.contentItems(), ContentRegistries.get(),
                                soundFileCheck())));
                addAddonPacks(packs, type);
            });
        } else if (type == PackType.SERVER_DATA && !event.isTrusted()) {
            // The trusted repository only lists packs the client may already know; ours are not among them.
            event.addRepositorySource(packs -> {
                packs.accept(GeneratedPack.create(McheliCePort.MOD_ID + "/content_recipes",
                        Component.literal("McHeli CE content recipes"), type, Pack.Position.TOP,
                        () -> ContentRecipes.build(ModItems.contentItems(), ContentRegistries.get(),
                                REGISTRY_LOOKUP)));
                addAddonPacks(packs, type);
            });
        }
    }

    private static void addAddonPacks(Consumer<Pack> packs, PackType type) {
        for (AddonPack addon : ModContentLoader.packs()) {
            if (addon.getFile().getFileName().toString().equals(BuiltinAddonPack.EXTRACTED_FOLDER_NAME)) {
                continue;
            }
            if (!hasFolder(addon, type.getDirectory())) {
                continue;
            }
            Pack.ResourcesSupplier resources = Files.isDirectory(addon.getFile())
                    ? new PathPackResources.PathResourcesSupplier(addon.getFile())
                    : new FilePackResources.FileResourcesSupplier(addon.getFile());
            String id = McheliCePort.MOD_ID + "/addon/" + addon.getFile().getFileName();
            Component title = Component.literal(addon.getName());
            Component description = Component.literal(addon.getDescription().isEmpty()
                    ? addon.getDomain() + " " + addon.getVersion() : addon.getDescription());
            packs.accept(new Pack(new PackLocationInfo(id, title, ADDON_SOURCE, Optional.empty()), resources,
                    new Pack.Metadata(description, PackCompatibility.COMPATIBLE, FeatureFlagSet.of(), List.of(), false),
                    new PackSelectionConfig(true, Pack.Position.TOP, false)));
        }
    }

    private static boolean hasFolder(AddonPack addon, String folder) {
        try (PackFiles files = PackFiles.open(addon.getFile())) {
            return Files.isDirectory(files.root().resolve(folder));
        } catch (IOException e) {
            LOGGER.warn("Could not open addon pack {}", addon, e);
            return false;
        }
    }

    private static final ContentRecipes.ItemLookup REGISTRY_LOOKUP = BuiltInRegistries.ITEM::containsKey;

    /**
     * Whether {@code assets/mcheli/sounds/<name>.ogg} exists in the mod or an addon pack. Upstream
     * asked the client's resource manager; the port decides when it generates {@code sounds.json},
     * so sounds added only by other resource packs do not count.
     */
    private static Predicate<String> soundFileCheck() {
        String prefix = "assets/" + ContentLocation.NAMESPACE + "/sounds/";
        Set<String> addonFiles = new HashSet<>();
        for (AddonPack addon : ModContentLoader.packs()) {
            try (PackFiles files = PackFiles.open(addon.getFile())) {
                for (String path : files.listFiles()) {
                    if (path.startsWith(prefix)) {
                        addonFiles.add(path);
                    }
                }
            } catch (IOException e) {
                LOGGER.warn("Could not list the sounds of addon pack {}", addon, e);
            }
        }
        return name -> {
            String path = prefix + name + ".ogg";
            return addonFiles.contains(path) || ModContentLoader.modContents().containsFile(path);
        };
    }
}
