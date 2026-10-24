package io.github.mcheliceport.client.model;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Model-space crease shading, baked on the model loader rather than ray traced per frame. */
public final class VehicleSurfaceShade {
    private VehicleSurfaceShade() {}
    public static final int BASE_COLOR = 0xFFBFBFBF;
    private static final Map<McheliModel.Group, int[]> CACHE = new WeakHashMap<>();
    private record Position(float x, float y, float z) {}
    private static final class Neighbours {
        double x, y, z, distance;
    }
    public static void prepare(McheliModel model) { prepare(model, java.util.List.of()); }
    public static void prepare(McheliModel model, java.util.List<VehicleDetailShade.Zone> zones) {
        boolean hasBody = model.containsPart("$body");
        for (var group : model.groups) {
            float[] shade = bake(group.data());
            int[] colors = shade == null ? null : new int[shade.length];
            if (colors != null) for (int i = 0; i < shade.length; i++) {
                float value = shade[i];
                if (!zones.isEmpty() && (!hasBody || group.name().equalsIgnoreCase("$body"))) {
                    int at = i * McheliModel.STRIDE;
                    value = Math.max(0.84F, value * VehicleDetailShade.factor(zones,
                            group.data()[at], group.data()[at + 1], group.data()[at + 2]));
                }
                colors[i] = tint(BASE_COLOR, value);
            }
            synchronized (CACHE) { CACHE.put(group, colors); }
        }
    }
    /** No lazy bake on the render thread. Oversized addon groups use ordinary lighting. */
    public static int[] get(McheliModel.Group group) {
        synchronized (CACHE) { return CACHE.get(group); }
    }
    static float[] bake(float[] data) {
        int count = data.length / McheliModel.STRIDE;
        if (count > 60000) return null;
        var neighbours = new HashMap<Position, Neighbours>();
        for (int t = 0; t + 24 <= data.length; t += 24) {
            for (int corner = 0; corner < 3; corner++) {
                int i = t + corner * 8;
                var n = neighbours.computeIfAbsent(position(data, i), ignored -> new Neighbours());
                for (int other = 1; other <= 2; other++) {
                    int j = t + ((corner + other) % 3) * 8;
                    double x = data[j] - data[i], y = data[j + 1] - data[i + 1], z = data[j + 2] - data[i + 2];
                    n.x += x; n.y += y; n.z += z;
                    n.distance += Math.sqrt(x * x + y * y + z * z);
                }
            }
        }
        float[] result = new float[count];
        for (int v = 0; v < count; v++) {
            int i = v * 8;
            var n = neighbours.get(position(data, i));
            double normalLength = Math.sqrt(data[i + 5] * data[i + 5] + data[i + 6] * data[i + 6] + data[i + 7] * data[i + 7]);
            double crease = n == null || n.distance < 1e-9 || normalLength < 1e-9 ? 0
                    : (n.x * data[i + 5] + n.y * data[i + 6] + n.z * data[i + 7]) / (n.distance * normalLength);
            result[v] = Float.isFinite((float) crease) ? (float) Math.clamp(1 - crease * (crease > 0 ? 0.18 : 0.04), 0.88, 1.03) : 1;
        }
        return result;
    }
    private static Position position(float[] d, int i) {
        return new Position(d[i] == 0 ? 0 : d[i], d[i + 1] == 0 ? 0 : d[i + 1], d[i + 2] == 0 ? 0 : d[i + 2]);
    }
    public static int tint(int color, float shade) {
        int r = Math.clamp(Math.round(((color >>> 16) & 255) * shade), 0, 255);
        int g = Math.clamp(Math.round(((color >>> 8) & 255) * shade), 0, 255);
        int b = Math.clamp(Math.round((color & 255) * shade), 0, 255);
        return (color & 0xFF000000) | r << 16 | g << 8 | b;
    }
}
