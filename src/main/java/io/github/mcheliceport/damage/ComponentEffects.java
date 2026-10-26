package io.github.mcheliceport.damage;

import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.aircraft.WingedAircraftEntity;
import io.github.mcheliceport.entity.heli.HeliEntity;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * What damaged components do to a vehicle (M9e), so a damaged vehicle can still limp home:
 *
 * <ul>
 * <li>engines cap the throttle: planes by the mean of their engines (OK 1, DAMAGED 0.6, DISABLED 0),
 * so a twin jet with one engine out keeps half thrust and one with none glides; helicopters the
 * same with DAMAGED 0.7; ground vehicles and ships 0.7 when damaged and 0.25 when every engine is
 * out, so they crawl;</li>
 * <li>a damaged main rotor caps a helicopter's throttle at 0.75; a helicopter with no engine left or
 * a disabled main rotor autorotates: no power, but it sinks at most {@link #AUTOROTATION_SINK};</li>
 * <li>a damaged or disabled tail rotor makes a powered helicopter yaw against its main rotor, less
 * the faster it flies forward;</li>
 * <li>a damaged fuel tank leaks 1% of its capacity every 5 seconds, a disabled one every second. It
 * never explodes on its own.</li>
 * </ul>
 *
 * Everything reads the synced component health, so the pilot's client and the server agree.
 */
public final class ComponentEffects {

    /** Blocks a tick an autorotating helicopter may sink at most. */
    public static final double AUTOROTATION_SINK = 0.4;
    /** Degrees a tick of tail-rotor drift at full throttle and no forward speed. */
    public static final float TAIL_DRIFT_DAMAGED = 0.5F;
    public static final float TAIL_DRIFT_DISABLED = 2.0F;
    /** Share of the type's top speed at which forward flight cancels tail drift. */
    public static final double TAIL_DRIFT_CANCEL_SPEED = 0.6;
    public static final int LEAK_TICKS_DAMAGED = 100;
    public static final int LEAK_TICKS_DISABLED = 20;

    private ComponentEffects() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ComponentEffects::onEntityTick);
    }

    private static void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof AircraftEntity ac && ac.level() instanceof ServerLevel level
                && !ac.isDestroyed()) {
            leak(ac, level.getGameTime());
        }
    }

    /** The kind of vehicle, for which rules apply. */
    public enum Frame {
        HELI,
        PLANE,
        GROUND
    }

    public static Frame frame(AircraftEntity ac) {
        if (ac instanceof HeliEntity) {
            return Frame.HELI;
        }
        return ac instanceof WingedAircraftEntity ? Frame.PLANE : Frame.GROUND;
    }

    /** The highest throttle the vehicle's components allow, 1 when intact. */
    public static double throttleCap(AircraftEntity ac) {
        List<ComponentView> views = VehicleComponents.of(ac);
        return views.isEmpty() ? 1.0 : throttleCap(frame(ac), views);
    }

    public static double throttleCap(Frame frame, List<ComponentView> views) {
        int engines = 0;
        int disabled = 0;
        boolean damaged = false;
        double sum = 0.0;
        float damagedFactor = frame == Frame.HELI ? 0.7F : 0.6F;
        double cap = 1.0;
        for (ComponentView v : views) {
            if (v.kind() == ComponentKind.ENGINE) {
                engines++;
                switch (v.state()) {
                    case OK -> sum += 1.0;
                    case DAMAGED -> {
                        sum += damagedFactor;
                        damaged = true;
                    }
                    case DISABLED -> disabled++;
                }
            } else if (v.kind() == ComponentKind.MAIN_ROTOR && frame == Frame.HELI) {
                cap = Math.min(cap, switch (v.state()) {
                    case OK -> 1.0;
                    case DAMAGED -> 0.75;
                    case DISABLED -> 0.0;
                });
            }
        }
        if (engines > 0) {
            if (frame == Frame.GROUND) {
                cap = Math.min(cap, disabled == engines ? 0.25 : damaged || disabled > 0 ? 0.7 : 1.0);
            } else {
                cap = Math.min(cap, sum / engines);
            }
        }
        return cap;
    }

    /** Whether a helicopter has lost all power to its rotor and autorotates. */
    public static boolean autorotating(AircraftEntity ac) {
        return ac instanceof HeliEntity && !ac.isDestroyed() && throttleCap(ac) <= 0.0;
    }

    /**
     * Degrees of yaw a tick from a hurt tail rotor: its drift, times the throttle (the main rotor's
     * torque), less as forward speed approaches {@link #TAIL_DRIFT_CANCEL_SPEED} of top speed.
     */
    public static float tailDrift(ComponentState tail, double throttle, double speed, double maxSpeed) {
        float drift = switch (tail) {
            case OK -> 0.0F;
            case DAMAGED -> TAIL_DRIFT_DAMAGED;
            case DISABLED -> TAIL_DRIFT_DISABLED;
        };
        if (drift == 0.0F || maxSpeed <= 0.0) {
            return 0.0F;
        }
        double cancel = Math.clamp(speed / (TAIL_DRIFT_CANCEL_SPEED * maxSpeed), 0.0, 1.0);
        return (float) (drift * Math.clamp(throttle, 0.0, 1.0) * (1.0 - cancel));
    }

    public static float tailDrift(AircraftEntity ac, double speed, double maxSpeed) {
        ComponentView tail = VehicleComponents.first(ac, ComponentKind.TAIL_ROTOR);
        return tail == null ? 0.0F : tailDrift(tail.state(), ac.getCurrentThrottle(), speed, maxSpeed);
    }

    /** Fuel lost this tick from a hurt fuel tank, out of {@code maxFuel}. */
    public static int leakAmount(ComponentState tank, long tick, int maxFuel) {
        int every = switch (tank) {
            case OK -> 0;
            case DAMAGED -> LEAK_TICKS_DAMAGED;
            case DISABLED -> LEAK_TICKS_DISABLED;
        };
        if (every == 0 || maxFuel <= 0 || Math.floorMod(tick, every) != 0) {
            return 0;
        }
        return Math.max(1, maxFuel / 100);
    }

    private static void leak(AircraftEntity ac, long tick) {
        ComponentView tank = VehicleComponents.first(ac, ComponentKind.FUEL_TANK);
        if (tank == null || tank.state() == ComponentState.OK || ac.getFuel() <= 0) {
            return;
        }
        int lost = leakAmount(tank.state(), tick + ac.getId(), ac.getMaxFuel());
        if (lost > 0) {
            ac.setFuel(ac.getFuel() - lost);
        }
    }
}
