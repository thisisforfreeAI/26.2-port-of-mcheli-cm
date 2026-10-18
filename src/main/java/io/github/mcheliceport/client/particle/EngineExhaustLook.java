package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.config.ClientConfig.EffectBudget;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.content.aircraft.PlaneInfo;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/** Outlet selection and small, short-lived exhaust bounds. No inferred helicopter engines. */
public final class EngineExhaustLook {
    private EngineExhaustLook() {}

    public static List<AircraftInfo.EngineOutlet> outlets(AircraftInfo info, float nozzleAngle) {
        if (!info.engineOutlets.isEmpty()) return info.engineOutlets.subList(0, Math.min(4, info.engineOutlets.size()));
        if (!(info instanceof PlaneInfo plane)) return List.of();
        if (plane.nozzles.isEmpty() && plane.exhaustFlames.isEmpty()) return List.of();
        List<AircraftInfo.EngineOutlet> result = new ArrayList<>(4);
        if (!plane.nozzles.isEmpty()) {
            // Same local nozzle direction as the original emitter, including VTOL deflection.
            Vec3 direction = io.github.mcheliceport.util.McheliMath.rotVec3(0, 0, -1, 0, nozzleAngle, 0);
            for (int i = 0; i < Math.min(4, plane.nozzles.size()); i++) {
                result.add(new AircraftInfo.EngineOutlet(plane.nozzles.get(i).pos, direction, 1, true));
            }
        } else if (Math.abs(nozzleAngle) < 1) {
            for (int i = 0; i < Math.min(4, plane.exhaustFlames.size()); i++) {
                result.add(new AircraftInfo.EngineOutlet(plane.exhaustFlames.get(i).pos, new Vec3(0, 0, -1), 1, true));
            }
        }
        return result;
    }

    public static int period(EffectBudget quality, int pressure) {
        return (quality == EffectBudget.LOW ? 4 : quality == EffectBudget.BALANCED ? 2 : 1)
                * (1 + Math.clamp(pressure, 0, 3));
    }
    public static int cap(EffectBudget quality) { return quality == EffectBudget.LOW ? 8 : quality == EffectBudget.BALANCED ? 16 : 24; }
    public static float strength(double throttle) {
        return Double.isFinite(throttle) ? (float) Math.clamp((throttle - 0.1) / 0.8, 0, 1) : 0;
    }
    public static float radius(float size, float strength) {
        return Math.clamp(size, 0.1F, 4) * (0.18F + Math.clamp(strength, 0, 1) * 0.16F);
    }
    public static float fade(float age, int life) {
        return Math.clamp(age / 2, 0, 1) * Math.clamp((life - age) / 6, 0, 1);
    }
}
