package io.github.mcheliceport.client.effect;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

class BlastMarkColumnsTest {
    @Test void centerFirstTraversalKeepsEveryOriginalFootprintColumnExactlyOnce() {
        for (double x : new double[]{-12.7, -0.1, 0, 0.9, 24.5}) for (double z : new double[]{-0.8, 0, 3.75}) {
            for (float radius : new float[]{0.6F, 1.3F, 4, 6, 7}) {
                var cursor = new BlastMarkColumns(x, z, radius);
                var columns = new HashSet<String>();
                int previousDistance = -1;
                while (cursor.next()) {
                    int dx = cursor.x() - (int) Math.floor(x), dz = cursor.z() - (int) Math.floor(z);
                    int distance = dx * dx + dz * dz;
                    assertTrue(distance >= previousDistance, "Fit the center before the edge");
                    previousDistance = distance;
                    assertTrue(columns.add(cursor.x() + ":" + cursor.z()), "No duplicate columns");
                }
                int width = (int) Math.floor(x + radius) - (int) Math.floor(x - radius) + 1;
                int depth = (int) Math.floor(z + radius) - (int) Math.floor(z - radius) + 1;
                assertEquals(width * depth, columns.size());
                for (int bx = (int) Math.floor(x - radius); bx <= Math.floor(x + radius); bx++)
                    for (int bz = (int) Math.floor(z - radius); bz <= Math.floor(z + radius); bz++)
                        assertTrue(columns.contains(bx + ":" + bz));
                assertTrue(columns.size() <= 225);
                assertFalse(cursor.next());
            }
        }
    }
}
