package io.github.mcheliceport.registry;

import com.mojang.logging.LogUtils;
import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.content.ContentRegistries;
import io.github.mcheliceport.content.ContentRegistry;
import io.github.mcheliceport.content.ContentType;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.content.throwable.ThrowableInfo;
import io.github.mcheliceport.item.AircraftItem;
import io.github.mcheliceport.item.ThrowableItem;
import io.github.mcheliceport.item.ParachuteItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The port's items: upstream's fixed items, the block items, and one item per vehicle and
 * throwable definition of the loaded content (upstream {@code MCH_MOD#registerItem*}).
 *
 * <p>Stack sizes and durabilities are upstream's. Item IDs are the upstream names in the
 * {@code mcheli_ce_port} namespace, for example {@code mcheli_ce_port:ah-64}.
 */
public final class ModItems {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<ModTab, List<DeferredItem<? extends Item>>> TAB_ITEMS = new EnumMap<>(ModTab.class);
    private static final List<ContentItem> CONTENT_ITEMS = new ArrayList<>();
    private static final Set<String> USED_PATHS = new HashSet<>();

    public static final DeferredItem<Item> SPAWN_GUNNER_VS_MONSTER = item("spawn_gunner_vs_monster", p -> p.stacksTo(1));
    public static final DeferredItem<Item> SPAWN_GUNNER_VS_PLAYER = item("spawn_gunner_vs_player", p -> p.stacksTo(1));
    public static final DeferredItem<Item> RANGEFINDER = item("rangefinder", p -> p.durability(10));
    /** Upstream's wrench took its durability from 1.12's iron tool material. */
    public static final DeferredItem<Item> WRENCH = item("wrench", p -> p.durability(250));
    public static final DeferredItem<Item> FUEL = item("fuel", p -> p.durability(600).setNoCombineRepair());
    public static final DeferredItem<Item> GLTD = item("gltd", p -> p.stacksTo(1));
    public static final DeferredItem<Item> CHAIN = item("chain", p -> p.stacksTo(1));
    public static final DeferredItem<Item> PARACHUTE = register("parachute", ParachuteItem::new,
            p -> p.stacksTo(1), ModTab.ITEMS);
    public static final DeferredItem<Item> CONTAINER = item("container", p -> p.stacksTo(1));
    public static final DeferredItem<Item> UAV_STATION = item("uav_station", p -> p.stacksTo(1));
    /** The portable UAV controller. */
    public static final DeferredItem<Item> UAV_STATION2 = item("uav_station2", p -> p.stacksTo(1));
    public static final DeferredItem<Item> UAV_PAIRING_DEVICE = item("uav_pairing_device", p -> p.stacksTo(1));
    public static final DeferredItem<Item> UAV_TABLET = item("uav_tablet", p -> p.stacksTo(1));
    public static final DeferredItem<Item> MINE_DEFUSER = item("mine_defuser", p -> p.stacksTo(1));
    /** Upstream's invisible helper item ({@code MCH_InvisibleItem}). It is in no creative tab. */
    public static final DeferredItem<Item> INTERNAL = register("internal", Item::new, UnaryOperator.identity(), null);
    public static final DeferredItem<Item> FIM92_BULLET = item("fim92_bullet", p -> p.stacksTo(2));
    public static final DeferredItem<Item> FGM148_BULLET = item("fgm148_bullet", p -> p.stacksTo(2));
    public static final DeferredItem<Item> FIM92 = item("fim92", p -> p.durability(10));
    public static final DeferredItem<Item> FGM148 = item("fgm148", p -> p.durability(10));
    public static final DeferredItem<BlockItem> DRAFTING_TABLE = blockItem(ModBlocks.DRAFTING_TABLE);
    public static final DeferredItem<BlockItem> CHARGER = blockItem(ModBlocks.CHARGER);
    public static final DeferredItem<BlockItem> WINGMAN_MARKER = blockItem(ModBlocks.WINGMAN_MARKER);

    private ModItems() {
    }

    /** An item made from a content definition. */
    public record ContentItem(ContentType type, String contentName, DeferredItem<? extends Item> item) {

        public Identifier id() {
            return this.item.getId();
        }
    }

    /**
     * Registers one item per vehicle and throwable of the given content. Upstream's order:
     * throwables, then helicopters, planes, ships, tanks and vehicles.
     */
    public static void registerContentItems(ContentRegistries.Snapshot content) {
        for (ThrowableInfo info : content.throwable().values()) {
            int stackSize = Mth.clamp(info.stackSize, 1, Item.ABSOLUTE_MAX_STACK_SIZE);
            String name = info.getName();
            registerContentItem(ContentType.THROWABLE, name, ModTab.ITEMS, p -> new ThrowableItem(p, name),
                    p -> p.stacksTo(stackSize));
        }
        registerAircraft(ContentType.HELICOPTER, content.heli(), ModTab.HELICOPTERS);
        registerAircraft(ContentType.PLANE, content.plane(), ModTab.PLANES);
        registerAircraft(ContentType.SHIP, content.ship(), ModTab.SHIPS);
        registerAircraft(ContentType.TANK, content.tank(), ModTab.TANKS);
        registerAircraft(ContentType.VEHICLE, content.vehicle(), ModTab.VEHICLES);
        LOGGER.info("Registered {} content items", CONTENT_ITEMS.size());
    }

    private static void registerAircraft(ContentType type, ContentRegistry<? extends AircraftInfo> registry,
            ModTab typeTab) {
        for (AircraftInfo info : registry.values()) {
            // Upstream puts supply vehicles that nobody rides (ammo boxes, fuel trucks) in the items tab.
            boolean supplyOnly = !info.canRide && (info.ammoSupplyRange > 0f || info.fuelSupplyRange > 0f);
            int maxHp = Math.max(1, info.maxHp);
            String name = info.getName();
            registerContentItem(type, name, supplyOnly ? ModTab.ITEMS : typeTab,
                    p -> new AircraftItem(p, type, name), p -> p.durability(maxHp));
        }
    }

    private static void registerContentItem(ContentType type, String contentName, ModTab tab,
            Function<Item.Properties, ? extends Item> factory,
            UnaryOperator<Item.Properties> properties) {
        String path = toItemPath(contentName);
        if (!path.equals(contentName)) {
            LOGGER.warn("The {} '{}' gets the item ID '{}:{}': its name has characters item IDs cannot have",
                    type.typeName, contentName, McheliCePort.MOD_ID, path);
        }
        if (USED_PATHS.contains(path)) {
            LOGGER.error("Skipping the item of {} '{}': the item ID '{}:{}' is already taken", type.typeName,
                    contentName, McheliCePort.MOD_ID, path);
            return;
        }
        DeferredItem<? extends Item> item = register(path, factory, properties, tab);
        CONTENT_ITEMS.add(new ContentItem(type, contentName, item));
    }

    /**
     * The item ID path for a content name: the name itself, with characters that IDs cannot hold
     * (1.12 allowed spaces, for example) replaced by {@code _}.
     */
    public static String toItemPath(String contentName) {
        String lower = contentName.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            sb.append(Identifier.validPathChar(c) ? c : '_');
        }
        return sb.toString();
    }

    /** All items made from content, in registration order. */
    public static List<ContentItem> contentItems() {
        return Collections.unmodifiableList(CONTENT_ITEMS);
    }

    /** The items of a creative tab, in registration order (the tab sorts them). */
    public static List<DeferredItem<? extends Item>> tabItems(ModTab tab) {
        return Collections.unmodifiableList(TAB_ITEMS.getOrDefault(tab, List.of()));
    }

    private static DeferredItem<Item> item(String path, UnaryOperator<Item.Properties> properties) {
        return register(path, Item::new, properties, ModTab.ITEMS);
    }

    private static DeferredItem<BlockItem> blockItem(DeferredBlock<? extends Block> block) {
        return register(block.getId().getPath(), p -> new BlockItem(block.get(), p),
                Item.Properties::useBlockDescriptionPrefix, ModTab.ITEMS);
    }

    private static <I extends Item> DeferredItem<I> register(String path,
            Function<Item.Properties, ? extends I> factory,
            UnaryOperator<Item.Properties> properties, @Nullable ModTab tab) {
        DeferredItem<I> item = ModRegistries.ITEMS.registerItem(path, factory, properties);
        USED_PATHS.add(path);
        if (tab != null) {
            TAB_ITEMS.computeIfAbsent(tab, t -> new ArrayList<>()).add(item);
        }
        return item;
    }

    /** Loads the class, which registers the fixed items. */
    static void init() {
    }
}
