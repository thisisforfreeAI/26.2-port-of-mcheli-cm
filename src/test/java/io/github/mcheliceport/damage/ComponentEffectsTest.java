package io.github.mcheliceport.damage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.mcheliceport.damage.ComponentEffects.Frame;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** M9e: how hurt components limit a vehicle, on plain values. */
class ComponentEffectsTest {

    private static List<ComponentView> views(Object... kindsAndStates) {
        List<ComponentView> out = new ArrayList<>();
        for (int i = 0; i < kindsAndStates.length; i += 2) {
            out.add(new ComponentView((ComponentKind) kindsAndStates[i], 0, (ComponentState) kindsAndStates[i + 1],
                    1.0F, Vec3.ZERO, Vec3.ZERO, "", -1L));
        }
        return out;
    }

    private static final ComponentKind E = ComponentKind.ENGINE;
    private static final ComponentKind R = ComponentKind.MAIN_ROTOR;
    private static final ComponentKind T = ComponentKind.TAIL_ROTOR;
    private static final ComponentState OK = ComponentState.OK;
    private static final ComponentState DMG = ComponentState.DAMAGED;
    private static final ComponentState OUT = ComponentState.DISABLED;

    @Test
    void planesKeepTheMeanOfTheirEngines() {
        assertEquals(1.0, ComponentEffects.throttleCap(Frame.PLANE, views(E, OK, E, OK)), 1e-9);
        assertEquals(0.5, ComponentEffects.throttleCap(Frame.PLANE, views(E, OK, E, OUT)), 1e-9);
        assertEquals(0.6, ComponentEffects.throttleCap(Frame.PLANE, views(E, DMG)), 1e-6);
        assertEquals(0.0, ComponentEffects.throttleCap(Frame.PLANE, views(E, OUT, E, OUT)), 1e-9);
        assertEquals(1.0, ComponentEffects.throttleCap(Frame.PLANE, views()), 1e-9);
    }

    @Test
    void helicoptersAlsoNeedTheirMainRotor() {
        assertEquals(0.7, ComponentEffects.throttleCap(Frame.HELI, views(E, DMG, R, OK)), 1e-6);
        assertEquals(0.75, ComponentEffects.throttleCap(Frame.HELI, views(E, OK, R, DMG)), 1e-9);
        assertEquals(0.0, ComponentEffects.throttleCap(Frame.HELI, views(E, OK, R, OUT)), 1e-9);
        assertEquals(1.0, ComponentEffects.throttleCap(Frame.HELI, views(E, OK, R, OK, T, OUT)), 1e-9);
    }

    @Test
    void groundVehiclesCrawlOnDeadEngines() {
        assertEquals(0.7, ComponentEffects.throttleCap(Frame.GROUND, views(E, DMG)), 1e-9);
        assertEquals(0.7, ComponentEffects.throttleCap(Frame.GROUND, views(E, OK, E, OUT)), 1e-9);
        assertEquals(0.25, ComponentEffects.throttleCap(Frame.GROUND, views(E, OUT)), 1e-9);
        // A main rotor means nothing to a ground vehicle.
        assertEquals(1.0, ComponentEffects.throttleCap(Frame.GROUND, views(E, OK, R, OUT)), 1e-9);
    }

    @Test
    void tailDriftFollowsTorqueAndFadesWithSpeed() {
        assertEquals(0.0F, ComponentEffects.tailDrift(OK, 1.0, 0.0, 2.0));
        assertEquals(2.0F, ComponentEffects.tailDrift(OUT, 1.0, 0.0, 2.0), 1e-6);
        assertEquals(0.25F, ComponentEffects.tailDrift(DMG, 0.5, 0.0, 2.0), 1e-6);
        assertEquals(1.0F, ComponentEffects.tailDrift(OUT, 1.0, 0.6, 2.0), 1e-6);
        assertEquals(0.0F, ComponentEffects.tailDrift(OUT, 1.0, 1.2, 2.0), 1e-6);
        assertEquals(0.0F, ComponentEffects.tailDrift(OUT, 1.0, 5.0, 2.0), 1e-6);
        assertEquals(0.0F, ComponentEffects.tailDrift(OUT, 0.0, 0.0, 2.0), 1e-6);
    }

    @Test
    void leaksEveryFiveSecondsOrEverySecond() {
        int damaged = 0;
        int disabled = 0;
        for (long t = 0; t < 200; t++) {
            damaged += ComponentEffects.leakAmount(DMG, t, 1000);
            disabled += ComponentEffects.leakAmount(OUT, t, 1000);
            assertEquals(0, ComponentEffects.leakAmount(OK, t, 1000));
        }
        assertEquals(20, damaged);
        assertEquals(100, disabled);
        assertEquals(1, ComponentEffects.leakAmount(OUT, 0, 50));
        assertEquals(0, ComponentEffects.leakAmount(OUT, 0, 0));
    }
}
