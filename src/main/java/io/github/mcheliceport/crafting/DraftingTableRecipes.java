package io.github.mcheliceport.crafting;

import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.content.ContentType;
import io.github.mcheliceport.item.AircraftItem;
import io.github.mcheliceport.registry.ModItems;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import org.jspecify.annotations.Nullable;

/**
 * The recipes the drafting table lists and crafts (upstream {@code MCH_ItemRecipe}, the aircraft
 * info managers' recipe lists and {@code MCH_Recipes#canCraft}/{@code consumeInventory}): every
 * shaped or shapeless crafting recipe that makes one of the port's items, sorted into upstream's
 * six lists. The same recipes also work in a crafting table, as upstream's did.
 */
public final class DraftingTableRecipes {

    /** Upstream's list ids ({@code MCH_DraftingTableGui.RECIPE_*}). */
    public static final int HELICOPTER = 0;
    public static final int PLANE = 1;
    public static final int VEHICLE = 2;
    public static final int TANK = 3;
    public static final int ITEM = 4;
    public static final int SHIP = 5;
    public static final int LIST_COUNT = 6;

    /** Upstream {@code MCH_ItemRecipe#registerCommonItemRecipe}'s order of the fixed items. */
    private static final List<String> FIXED_ORDER = List.of("fuel", "gltd", "chain", "parachute", "container",
            "uav_station", "uav_station2", "wrench", "rangefinder", "fim92", "fim92_bullet", "fgm148", "fgm148_bullet",
            "spawn_gunner_vs_monster", "spawn_gunner_vs_player", "drafting_table");

    private DraftingTableRecipes() {
    }

    /**
     * One recipe as the screen shows it: a 3 by 3 grid of ingredients (empty slots are empty) and
     * the result.
     */
    public record Entry(Identifier id, int list, List<Optional<Ingredient>> grid, ItemStack result) {

        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, Entry::id,
                ByteBufCodecs.VAR_INT, Entry::list,
                Ingredient.OPTIONAL_CONTENTS_STREAM_CODEC.apply(ByteBufCodecs.list(9)), Entry::grid,
                ItemStack.STREAM_CODEC, Entry::result,
                Entry::new);

        /** The ingredients to take, one item each (upstream ignored empty slots). */
        public List<Ingredient> ingredients() {
            return this.grid.stream().flatMap(Optional::stream).toList();
        }
    }

    /** The table's recipes, in upstream's order: fixed items first, then content in load order. */
    public static List<Entry> collect(RecipeManager recipes) {
        Map<Item, Integer> order = new HashMap<>();
        for (String name : FIXED_ORDER) {
            BuiltInRegistries.ITEM.getOptional(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, name))
                    .ifPresent(item -> order.putIfAbsent(item, order.size()));
        }
        for (ModItems.ContentItem content : ModItems.contentItems()) {
            order.putIfAbsent(content.item().get(), order.size());
        }
        List<Entry> entries = new ArrayList<>();
        for (RecipeHolder<?> holder : recipes.getRecipes()) {
            Entry entry = entry(holder);
            if (entry != null) {
                entries.add(entry);
            }
        }
        entries.sort(Comparator.<Entry>comparingInt(e -> order.getOrDefault(e.result().getItem(), Integer.MAX_VALUE))
                .thenComparing(e -> e.id().toString()));
        return entries;
    }

    /** The entry for a recipe, or null when the table does not list it. */
    public static @Nullable Entry entry(RecipeHolder<?> holder) {
        Recipe<?> recipe = holder.value();
        List<Optional<Ingredient>> grid = new ArrayList<>(9);
        ItemStack result;
        if (recipe instanceof ShapedRecipe shaped) {
            if (shaped.getWidth() > 3 || shaped.getHeight() > 3) {
                return null;
            }
            List<Optional<Ingredient>> ingredients = shaped.getIngredients();
            for (int i = 0; i < 9; i++) {
                int x = i % 3;
                int y = i / 3;
                grid.add(x < shaped.getWidth() && y < shaped.getHeight()
                        ? ingredients.get(x + y * shaped.getWidth()) : Optional.empty());
            }
            result = shaped.assemble(CraftingInput.EMPTY);
        } else if (recipe instanceof ShapelessRecipe shapeless && shapeless.result() != null) {
            List<Ingredient> ingredients = shapeless.placementInfo().ingredients();
            if (ingredients.size() > 9) {
                return null;
            }
            for (int i = 0; i < 9; i++) {
                grid.add(i < ingredients.size() ? Optional.of(ingredients.get(i)) : Optional.empty());
            }
            result = shapeless.assemble(CraftingInput.EMPTY);
        } else {
            return null;
        }
        if (result.isEmpty() || !McheliCePort.MOD_ID.equals(BuiltInRegistries.ITEM.getKey(result.getItem()).getNamespace())) {
            return null;
        }
        return new Entry(holder.id().identifier(), list(result), List.copyOf(grid), result);
    }

    private static int list(ItemStack result) {
        if (result.getItem() instanceof AircraftItem aircraft) {
            ContentType type = aircraft.getContentType();
            return switch (type) {
                case HELICOPTER -> HELICOPTER;
                case PLANE -> PLANE;
                case SHIP -> SHIP;
                case TANK -> TANK;
                case VEHICLE -> VEHICLE;
                default -> ITEM;
            };
        }
        return ITEM;
    }

    /**
     * The main-inventory slot each ingredient takes an item from, or null when the inventory does
     * not hold them all. Upstream checked each ingredient on its own, so one item could stand for
     * several; here every ingredient needs its own item.
     */
    public static int @Nullable [] match(Inventory inventory, List<Ingredient> ingredients) {
        int[] left = new int[Inventory.INVENTORY_SIZE];
        for (int i = 0; i < left.length; i++) {
            left[i] = inventory.getItem(i).getCount();
        }
        int[] slots = new int[ingredients.size()];
        for (int n = 0; n < ingredients.size(); n++) {
            slots[n] = -1;
            for (int i = 0; i < left.length; i++) {
                if (left[i] > 0 && ingredients.get(n).test(inventory.getItem(i))) {
                    left[i]--;
                    slots[n] = i;
                    break;
                }
            }
            if (slots[n] < 0) {
                return null;
            }
        }
        return slots;
    }

    /** Upstream {@code MCH_Recipes#canCraft}. */
    public static boolean canCraft(Inventory inventory, Entry entry) {
        return match(inventory, entry.ingredients()) != null;
    }

    /** Upstream {@code MCH_Recipes#consumeInventory}: takes one item per ingredient, or none at all. */
    public static boolean consume(Inventory inventory, Entry entry) {
        int[] slots = match(inventory, entry.ingredients());
        if (slots == null) {
            return false;
        }
        for (int slot : slots) {
            inventory.removeItem(slot, 1);
        }
        return true;
    }
}
