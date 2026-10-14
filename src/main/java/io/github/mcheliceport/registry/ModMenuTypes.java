package io.github.mcheliceport.registry;

import io.github.mcheliceport.menu.AircraftMenu;
import java.util.function.Supplier;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;

/** The port's menus. */
public final class ModMenuTypes {

    /** A vehicle's fuel slots and trunk. */
    public static final Supplier<MenuType<AircraftMenu>> AIRCRAFT =
            ModRegistries.MENU_TYPES.register("aircraft", () -> IMenuTypeExtension.create(AircraftMenu::new));

    private ModMenuTypes() {
    }

    /** Loads the class, which registers the menus. */
    static void init() {
    }
}
