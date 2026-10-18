package io.github.mcheliceport.datagen;

import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.block.Direction8;
import io.github.mcheliceport.block.DraftingTableBlock;
import io.github.mcheliceport.content.ContentLocation;
import io.github.mcheliceport.item.AircraftItem;
import io.github.mcheliceport.item.ThrowableItem;
import io.github.mcheliceport.registry.ModBlocks;
import io.github.mcheliceport.registry.ModItems;
import io.github.mcheliceport.registry.ModRegistries;
import io.github.mcheliceport.wingman.block.MarkerType;
import io.github.mcheliceport.wingman.block.WingmanMarkerBlock;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.MultiVariant;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.blockstates.PropertyDispatch;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.data.models.model.ModelLocationUtils;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Holder;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Block states and models of the port's blocks, and item models of its fixed items. Content items
 * get theirs at runtime ({@link io.github.mcheliceport.resources.ContentAssets}).
 *
 * <p>Textures stay where upstream had them ({@code mcheli:items/...}, {@code mcheli:blocks/...});
 * the port's atlas definitions add those folders to the item and block atlases.
 */
public class ModModelProvider extends ModelProvider {

    /** Items whose item model is hand-written (assets/mcheli_ce_port/items). */
    private static final List<DeferredItem<? extends Item>> TOOLS = List.of(ModItems.RANGEFINDER, ModItems.WRENCH,
            ModItems.GLTD, ModItems.FIM92, ModItems.FGM148);

    public ModModelProvider(PackOutput output) {
        super(output, McheliCePort.MOD_ID);
    }

    @Override
    protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        for (DeferredItem<? extends Item> item : List.of(ModItems.FUEL, ModItems.CHAIN, ModItems.PARACHUTE,
                ModItems.CONTAINER, ModItems.UAV_STATION, ModItems.UAV_STATION2, ModItems.INTERNAL,
                ModItems.FIM92_BULLET, ModItems.FGM148_BULLET)) {
            flatItem(itemModels, item.get(), mcheli("items/" + item.getId().getPath()));
        }
        // The hand tools: the flat model is the fallback of a hand-written mcheli_ce_port:tool item model
        // (ToolItemModel), which draws upstream's 3D model in the hand.
        for (DeferredItem<? extends Item> item : TOOLS) {
            ModelTemplates.FLAT_ITEM.create(ModelLocationUtils.getModelLocation(item.get()),
                    TextureMapping.layer0(new Material(mcheli("items/" + item.getId().getPath()))),
                    itemModels.modelOutput);
        }
        // Upstream has no textures for these three; the port draws simple placeholders.
        for (DeferredItem<? extends Item> item : List.of(ModItems.UAV_PAIRING_DEVICE, ModItems.UAV_TABLET,
                ModItems.MINE_DEFUSER)) {
            flatItem(itemModels, item.get(), item.getId().withPrefix("item/"));
        }
        // Spawn-egg style: a base and an overlay layer, each tinted (upstream's item colours).
        spawnGunner(itemModels, ModItems.SPAWN_GUNNER_VS_MONSTER.get(), 0xC0C0A0, 0xC00000);
        spawnGunner(itemModels, ModItems.SPAWN_GUNNER_VS_PLAYER.get(), 0xC0C0A0, 0x00C000);

