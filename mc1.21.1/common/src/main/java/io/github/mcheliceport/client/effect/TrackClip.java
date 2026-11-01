package io.github.mcheliceport.client.effect;

import java.util.ArrayList;
import java.util.List;

/** Clips an oriented imprint to a cached collision top face, preserving its UV coordinates. */
public final class TrackClip {
    private TrackClip() {}
    public record Point(double x, double z, float u, float v) {}

    public static List<Point> rectangle(double x, double z, double dx, double dz, double width, double length) {
        double norm = Math.hypot(dx, dz);
        if (!Double.isFinite(x + z + norm + width + length) || norm < 1.0E-6 || width <= 0 || length <= 0) return List.of();
        dx /= norm; dz /= norm;
        double sx = dz * width / 2, sz = -dx * width / 2, fx = dx * length / 2, fz = dz * length / 2;
        return List.of(new Point(x - sx - fx, z - sz - fz, 0, 0), new Point(x - sx + fx, z - sz + fz, 0, 1),
                new Point(x + sx + fx, z + sz + fz, 1, 1), new Point(x + sx - fx, z + sz - fz, 1, 0));
    }

    public static List<Point> clip(List<Point> source, double x0, double z0, double x1, double z1) {
        List<Point> p = edge(source, 0, x0, true);
        p = edge(p, 0, x1, false); p = edge(p, 1, z0, true); p = edge(p, 1, z1, false);
        if (p.size() < 3 || Math.abs(area(p)) < 1.0E-8) return List.of();
        return List.copyOf(p);
    }

    public static double area(List<Point> points) {
        double sum = 0;
        if (points.size() < 3) return 0;
        Point origin = points.getFirst();
        for (int i = 1; i + 1 < points.size(); i++) {
            Point a = points.get(i), b = points.get(i + 1);
            sum += (a.x - origin.x) * (b.z - origin.z) - (a.z - origin.z) * (b.x - origin.x);
        }
        return sum / 2;
    }

    public static int quads(List<Point> p) { return p.size() < 3 ? 0 : p.size() <= 4 ? 1 : p.size() - 2; }

    private static List<Point> edge(List<Point> in, int axis, double bound, boolean greater) {
        if (in.isEmpty()) return in;
        List<Point> out = new ArrayList<>(8);
        Point previous = in.getLast();
        for (Point current : in) {
            double a = axis == 0 ? previous.x : previous.z, b = axis == 0 ? current.x : current.z;
            boolean insideA = greater ? a >= bound : a <= bound, insideB = greater ? b >= bound : b <= bound;
            if (insideA != insideB) {
                double t = (bound - a) / (b - a);
                out.add(new Point(previous.x + (current.x - previous.x) * t, previous.z + (current.z - previous.z) * t,
                        (float) (previous.u + (current.u - previous.u) * t), (float) (previous.v + (current.v - previous.v) * t)));
            }
            if (insideB) out.add(current);
            previous = current;
        }
        return out;
    }
}
