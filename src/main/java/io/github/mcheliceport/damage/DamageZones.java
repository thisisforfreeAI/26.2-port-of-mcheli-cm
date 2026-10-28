package io.github.mcheliceport.damage;

import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.content.aircraft.BoundingBoxInfo;
import io.github.mcheliceport.content.aircraft.HeliInfo;
import io.github.mcheliceport.content.aircraft.WingedAircraftInfo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A vehicle type's component zones (M9). Stock content names no component positions, so they are
 * derived from what it does have:
 *
 * <ul>
 * <li>engines: {@code ENGINE}-typed bounding boxes; otherwise one per engine outlet, 1.5 outlet sizes
 * forward of the outlet; otherwise one per propeller (a plane rotor turning about a horizontal
 * axis); otherwise one in the rear third of the hull;</li>
 * <li>main rotors: rotors turning about a mostly vertical axis, a 1-block box around the hub;</li>
 * <li>tail rotors: helicopter rotors turning about a horizontal axis;</li>
 * <li>a fuel tank in the middle third of the hull, for vehicles that burn fuel.</li>
 * </ul>
 *
 * At most {@link ComponentHealth#SLOTS} zones; engines are capped at four. A vehicle file that lists
 * its own {@code DamageZones} (M10a) gets exactly those instead, in file order.
 */
public final class DamageZones {

    private static final Map<AircraftInfo, List<DamageZone>> CACHE = new WeakHashMap<>();
    private static final int MAX_ENGINES = 4;
    private static final Vec3 HUB = new Vec3(1.0, 1.0, 1.0);

    private DamageZones() {
    }

    public static List<DamageZone> of(AircraftInfo info) {
        synchronized (CACHE) {
            return CACHE.computeIfAbsent(info, DamageZones::derive);
        }
    }

    /** Drops a type's cached zones after its content changed at runtime (tests, live edits). */
    public static void forget(AircraftInfo info) {
        synchronized (CACHE) {
            CACHE.remove(info);
        }
    }

    static List<DamageZone> derive(AircraftInfo info) {
        if (!info.damageZones.isEmpty()) {
            return placed(info);
        }
        List<DamageZone> zones = new ArrayList<>();
        AABB hull = hull(info);
        List<Vec3[]> engines = new ArrayList<>();
        for (BoundingBoxInfo box : info.extraBoundingBox) {
            if (box.boundingBoxType == BoundingBoxInfo.Type.ENGINE) {
                AABB b = box.getBoundingBox();
                engines.add(new Vec3[] {b.getCenter(), half(b)});
            }
        }
        if (engines.isEmpty()) {
            for (AircraftInfo.EngineOutlet outlet : info.engineOutlets) {
                double s = Math.max(outlet.size(), 0.75);
                Vec3 center = outlet.position().subtract(outlet.direction().scale(1.5 * outlet.size()));
                engines.add(new Vec3[] {center, new Vec3(s, s, s)});
            }
        }
        List<AircraftInfo.DrawnPart> rotors = new ArrayList<>();
        if (info instanceof HeliInfo heli) {
            rotors.addAll(heli.rotorList);
        } else if (info instanceof WingedAircraftInfo winged) {
            rotors.addAll(winged.rotorList);
        }
        if (engines.isEmpty() && info instanceof WingedAircraftInfo) {
            for (AircraftInfo.DrawnPart rotor : rotors) {
                if (!vertical(rotor)) {
                    engines.add(new Vec3[] {rotor.pos.subtract(axis(rotor).scale(1.0)), HUB});
                }
            }
        }
        if (engines.isEmpty()) {
            double len = hull.maxZ - hull.minZ;
            engines.add(new Vec3[] {
                    new Vec3((hull.minX + hull.maxX) / 2, (hull.minY + hull.maxY) / 2, hull.minZ + len / 6),
                    new Vec3((hull.maxX - hull.minX) / 2, (hull.maxY - hull.minY) / 2, len / 6)});
        }
        for (int i = 0; i < engines.size() && i < MAX_ENGINES; i++) {
            zones.add(new DamageZone(ComponentKind.ENGINE, i, engines.get(i)[0], engines.get(i)[1], ""));
        }
        int main = 0;
        int tail = 0;
        for (AircraftInfo.DrawnPart rotor : rotors) {
            if (vertical(rotor)) {
                zones.add(new DamageZone(ComponentKind.MAIN_ROTOR, main++, rotor.pos, HUB, name(rotor)));
            } else if (info instanceof HeliInfo) {
                zones.add(new DamageZone(ComponentKind.TAIL_ROTOR, tail++, rotor.pos, HUB, name(rotor)));
            }
        }
        if (info.maxFuel > 0) {
            double len = hull.maxZ - hull.minZ;
            zones.add(new DamageZone(ComponentKind.FUEL_TANK, 0,
                    new Vec3((hull.minX + hull.maxX) / 2, (hull.minY + hull.maxY) / 2, (hull.minZ + hull.maxZ) / 2),
                    new Vec3(Math.min((hull.maxX - hull.minX) / 2, info.bodyWidth / 2.0), (hull.maxY - hull.minY) / 4,
                            len / 6), ""));
        }
        // Append only: existing engine/rotor/fuel slot indices in saves and items stay valid.
        if (info instanceof io.github.mcheliceport.content.aircraft.PlaneInfo && zones.size() <= ComponentHealth.SLOTS - 3) {
            zones.addAll(AirframeDamage.zones(info, hull));
        }
        if (zones.size() > ComponentHealth.SLOTS) {
            zones = zones.subList(0, ComponentHealth.SLOTS);
        }
        return Collections.unmodifiableList(new ArrayList<>(zones));
    }

    /**
     * Zones from the vehicle's own list. A rotor zone given by part takes a 1-block box around the
     * hub of the rotor drawn by that model group; one naming no rotor of the vehicle is skipped.
     */
    static List<DamageZone> placed(AircraftInfo info) {
        List<DamageZone> zones = new ArrayList<>();
        int[] counts = new int[ComponentKind.values().length];
        for (AircraftInfo.DamageZoneSpec spec : info.damageZones) {
            if (zones.size() >= ComponentHealth.SLOTS) {
                break;
            }
            Vec3 center = spec.center();
            Vec3 halfSize = spec.halfSize();
            if (center == null) {
                AircraftInfo.DrawnPart rotor = rotor(info, spec.part());
                if (rotor == null) {
                    continue;
                }
                center = rotor.pos;
                halfSize = HUB;
            }
            float share = Float.isNaN(spec.share()) ? spec.kind().damageShare : spec.share();
            float health = Float.isNaN(spec.health()) ? ComponentDamage.HEALTH_SHARE_OF_MAX_HP : spec.health();
            zones.add(new DamageZone(spec.kind(), counts[spec.kind().ordinal()]++, center, halfSize, spec.part(),
                    share, health));
        }
        return Collections.unmodifiableList(zones);
    }

    private static AircraftInfo.DrawnPart rotor(AircraftInfo info, String part) {
        List<? extends AircraftInfo.DrawnPart> rotors = info instanceof HeliInfo heli ? heli.rotorList
                : info instanceof WingedAircraftInfo winged ? winged.rotorList : List.of();
        for (AircraftInfo.DrawnPart rotor : rotors) {
            if (part.equals(rotor.modelName)) {
                return rotor;
            }
        }
        return null;
    }

    /** The hull: the main box joined with every extra box. */
    static AABB hull(AircraftInfo info) {
        double w = info.bodyWidth / 2.0;
        AABB hull = new AABB(-w, 0.0, -w, w, Math.max(info.bodyHeight, 0.5), w);
        for (BoundingBoxInfo box : info.extraBoundingBox) {
            hull = hull.minmax(box.getBoundingBox());
        }
        return hull;
    }

    private static Vec3 half(AABB b) {
        return new Vec3(b.getXsize() / 2, b.getYsize() / 2, b.getZsize() / 2);
    }

    private static boolean vertical(AircraftInfo.DrawnPart rotor) {
        Vec3 a = axis(rotor);
        return Math.abs(a.y) >= 0.5;
    }

    private static Vec3 axis(AircraftInfo.DrawnPart rotor) {
        return rotor.rot.lengthSqr() > 1.0E-6 ? rotor.rot.normalize() : new Vec3(0, 1, 0);
    }

    private static String name(AircraftInfo.DrawnPart rotor) {
        return rotor.modelName != null ? rotor.modelName : "";
    }
}
