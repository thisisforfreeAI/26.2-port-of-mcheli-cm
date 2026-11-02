package io.github.mcheliceport.client.effect;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/** Per-contact distance sampling. No backlog, stationary stamping or teleport/air-gap bridges. */
public final class TrackPath {
    public record Sample(Vec3 position, Vec3 direction) {}
    private Vec3 previous;
    private long tick;
    private double carry;
    public void clear() { previous = null; carry = 0; }

    public List<Sample> update(Vec3 current, boolean grounded, long now, double spacing) {
        if (!grounded || !Double.isFinite(current.x + current.y + current.z) || !Double.isFinite(spacing) || spacing <= 0) {
            clear(); return List.of();
        }
        if (previous == null || now < tick || now - tick > 8 || current.distanceToSqr(previous) > 16
                || Math.abs(current.y - previous.y) > 1.5) {
            previous = current; tick = now; carry = 0; return List.of();
        }
        if (now == tick) return List.of();
        Vec3 delta = current.subtract(previous);
        double distance = Math.hypot(delta.x, delta.z);
        tick = now;
        if (distance < 1.0E-7) { previous = current; return List.of(); }
        int count = (int) Math.floor((carry + distance) / spacing);
        double first = spacing - carry;
        List<Sample> samples = new ArrayList<>(Math.min(count, 2));
        Vec3 heading = new Vec3(delta.x / distance, 0, delta.z / distance);
        for (int i = Math.max(0, count - 2); i < count; i++) {
            double t = Math.clamp((first + i * spacing) / distance, 0, 1);
            samples.add(new Sample(previous.lerp(current, t), heading));
        }
        carry = (carry + distance) % spacing; previous = current;
        return samples;
    }
}
