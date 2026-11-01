package io.github.mcheliceport.registry;

import io.github.mcheliceport.menu.AircraftMenu;
import java.util.function.Supplier;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;

/** The port's menus. */
public final class ModMenuTypes {
    public static final Supplier<MenuType<io.github.mcheliceport.menu.UavStationMenu>> UAV_STATION =
            ModRegistries.MENU_TYPES.register("uav_station", () -> IMenuTypeExtension.create(io.github.mcheliceport.menu.UavStationMenu::new));

    /** A vehicle's fuel slots and trunk. */
    public static final Supplier<MenuType<AircraftMenu>> AIRCRAFT =
            ModRegistries.MENU_TYPES.register("aircraft", () -> IMenuTypeExtension.create(AircraftMenu::new));

    /** The drafting table's recipe browser. */
    public static final Supplier<MenuType<io.github.mcheliceport.menu.DraftingTableMenu>> DRAFTING_TABLE =
            ModRegistries.MENU_TYPES.register("drafting_table",
                    () -> IMenuTypeExtension.create(io.github.mcheliceport.menu.DraftingTableMenu::new));

    private ModMenuTypes() {
    }

    /** Loads the class, which registers the menus. */
    static void init() {
    }
}
