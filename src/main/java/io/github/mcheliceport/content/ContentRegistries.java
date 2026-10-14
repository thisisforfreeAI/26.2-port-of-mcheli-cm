package io.github.mcheliceport.content;

import com.mojang.logging.LogUtils;
import io.github.mcheliceport.content.addon.AddonPack;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.content.aircraft.HeliInfo;
import io.github.mcheliceport.content.aircraft.PlaneInfo;
import io.github.mcheliceport.content.aircraft.ShipInfo;
import io.github.mcheliceport.content.aircraft.TankInfo;
import io.github.mcheliceport.content.aircraft.VehicleInfo;
import io.github.mcheliceport.content.hud.Hud;
import io.github.mcheliceport.content.sound.SoundDefinitions;
import io.github.mcheliceport.content.throwable.ThrowableInfo;
import io.github.mcheliceport.content.weapon.WeaponInfo;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;

/**
 * The loaded content of every type.
 *
 * <p>Ported from upstream {@code com.norwood.mcheli.helper.info.ContentRegistries}. Types load in
 * upstream's order (HUDs, weapons, then vehicles and throwables), because vehicle files refer to
 * weapons and HUDs by name. Unlike upstream, every definition is validated exactly once, here.
 */
public final class ContentRegistries {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static volatile Snapshot current = Snapshot.EMPTY;
    // The registries being built; parsers look up already loaded types through the accessors.
    private static volatile Snapshot loading = Snapshot.EMPTY;

    private ContentRegistries() {
    }

    /** All registries of one load. */
    public record Snapshot(ContentRegistry<Hud> hud, ContentRegistry<WeaponInfo> weapon,
            ContentRegistry<HeliInfo> heli, ContentRegistry<PlaneInfo> plane, ContentRegistry<ShipInfo> ship,
            ContentRegistry<TankInfo> tank, ContentRegistry<VehicleInfo> vehicle,
            ContentRegistry<ThrowableInfo> throwable) {

        public static final Snapshot EMPTY = new Snapshot(ContentRegistry.empty(Hud.class, "hud"),
                ContentRegistry.empty(WeaponInfo.class, "weapons"),
                ContentRegistry.empty(HeliInfo.class, "helicopters"),
                ContentRegistry.empty(PlaneInfo.class, "planes"), ContentRegistry.empty(ShipInfo.class, "ships"),
                ContentRegistry.empty(TankInfo.class, "tanks"),
                ContentRegistry.empty(VehicleInfo.class, "vehicles"),
                ContentRegistry.empty(ThrowableInfo.class, "throwable"));

        /** Every vehicle definition, helicopters first, in load order within each type. */
        public List<AircraftInfo> aircraft() {
            List<AircraftInfo> list = new ArrayList<>();
            list.addAll(this.heli.values());
            list.addAll(this.plane.values());
            list.addAll(this.ship.values());
            list.addAll(this.tank.values());
            list.addAll(this.vehicle.values());
            return list;
        }

        public int totalSize() {
            return this.hud.size() + this.weapon.size() + this.heli.size() + this.plane.size() + this.ship.size()
                    + this.tank.size() + this.vehicle.size() + this.throwable.size();
        }
    }

    public static Snapshot get() {
        return current;
    }

    public static ContentRegistry<HeliInfo> heli() {
        return active().heli();
    }

    public static ContentRegistry<PlaneInfo> plane() {
        return active().plane();
    }

    public static ContentRegistry<ShipInfo> ship() {
        return active().ship();
    }

    public static ContentRegistry<TankInfo> tank() {
        return active().tank();
    }

    public static ContentRegistry<VehicleInfo> vehicle() {
        return active().vehicle();
    }

    public static ContentRegistry<WeaponInfo> weapon() {
        return active().weapon();
    }

    public static ContentRegistry<ThrowableInfo> throwable() {
        return active().throwable();
    }

    public static ContentRegistry<Hud> hud() {
        return active().hud();
    }

    private static Snapshot active() {
        Snapshot l = loading;
        return l != Snapshot.EMPTY ? l : current;
    }

