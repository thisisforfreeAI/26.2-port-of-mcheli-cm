package io.github.mcheliceport.client.effect;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Center-first traversal of the original square footprint, without per-impact column arrays. */
final class BlastMarkColumns {
    private record Offset(int x, int z) {}
    private static final List<Offset> OFFSETS;
    static {
        var offsets = new ArrayList<Offset>(225);
        for (int x = -7; x <= 7; x++) for (int z = -7; z <= 7; z++) offsets.add(new Offset(x, z));
        offsets.sort(Comparator.comparingInt(o -> o.x * o.x + o.z * o.z));
        OFFSETS = List.copyOf(offsets);
    }
    private final int centerX, centerZ, minX, maxX, minZ, maxZ;
    private int cursor, x, z;

    BlastMarkColumns(double x, double z, float radius) {
        centerX = (int) Math.floor(x); centerZ = (int) Math.floor(z);
        minX = (int) Math.floor(x - radius); maxX = (int) Math.floor(x + radius);
        minZ = (int) Math.floor(z - radius); maxZ = (int) Math.floor(z + radius);
    }
    boolean next() {
        while (cursor < OFFSETS.size()) {
            var offset = OFFSETS.get(cursor++);
            x = centerX + offset.x; z = centerZ + offset.z;
            if (x >= minX && x <= maxX && z >= minZ && z <= maxZ) return true;
        }
        return false;
    }
    int x() { return x; }
    int z() { return z; }
}
