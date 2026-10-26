package io.github.mcheliceport.gametest;

import static io.github.mcheliceport.gametest.GameTestSupport.check;

import io.github.mcheliceport.content.ContentType;
import io.github.mcheliceport.damage.ComponentEffects;
import io.github.mcheliceport.damage.ComponentHealth;
import io.github.mcheliceport.damage.ComponentKind;
import io.github.mcheliceport.damage.ComponentView;
import io.github.mcheliceport.damage.VehicleComponents;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.item.AircraftItem;
import io.github.mcheliceport.registry.ModItems;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;

/** M9e: what disabled engines, rotors and fuel tanks do to stock vehicles. */
final class ComponentEffectsGameTests {
    private ComponentEffectsGameTests() { }

    static void register() {
        ModGameTests.test("component_effects_twin_engine_plane", 10, ComponentEffectsGameTests::twinPlane);
        ModGameTests.test("component_effects_heli_autorotates", 40, ComponentEffectsGameTests::autorotation);
        ModGameTests.test("component_effects_tail_rotor_and_leak", 60, ComponentEffectsGameTests::tailAndLeak);
    }

    private static AircraftEntity spawn(GameTestHelper h, AircraftItem item, Vec3 rel) {
        Vec3 p = h.absoluteVec(rel);
        AircraftEntity ac = item.createAircraft(h.getLevel(), p.x, p.y, p.z);
        h.getLevel().addFreshEntity(ac);
        ac.tick();
        return ac;
    }

    private static AircraftEntity heli(GameTestHelper h, Vec3 rel) {
        var item = (AircraftItem) ModItems.contentItems().stream()
                .filter(c -> c.type() == ContentType.HELICOPTER && c.contentName().equals("ah-64"))
                .findFirst().orElseThrow().item().get();
        return spawn(h, item, rel);
    }

    /** Sets every component of a kind to a health level (0 disabled, 255 intact). */
    private static void set(AircraftEntity ac, ComponentKind kind, int level) {
        List<ComponentView> views = VehicleComponents.of(ac);
        long packed = ac.getComponentHealth();
        for (int i = 0; i < views.size(); i++) {
            if (views.get(i).kind() == kind) {
                packed = ComponentHealth.withLevel(packed, i, level);
            }
        }
        ac.setComponentHealth(packed);
    }

    private static void twinPlane(GameTestHelper h) {
        AircraftEntity plane = null;
        for (var c : ModItems.contentItems()) {
            if (c.type() != ContentType.PLANE) {
                continue;
            }
            AircraftEntity ac = spawn(h, (AircraftItem) c.item().get(), new Vec3(8, 2, 8));
            long engines = VehicleComponents.of(ac).stream().filter(v -> v.kind() == ComponentKind.ENGINE).count();
            if (engines == 2) {
                plane = ac;
                break;
            }
            GameTestSupport.cleanUp(ac);
        }
        check(h, plane != null, "the stock pack has a twin-engine plane");
        plane.setCurrentThrottle(1.0);
        check(h, plane.getCurrentThrottle() == 1.0, "an intact plane takes full throttle");
        List<ComponentView> views = VehicleComponents.of(plane);
        int first = views.indexOf(views.stream().filter(v -> v.kind() == ComponentKind.ENGINE).findFirst().orElseThrow());
        plane.setComponentHealth(ComponentHealth.withLevel(ComponentHealth.FULL, first, 0));
        plane.setCurrentThrottle(1.0);
        check(h, Math.abs(plane.getCurrentThrottle() - 0.5) < 1e-9,
                "one engine out leaves half thrust, so it flies home: " + plane.getCurrentThrottle());
        set(plane, ComponentKind.ENGINE, 0);
        plane.setCurrentThrottle(1.0);
        check(h, plane.getCurrentThrottle() == 0.0, "both engines out: it glides");
        plane.repair(plane.getMaxHP());
        plane.setCurrentThrottle(1.0);
        check(h, plane.getCurrentThrottle() == 1.0, "a repair restores the thrust");
        GameTestSupport.cleanUp(plane);
        h.succeed();
    }

    private static void autorotation(GameTestHelper h) {
        AircraftEntity dead = heli(h, new Vec3(6, 60, 6));
        AircraftEntity intact = heli(h, new Vec3(18, 60, 18));
        set(dead, ComponentKind.ENGINE, 0);
        check(h, ComponentEffects.autorotating(dead) && !ComponentEffects.autorotating(intact),
                "only the engine-out helicopter autorotates");
        dead.setCurrentThrottle(1.0);
        check(h, dead.getCurrentThrottle() == 0.0, "and it has no power");
        dead.setDeltaMovement(0.0, -1.0, 0.0);
        intact.setDeltaMovement(0.0, -1.0, 0.0);
        double startY = dead.getY();
        double[] worst = {0.0, 0.0};
        h.onEachTick(() -> {
            worst[0] = Math.min(worst[0], dead.getDeltaMovement().y);
            worst[1] = Math.min(worst[1], intact.getDeltaMovement().y);
        });
        h.runAfterDelay(30, () -> {
            check(h, dead.getY() < startY - 3.0, "it still comes down: " + (startY - dead.getY()));
            check(h, dead.getDeltaMovement().y >= -ComponentEffects.AUTOROTATION_SINK - 1e-6,
                    "but sinks no faster than autorotation allows: " + dead.getDeltaMovement().y);
            check(h, worst[1] < -ComponentEffects.AUTOROTATION_SINK,
                    "an unpowered intact one would fall faster (control): " + worst[1]);
            check(h, !dead.isDestroyed(), "and it is not destroyed by losing its engines");
            GameTestSupport.cleanUp(dead);
            GameTestSupport.cleanUp(intact);
            h.succeed();
        });
    }

    private static void tailAndLeak(GameTestHelper h) {
        AircraftEntity heli = heli(h, new Vec3(12, 2, 12));
        double top = 1.0;
        heli.setCurrentThrottle(1.0);
        check(h, ComponentEffects.tailDrift(heli, 0.0, top) == 0.0F, "an intact tail rotor does not drift");
        set(heli, ComponentKind.TAIL_ROTOR, 0);
        heli.setCurrentThrottle(1.0);
        check(h, heli.getCurrentThrottle() == 1.0, "a lost tail rotor keeps the engine's power");
        float hover = ComponentEffects.tailDrift(heli, 0.0, top);
        float fast = ComponentEffects.tailDrift(heli, ComponentEffects.TAIL_DRIFT_CANCEL_SPEED * top, top);
        check(h, hover == ComponentEffects.TAIL_DRIFT_DISABLED, "it spins in a hover: " + hover);
        check(h, fast == 0.0F, "and flies straight at speed, so it gets home: " + fast);

        set(heli, ComponentKind.FUEL_TANK, 0);
        heli.setCurrentThrottle(0.0); // no fuel burnt by the engine, only leaked
        heli.setFuel(heli.getMaxFuel());
        int full = heli.getFuel();
        check(h, full > 0, "the AH-64 carries fuel");
        h.runAfterDelay(50, () -> {
            int lost = full - heli.getFuel();
            int step = Math.max(1, heli.getMaxFuel() / 100);
            check(h, lost >= 2 * step && lost <= 3 * step, "a holed tank leaks 1% a second: lost " + lost);
            check(h, !heli.isDestroyed(), "and does not explode");
            GameTestSupport.cleanUp(heli);
            h.succeed();
        });
    }
}
