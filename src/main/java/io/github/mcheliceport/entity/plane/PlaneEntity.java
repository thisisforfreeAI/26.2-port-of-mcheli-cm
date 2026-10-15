package io.github.mcheliceport.entity.plane;

import io.github.mcheliceport.config.CommonConfig;
import io.github.mcheliceport.content.ContentRegistries;
import io.github.mcheliceport.content.ContentType;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.content.aircraft.PlaneInfo;
import io.github.mcheliceport.entity.aircraft.WingedAircraftEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/** Plane entity, with the exhaust-flame animation added by upstream's plane implementation. */
public class PlaneEntity extends WingedAircraftEntity {
    public final PlaneExhaustAnimation exhaustAnimation = new PlaneExhaustAnimation();
    public float exhaustYaw;
    public float previousExhaustYaw;

    public PlaneEntity(EntityType<? extends PlaneEntity> type, Level level) { super(type, level); }
    @Override protected @Nullable AircraftInfo lookupInfo(String name) { return ContentRegistries.get().plane().get(name); }
    @Override public ContentType getContentType() { return ContentType.PLANE; }
    public @Nullable PlaneInfo getPlaneInfo() { return (PlaneInfo) this.wingedInfo; }
    @Override protected double speedMultiplier() { return CommonConfig.ALL_PLANE_SPEED.get(); }
    @Override protected String savePrefix() { return "Plane"; }

    @Override
    protected void onTypeChanged(AircraftInfo info) {
        super.onTypeChanged(info);
        this.exhaustAnimation.reset(((PlaneInfo) info).exhaustFlames.size());
    }

    @Override
    protected void onUpdateAngles(float seconds) {
        super.onUpdateAngles(seconds);
        if (this.wingedInfo != null && !this.isDestroyed()) {
            this.previousExhaustYaw = this.exhaustYaw;
            this.exhaustYaw = PlaneExhaustAnimation.steer(this.exhaustYaw, this.moveLeft, this.moveRight, seconds);
        }
    }
}
