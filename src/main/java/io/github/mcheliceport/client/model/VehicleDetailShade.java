package io.github.mcheliceport.client.model;

import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.content.aircraft.HeliInfo;
import io.github.mcheliceport.content.aircraft.PlaneInfo;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/** Content-authored machinery anchors give each static hull its own restrained shading. */
public final class VehicleDetailShade {
    private VehicleDetailShade() {}
    public record Zone(Vec3 center, float rx, float ry, float rz, float darkness) {}

    public static List<Zone> zones(AircraftInfo info) {
        List<Zone> result = new ArrayList<>(12);
        for (int i = 0; i < Math.min(4, info.engineOutlets.size()); i++) {
            var outlet = info.engineOutlets.get(i);
            float size = Math.clamp(outlet.size(), 0.2F, 2);
            add(result, outlet.position(), size * 0.65F, size * 0.55F, size * 0.7F, 0.085F);
        }
        if (info.engineOutlets.isEmpty() && info instanceof PlaneInfo plane) {
            for (int i = 0; i < Math.min(4, plane.nozzles.size()); i++) {
                var nozzle = plane.nozzles.get(i);
                add(result, nozzle.pos, 1.2F, 1.1F, 1.2F, 0.085F);
            }
        }
        if (info instanceof HeliInfo heli) {
            int rotors = 0;
            for (int i = 0; i < Math.min(16, heli.rotorList.size()) && rotors < 4; i++) {
                var rotor = heli.rotorList.get(i);
                if (!Double.isFinite(rotor.rot.lengthSqr()) || rotor.rot.lengthSqr() < 0.01 || rotor.rot.y * rotor.rot.y < rotor.rot.lengthSqr() * 0.5) continue;
                add(result, rotor.pos.add(0, -0.25, 0), 0.75F, 0.65F, 0.75F, 0.045F);
                rotors++;
            }
        }
        // Author wheel-area shading from the contact row. No guessed wheels on unsupported content.
        double zMin = Double.POSITIVE_INFINITY, zMax = Double.NEGATIVE_INFINITY, x = 0, y = 0;
        int wheels = 0;
        for (int i = 0; i < Math.min(16, info.wheels.size()); i++) {
            Vec3 p = info.wheels.get(i).pos();
            if (!finite(p)) continue;
            zMin = Math.min(zMin, p.z); zMax = Math.max(zMax, p.z);
            x += Math.abs(p.x); y += p.y; wheels++;
        }
        if (wheels > 0) {
            float halfLength = (float) Math.clamp((zMax - zMin) * 0.5 + 0.5, 0.5, 6);
            for (int side : new int[]{-1, 1})
                add(result, new Vec3(side * x / wheels, y / wheels + 0.65, (zMin + zMax) * 0.5),
                        0.65F, 0.8F, halfLength, 0.05F);
        }
        return List.copyOf(result);
    }

    private static void add(List<Zone> zones, Vec3 p, float rx, float ry, float rz, float darkness) {
        if (zones.size() < 12 && finite(p) && Float.isFinite(rx + ry + rz) && rx > 0 && ry > 0 && rz > 0)
            zones.add(new Zone(p, rx, ry, rz, darkness));
    }
    private static boolean finite(Vec3 p) { return Double.isFinite(p.x + p.y + p.z); }

    /** Strongest zone wins: overlapping outlets cannot accumulate into a black patch. */
    public static float factor(List<Zone> zones, float x, float y, float z) {
        float darkest = 0;
        for (Zone zone : zones) {
            double dx = (x - zone.center.x) / zone.rx, dy = (y - zone.center.y) / zone.ry,
                    dz = (z - zone.center.z) / zone.rz;
            double q = dx * dx + dy * dy + dz * dz;
            if (q >= 1 || !Double.isFinite(q)) continue;
            float weight = (float) ((1 - q) * (1 - q));
            darkest = Math.max(darkest, zone.darkness * weight);
        }
        return 1 - darkest;
    }
}
