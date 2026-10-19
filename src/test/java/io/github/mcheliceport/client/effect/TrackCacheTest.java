package io.github.mcheliceport.client.effect;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class TrackCacheTest {
    @Test void WeightedEvictionExpiryAndClockResetsDoNotRetainExcessGeometry() {
        var cache = new TrackCache<String>();
        assertTrue(cache.add(10, 3, "old", 5));
        assertTrue(cache.add(11, 3, "new", 5));
        assertEquals(java.util.List.of("new"), cache.snapshot());
        assertEquals(3, cache.weight());
        assertFalse(cache.add(11, 6, "oversize", 5));
        assertTrue(cache.trim(11, 100, 2, x -> true));
        assertEquals(0, cache.weight());
        cache.add(20, 1, "changed surface", 5);
        cache.add(21, 2, "surviving surface", 5);
        cache.trim(22, 100, 5, x -> !x.startsWith("changed"));
        assertEquals(2, cache.weight());
        cache.trim(121, 100, 5, x -> true);
        assertTrue(cache.snapshot().isEmpty());
        cache.add(140, 2, "future", 5);
        cache.trim(130, 100, 5, x -> true);
        assertEquals(0, cache.weight());
    }
    @Test void FadeIsBoundedAndDisappearsAtTheSameAgeAsCacheExpiry() {
        for (var q : io.github.mcheliceport.config.ClientConfig.EffectBudget.values()) {
            int life = TrackTuning.lifetime(q);
            assertEquals(0, TrackTuning.alpha(-1, life));
            assertEquals(0, TrackTuning.alpha(life, life));
            float previous = 1;
            for (int age = life - 100; age <= life; age++) {
                float alpha = TrackTuning.alpha(age, life);
                assertTrue(alpha <= previous && alpha >= 0); previous = alpha;
            }
        }
    }
}