        Identifier charger = ModelTemplates.CUBE_ALL.create(ModBlocks.CHARGER.get(),
                TextureMapping.cube(new Material(mcheli("blocks/charger"))), blockModels.modelOutput);
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(ModBlocks.CHARGER.get(),
                BlockModelGenerators.plainVariant(charger)));
        itemModels.itemModelOutput.accept(ModItems.CHARGER.get(), ItemModelUtils.plainModel(charger));

        wingmanMarker(blockModels);
        flatItem(itemModels, ModItems.WINGMAN_MARKER.get(), mcheli("items/wingman_marker"));

        Identifier draftingTable = draftingTableModel(blockModels);
        draftingTableState(blockModels, ModBlocks.DRAFTING_TABLE.get(), draftingTable);
        draftingTableState(blockModels, ModBlocks.LIT_DRAFTING_TABLE.get(), draftingTable);
        // The drafting table item's model is hand-written (ToolItemModel), as the hand tools'.
    }

    @Override
    protected Stream<? extends Holder<Block>> getKnownBlocks() {
        return ModRegistries.BLOCKS.getEntries().stream();
    }

    /** The fixed items only: content items are not known at build time. */
    @Override
    protected Stream<? extends Holder<Item>> getKnownItems() {
        return ModRegistries.ITEMS.getEntries().stream()
                .filter(holder -> !(holder.get() instanceof AircraftItem) && !(holder.get() instanceof ThrowableItem))
                .filter(holder -> TOOLS.stream().noneMatch(tool -> tool.get() == holder.get())
                        && holder.get() != ModItems.DRAFTING_TABLE.get());
    }

    private static Identifier mcheli(String path) {
        return Identifier.fromNamespaceAndPath(ContentLocation.NAMESPACE, path);
    }

    private static void flatItem(ItemModelGenerators itemModels, Item item, Identifier texture) {
        Identifier model = ModelTemplates.FLAT_ITEM.create(ModelLocationUtils.getModelLocation(item),
                TextureMapping.layer0(new Material(texture)), itemModels.modelOutput);
        itemModels.itemModelOutput.accept(item, ItemModelUtils.plainModel(model));
    }

    private static void spawnGunner(ItemModelGenerators itemModels, Item item, int baseColor, int spotColor) {
        String path = ModelLocationUtils.getModelLocation(item).getPath().substring("item/".length());
        Identifier model = ModelTemplates.TWO_LAYERED_ITEM.create(ModelLocationUtils.getModelLocation(item),
                TextureMapping.layered(new Material(mcheli("items/" + path)),
                        new Material(mcheli("items/" + path + "_overlay"))),
                itemModels.modelOutput);
        itemModels.itemModelOutput.accept(item, ItemModelUtils.tintedModel(model,
                ItemModelUtils.constantTint(baseColor), ItemModelUtils.constantTint(spotColor)));
    }

    private static void wingmanMarker(BlockModelGenerators blockModels) {
        WingmanMarkerBlock block = ModBlocks.WINGMAN_MARKER.get();
        PropertyDispatch.C1<MultiVariant, MarkerType> dispatch = PropertyDispatch.initial(WingmanMarkerBlock.TYPE);
        for (MarkerType type : MarkerType.values()) {
            String name = type.getSerializedName();
            Identifier model = ModelTemplates.CUBE_ALL.create(
                    ModelLocationUtils.getModelLocation(block, "_" + name),
                    TextureMapping.cube(new Material(mcheli("blocks/marker_" + name))), blockModels.modelOutput);
            dispatch.select(type, BlockModelGenerators.plainVariant(model));
        }
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(block).with(dispatch));
    }

    /**
     * The drafting table block draws nothing itself (its block entity renderer draws upstream's
     * MQO model); this model only gives its breaking particles.
     */
    private static Identifier draftingTableModel(BlockModelGenerators blockModels) {
        return ModelTemplates.PARTICLE_ONLY.create(ModBlocks.DRAFTING_TABLE.get(),
                TextureMapping.particle(new Material(mcheli("blocks/drafting_table"))), blockModels.modelOutput);
    }

    private static void draftingTableState(BlockModelGenerators blockModels, Block block, Identifier model) {
        // Vanilla block states only turn in steps of 90 degrees, so diagonal directions round down.
        PropertyDispatch.C1<MultiVariant, Direction8> dispatch = PropertyDispatch.initial(DraftingTableBlock.DIRECTION8);
        for (Direction8 direction : Direction8.values()) {
            MultiVariant variant = BlockModelGenerators.plainVariant(model);
            variant = switch (direction.index() / 2) {
                case 1 -> variant.with(BlockModelGenerators.Y_ROT_90);
                case 2 -> variant.with(BlockModelGenerators.Y_ROT_180);
                case 3 -> variant.with(BlockModelGenerators.Y_ROT_270);
                default -> variant;
            };
            dispatch.select(direction, variant);
        }
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(block).with(dispatch));
    }
}