    /**
     * Loads all content from the given packs, in order, and makes it the current content. A later
     * pack overrides content of the same name from an earlier one.
     */
    public static synchronized Snapshot loadContents(List<AddonPack> packs) {
        LOGGER.info("Loading content from {} packs", packs.size());
        SoundDefinitions.reset();
        Map<ContentType, List<ContentLoader.ContentEntry>> contents = new EnumMap<>(ContentType.class);
        for (ContentType type : ContentType.values()) {
            contents.put(type, new ArrayList<>());
        }
        for (AddonPack pack : packs) {
            LOGGER.debug("addon({}) loading start.", pack.getDomain());
            for (ContentLoader.ContentEntry entry : ContentLoader.load(pack)) {
                contents.get(entry.type()).add(entry);
            }
        }

        try {
            Snapshot s = Snapshot.EMPTY;
            s = new Snapshot(parseContents(Hud.class, "hud", contents.get(ContentType.HUD)), s.weapon, s.heli,
                    s.plane, s.ship, s.tank, s.vehicle, s.throwable);
            loading = s;
            s = new Snapshot(s.hud, parseContents(WeaponInfo.class, "weapons", contents.get(ContentType.WEAPON)),
                    s.heli, s.plane, s.ship, s.tank, s.vehicle, s.throwable);
            loading = s;
            s = new Snapshot(s.hud, s.weapon,
                    parseContents(HeliInfo.class, "helicopters", contents.get(ContentType.HELICOPTER)), s.plane,
                    s.ship, s.tank, s.vehicle, s.throwable);
            s = new Snapshot(s.hud, s.weapon, s.heli,
                    parseContents(PlaneInfo.class, "planes", contents.get(ContentType.PLANE)), s.ship, s.tank,
                    s.vehicle, s.throwable);
            s = new Snapshot(s.hud, s.weapon, s.heli, s.plane,
                    parseContents(ShipInfo.class, "ships", contents.get(ContentType.SHIP)), s.tank, s.vehicle,
                    s.throwable);
            s = new Snapshot(s.hud, s.weapon, s.heli, s.plane, s.ship,
                    parseContents(TankInfo.class, "tanks", contents.get(ContentType.TANK)), s.vehicle, s.throwable);
            s = new Snapshot(s.hud, s.weapon, s.heli, s.plane, s.ship, s.tank,
                    parseContents(VehicleInfo.class, "vehicles", contents.get(ContentType.VEHICLE)), s.throwable);
            s = new Snapshot(s.hud, s.weapon, s.heli, s.plane, s.ship, s.tank, s.vehicle,
                    parseContents(ThrowableInfo.class, "throwable", contents.get(ContentType.THROWABLE)));
            resolveHudNames(s);
            current = s;
            LOGGER.info("Loaded {} HUDs, {} weapons, {} helicopters, {} planes, {} ships, {} tanks, {} vehicles,"
                    + " {} throwables", s.hud.size(), s.weapon.size(), s.heli.size(), s.plane.size(),
                    s.ship.size(), s.tank.size(), s.vehicle.size(), s.throwable.size());
            return s;
        } finally {
            loading = Snapshot.EMPTY;
        }
    }

    private static <T extends BaseInfo> ContentRegistry<T> parseContents(Class<T> clazz, String dir,
            List<ContentLoader.ContentEntry> values) {
        ContentRegistry.Builder<T> builder = ContentRegistry.builder(clazz, dir);
        LOGGER.debug("content type({}) loading start. steps:{}", dir, values.size());
        values.stream().map(ContentLoader.ContentEntry::parse).filter(Objects::nonNull).map(clazz::cast)
                .forEach(builder::put);
        return builder.build();
    }

    /**
     * Replaces HUD names that name no loaded HUD with {@link Hud#NONE_NAME}, which is what upstream
     * stored for them ({@code MCH_Hud.NoDisp}, or nothing for default names).
     */
    private static void resolveHudNames(Snapshot s) {
        for (AircraftInfo info : s.aircraft()) {
            info.hudList.replaceAll(name -> name != null && s.hud.contains(name) ? name : Hud.NONE_NAME);
            if (info.hudTvMissile != null && !s.hud.contains(info.hudTvMissile)) {
                info.hudTvMissile = Hud.NONE_NAME;
            }
        }
    }
}
