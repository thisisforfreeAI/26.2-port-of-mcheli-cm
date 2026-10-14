package io.github.mcheliceport.item;

import io.github.mcheliceport.content.ContentRegistries;
import io.github.mcheliceport.content.ContentType;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;

/**
 * The item of one vehicle definition (helicopter, plane, ship, tank or vehicle). As upstream's
 * {@code MCH_ItemAircraft}, its durability is the vehicle's hit points.
 *
 * <p>Placing the vehicle comes with the vehicle entities (milestone M3 onwards).
 */
public class AircraftItem extends Item {

    private final ContentType type;
    private final String contentName;

    public AircraftItem(Properties properties, ContentType type, String contentName) {
        super(properties);
        this.type = type;
        this.contentName = contentName;
    }

    public ContentType getContentType() {
        return this.type;
    }

    /** The content name, which may differ from the item ID (see {@code ModItems#toItemPath}). */
    public String getContentName() {
        return this.contentName;
    }

    /** The current definition of this vehicle, or null if it is no longer loaded. */
    public @Nullable AircraftInfo getAircraftInfo() {
        ContentRegistries.Snapshot content = ContentRegistries.get();
        return switch (this.type) {
            case HELICOPTER -> content.heli().get(this.contentName);
            case PLANE -> content.plane().get(this.contentName);
            case SHIP -> content.ship().get(this.contentName);
            case TANK -> content.tank().get(this.contentName);
            case VEHICLE -> content.vehicle().get(this.contentName);
            default -> null;
        };
    }
}
