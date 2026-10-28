package io.github.mcheliceport.datagen;

import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.crafting.FuelRefillRecipe;
import io.github.mcheliceport.crafting.RangeFinderRechargeRecipe;
import io.github.mcheliceport.registry.ModItems;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.SpecialRecipeBuilder;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.Tags;

/**
 * The crafting recipes of the fixed items, from upstream's {@code assets/mcheli/recipes/*.json}.
 * Upstream's Forge ore dictionary names become common tags ({@code ingotIron} is {@code c:ingots/iron}).
 *
 * <p>Upstream's two special recipes, refilling fuel cans ({@link FuelRefillRecipe}) and recharging
 * the rangefinder ({@link RangeFinderRechargeRecipe}), are saved under upstream's recipe names.
 */
public class ModRecipeProvider extends RecipeProvider {

    protected ModRecipeProvider(HolderLookup.Provider registries, RecipeOutput output) {
        super(registries, output);
    }

    @Override
    protected void buildRecipes() {
        this.shaped(RecipeCategory.DECORATIONS, ModItems.REPAIR_PAD.get(), 9)
                .pattern("IYI").pattern("SRS").pattern("IYI")
                .define('I', Tags.Items.INGOTS_IRON).define('Y', Tags.Items.DYES_YELLOW)
                .define('S', Items.SMOOTH_STONE).define('R', Tags.Items.DUSTS_REDSTONE)
                .unlockedBy("has_iron_ingot", this.has(Tags.Items.INGOTS_IRON)).save(this.output);
        this.shaped(RecipeCategory.MISC, ModItems.CHAIN.get())
                .pattern("I I").pattern("III").pattern("I I")
                .define('I', Tags.Items.INGOTS_IRON)
                .unlockedBy("has_iron_ingot", this.has(Tags.Items.INGOTS_IRON)).save(this.output);
        this.shaped(RecipeCategory.TRANSPORTATION, ModItems.CONTAINER.get())
                .pattern("CCI")
                .define('C', Items.CHEST).define('I', Tags.Items.INGOTS_IRON)
                .unlockedBy("has_chest", this.has(Items.CHEST)).save(this.output);
        this.shaped(RecipeCategory.DECORATIONS, ModItems.DRAFTING_TABLE.get())
                .pattern("R  ").pattern("PCP").pattern("F F")
                .define('C', Tags.Items.PLAYER_WORKSTATIONS_CRAFTING_TABLES).define('F', Tags.Items.FENCES_WOODEN)
                .define('P', net.minecraft.tags.ItemTags.PLANKS).define('R', Tags.Items.DUSTS_REDSTONE)
                .unlockedBy("has_crafting_table", this.has(Tags.Items.PLAYER_WORKSTATIONS_CRAFTING_TABLES))
                .save(this.output);
        this.shaped(RecipeCategory.COMBAT, ModItems.FGM148.get())
                .pattern("III").pattern("GR ")
                .define('G', Tags.Items.GLASS_BLOCKS).define('I', Tags.Items.INGOTS_IRON)
                .define('R', Tags.Items.DUSTS_REDSTONE)
                .unlockedBy("has_redstone", this.has(Tags.Items.DUSTS_REDSTONE)).save(this.output);
        this.shaped(RecipeCategory.COMBAT, ModItems.FGM148_BULLET.get())
                .pattern(" R ").pattern(" I ").pattern(" G ")
                .define('G', Tags.Items.GUNPOWDERS).define('I', Tags.Items.INGOTS_IRON)
                .define('R', Tags.Items.DUSTS_REDSTONE)
                .unlockedBy("has_gunpowder", this.has(Tags.Items.GUNPOWDERS)).save(this.output);
        this.shaped(RecipeCategory.COMBAT, ModItems.FIM92.get())
                .pattern("G  ").pattern("III").pattern("RI ")
                .define('G', Tags.Items.GLASS_BLOCKS).define('I', Tags.Items.INGOTS_IRON)
                .define('R', Tags.Items.DUSTS_REDSTONE)
                .unlockedBy("has_redstone", this.has(Tags.Items.DUSTS_REDSTONE)).save(this.output);
        this.shaped(RecipeCategory.COMBAT, ModItems.FIM92_BULLET.get())
                .pattern("R  ").pattern(" I ").pattern("  G")
                .define('G', Tags.Items.GUNPOWDERS).define('I', Tags.Items.INGOTS_IRON)
                .define('R', Tags.Items.DUSTS_REDSTONE)
                .unlockedBy("has_gunpowder", this.has(Tags.Items.GUNPOWDERS)).save(this.output);
        this.shaped(RecipeCategory.MISC, ModItems.FUEL.get())
                .pattern("ICI").pattern("III")
                .define('I', Tags.Items.INGOTS_IRON).define('C', Ingredient.of(Items.COAL, Items.CHARCOAL))
                .unlockedBy("has_coal", this.has(Items.COAL)).save(this.output);
        this.shaped(RecipeCategory.TOOLS, ModItems.GLTD.get())
                .pattern(" B ").pattern("IDI").pattern("IRI")
                .define('B', Tags.Items.STORAGE_BLOCKS_IRON).define('I', Tags.Items.INGOTS_IRON)
                .define('D', Tags.Items.GEMS_DIAMOND).define('R', Tags.Items.DUSTS_REDSTONE)
                .unlockedBy("has_diamond", this.has(Tags.Items.GEMS_DIAMOND)).save(this.output);
        this.shaped(RecipeCategory.TRANSPORTATION, ModItems.PARACHUTE.get())
                .pattern("WWW").pattern("S S").pattern(" W ")
                .define('S', Tags.Items.STRINGS).define('W', net.minecraft.tags.ItemTags.WOOL)
                .unlockedBy("has_string", this.has(Tags.Items.STRINGS)).save(this.output);
        this.shaped(RecipeCategory.TOOLS, ModItems.RANGEFINDER.get())
                .pattern("III").pattern("RGR").pattern("III")
                .define('I', Tags.Items.INGOTS_IRON).define('R', Tags.Items.DUSTS_REDSTONE)
                .define('G', Tags.Items.GLASS_BLOCKS)
                .unlockedBy("has_redstone", this.has(Tags.Items.DUSTS_REDSTONE)).save(this.output);
        this.shaped(RecipeCategory.MISC, ModItems.SPAWN_GUNNER_VS_MONSTER.get())
                .pattern("EI")
                .define('E', Tags.Items.EGGS).define('I', Tags.Items.STORAGE_BLOCKS_IRON)
                .unlockedBy("has_egg", this.has(Tags.Items.EGGS)).save(this.output);
        this.shaped(RecipeCategory.MISC, ModItems.SPAWN_GUNNER_VS_PLAYER.get())
                .pattern("EG")
                .define('E', Tags.Items.EGGS).define('G', Tags.Items.STORAGE_BLOCKS_GOLD)
                .unlockedBy("has_egg", this.has(Tags.Items.EGGS)).save(this.output);
        this.shaped(RecipeCategory.TOOLS, ModItems.UAV_STATION.get())
                .pattern("III").pattern("IDI").pattern("IRI")
                .define('I', Tags.Items.INGOTS_IRON).define('D', Tags.Items.GEMS_DIAMOND)
                .define('R', Tags.Items.STORAGE_BLOCKS_REDSTONE)
                .unlockedBy("has_diamond", this.has(Tags.Items.GEMS_DIAMOND)).save(this.output);
        this.shaped(RecipeCategory.TOOLS, ModItems.UAV_STATION2.get())
                .pattern("IDI").pattern("IRI")
                .define('I', Tags.Items.INGOTS_IRON).define('D', Tags.Items.GEMS_DIAMOND)
                .define('R', Tags.Items.DUSTS_REDSTONE)
                .unlockedBy("has_diamond", this.has(Tags.Items.GEMS_DIAMOND)).save(this.output);
        this.shaped(RecipeCategory.TOOLS, ModItems.WRENCH.get())
                .pattern(" I ").pattern(" II").pattern("I  ")
                .define('I', Tags.Items.INGOTS_IRON)
                .unlockedBy("has_iron_ingot", this.has(Tags.Items.INGOTS_IRON)).save(this.output);
        SpecialRecipeBuilder.special(() -> FuelRefillRecipe.INSTANCE)
                .save(this.output, McheliCePort.MOD_ID + ":charge_fuel");
        SpecialRecipeBuilder.special(() -> RangeFinderRechargeRecipe.INSTANCE)
                .save(this.output, McheliCePort.MOD_ID + ":charge_power_range_finder");
    }

    /** Runs the provider in datagen. */
    public static class Runner extends RecipeProvider.Runner {

        public Runner(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
            super(output, registries);
        }

        @Override
        protected RecipeProvider createRecipeProvider(HolderLookup.Provider registries, RecipeOutput output) {
            return new ModRecipeProvider(registries, output);
        }

        @Override
        public String getName() {
            return "McHeli CE (26.2 Port) recipes";
        }
    }
}
