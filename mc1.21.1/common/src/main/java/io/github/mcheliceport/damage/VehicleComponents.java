package io.github.mcheliceport.damage;

import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * M9 component damage as other code reads it: engines, rotors and fuel tanks with their own
 * health, synced to every client. Read-only; the M8 damage marks, the HUD and addons build on this.
 * See {@code docs/M9_COMPONENT_DAMAGE.md}.
 */
public final class VehicleComponents {

    /** Told when a vehicle's synced component health changes on this side, including repairs. */
    @FunctionalInterface
    public interface Listener {
        void changed(AircraftEntity vehicle, List<ComponentView> before, List<ComponentView> after);
    }

    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();

    private VehicleComponents() {
    }

    /** The vehicle's components, in a fixed order per vehicle type; empty before its type loads. */
    public static List<ComponentView> of(AircraftEntity vehicle) {
        return views(vehicle, vehicle.getComponentHealth());
    }

    /** The first component of a kind, or null when the vehicle has none. */
    public static ComponentView first(AircraftEntity vehicle, ComponentKind kind) {
        for (ComponentView v : of(vehicle)) {
            if (v.kind() == kind) {
                return v;
            }
        }
        return null;
    }

    public static void addListener(Listener listener) {
        LISTENERS.add(listener);
    }

    public static void removeListener(Listener listener) {
        LISTENERS.remove(listener);
    }

    /** Called by the vehicle when its synced component health changed. */
    public static void notifyChanged(AircraftEntity vehicle, long before, long after) {
        if (LISTENERS.isEmpty() || before == after) {
            return;
        }
        List<ComponentView> was = views(vehicle, before);
        List<ComponentView> now = views(vehicle, after);
        for (Listener listener : LISTENERS) {
            listener.changed(vehicle, was, now);
        }
    }

    private static List<ComponentView> views(AircraftEntity vehicle, long packed) {
        AircraftInfo info = vehicle.getAcInfo();
        if (info == null) {
            return List.of();
        }
        List<DamageZone> zones = DamageZones.of(info);
        List<ComponentView> views = new ArrayList<>(zones.size());
        for (int i = 0; i < zones.size(); i++) {
            DamageZone z = zones.get(i);
            float health = ComponentHealth.health(packed, i);
            views.add(new ComponentView(z.kind(), z.index(), ComponentState.of(health), health, z.center(),
                    z.halfSize(), z.partName(), vehicle.getComponentChangeTick(i)));
        }
        return List.copyOf(views);
    }
}
