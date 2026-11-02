package io.github.mcheliceport.entity.ship;

import io.github.mcheliceport.config.CommonConfig;
import io.github.mcheliceport.content.ContentRegistries;
import io.github.mcheliceport.content.ContentType;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.content.aircraft.ShipInfo;
import io.github.mcheliceport.entity.aircraft.WingedAircraftEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/** Ship movement, buoyancy, steering and parts use the pinned MCH_EntityShip formulas. */
public class ShipEntity extends WingedAircraftEntity {
    public ShipEntity(EntityType<? extends ShipEntity> type, Level level) { super(type, level); }
    @Override protected @Nullable AircraftInfo lookupInfo(String name) { return ContentRegistries.get().ship().get(name); }
    @Override public ContentType getContentType() { return ContentType.SHIP; }
    public @Nullable ShipInfo getShipInfo() { return (ShipInfo) this.wingedInfo; }
    @Override protected double speedMultiplier() { return CommonConfig.ALL_SHIP_SPEED.get(); }
    @Override protected String savePrefix() { return "Ship"; }
}
