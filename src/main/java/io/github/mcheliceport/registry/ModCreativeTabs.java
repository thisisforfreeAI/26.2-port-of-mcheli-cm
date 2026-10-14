package io.github.mcheliceport.registry;

import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.config.ClientConfig;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.item.AircraftItem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import org.jspecify.annotations.Nullable;

/**
 * The seven creative tabs (upstream {@code MCH_CreativeTabs}), placed after the vanilla tabs.
 *
 * <p>As upstream, vehicles are sorted by category and name and other items by translation key, and
 * a tab's icon is the item named in the client config. Upstream cycled through the tab's items when
 * no icon was configured; vanilla tabs keep one icon, so the port shows the tab's first item.
 */
public final class ModCreativeTabs {

    private static final Map<ModTab, DeferredHolder<CreativeModeTab, CreativeModeTab>> TABS =
            new EnumMap<>(ModTab.class);

    static {
        Identifier previous = CreativeModeTabs.SPAWN_EGGS.identifier();
        for (ModTab tab : ModTab.values()) {
            Identifier after = previous;
            DeferredHolder<CreativeModeTab, CreativeModeTab> holder = ModRegistries.CREATIVE_MODE_TABS.register(
                    tab.id, () -> CreativeModeTab.builder()
                            .title(Component.translatable(tab.translationKey()))
                            .icon(() -> icon(tab))
                            .displayItems((parameters, output) -> sortedItems(tab).forEach(output::accept))
                            .withTabsBefore(after)
                            .build());
            TABS.put(tab, holder);
            previous = holder.getId();
        }
    }

    private ModCreativeTabs() {
    }

    public static DeferredHolder<CreativeModeTab, CreativeModeTab> get(ModTab tab) {
        return TABS.get(tab);
    }

    /** The items of a tab, sorted as upstream sorted them. */
    public static List<ItemStack> sortedItems(ModTab tab) {
        List<Item> items = new ArrayList<>();
        for (DeferredItem<? extends Item> item : ModItems.tabItems(tab)) {
            items.add(item.get());
        }
        // Upstream compared two vehicles by "category.name" and anything else by translation key.
        // That comparator is not a total order, so the port sorts by translation key and then
        // sorts the vehicles among the places vehicles hold.
        items.sort(Comparator.comparing(ModCreativeTabs::legacyTranslationKey));
        List<Item> vehicles = new ArrayList<>(items.stream().filter(i -> i instanceof AircraftItem).toList());
        vehicles.sort(Comparator.comparing(ModCreativeTabs::categoryAndName));
        Iterator<Item> nextVehicle = vehicles.iterator();
        List<ItemStack> stacks = new ArrayList<>(items.size());
        for (Item item : items) {
            stacks.add(new ItemStack(item instanceof AircraftItem ? nextVehicle.next() : item));
        }
        return stacks;
    }

    /** Upstream's translation keys started with {@code item.} for items and {@code tile.} for blocks. */
    private static String legacyTranslationKey(Item item) {
        String key = item.getDescriptionId();
        return key.startsWith("block.") ? "tile." + key.substring("block.".length()) : key;
    }

    private static String categoryAndName(Item item) {
        AircraftInfo info = ((AircraftItem) item).getAircraftInfo();
        return info != null ? info.category + "." + info.getName() : "";
    }

    private static ItemStack icon(ModTab tab) {
        Item item = findItem(ClientConfig.tabIcon(tab));
        if (item == null) {
            List<ItemStack> items = sortedItems(tab);
            return items.isEmpty() ? new ItemStack(Items.IRON_BLOCK) : items.getFirst();
        }
        return new ItemStack(item);
    }

    /**
     * The item with the given name: {@code namespace:path}, or a bare upstream item name such as
     * {@code ah-64}, which is looked up in the port's namespace.
     */
    static @Nullable Item findItem(String name) {
        if (name.isBlank()) {
            return null;
        }
        Identifier id = name.indexOf(':') >= 0 ? Identifier.tryParse(name.trim())
                : Identifier.tryBuild(McheliCePort.MOD_ID, ModItems.toItemPath(name.trim()));
        if (id == null) {
            return null;
        }
        return BuiltInRegistries.ITEM.getOptional(id).filter(i -> i != Items.AIR).orElse(null);
    }

    /** Loads the class, which registers the tabs. */
    static void init() {
    }
}
