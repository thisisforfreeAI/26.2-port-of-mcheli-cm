package io.github.mcheliceport.client.sound;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExplosionAudioQueueTest {
    @Test void fourImpactsKeepTheirOnsetsEvenWhenEachHasATail() {
        var queue = new ExplosionAudioQueue<String>();
        for (int i = 0; i < 4; i++) {
            assertTrue(queue.offer(0, 2, true, "impact" + i));
            assertTrue(queue.offer(0, 2, false, "tail" + i));
        }
        assertFalse(queue.offer(0, 2, true, "fifth"));
        var played = new ArrayList<String>();
        queue.drain(1, played::add); assertTrue(played.isEmpty());
        queue.drain(2, played::add);
        assertEquals(List.of("impact0", "impact1", "impact2", "impact3"), played);
        queue.drain(2, played::add); assertEquals(4, played.size(), "Repeated drain must share the tick budget");
        queue.drain(3, played::add); assertEquals(8, played.size());
        assertEquals(0, queue.size());
    }

    @Test void anImpactCanReplaceAnOptionalTailInAFullQueue() {
        var queue = new ExplosionAudioQueue<String>();
        for (int i = 0; i < 32; i++) assertTrue(queue.offer(0, 1, false, "tail"));
        assertFalse(queue.offer(0, 1, false, "extra tail"));
        assertTrue(queue.offer(0, 1, true, "impact"));
        assertEquals(32, queue.size());
        var played = new ArrayList<String>(); queue.drain(1, played::add);
        assertEquals("impact", played.getFirst());
        assertEquals(4, played.size());
    }

    @Test void primaryOnlySaturationCannotGrowOrEvictExistingImpacts() {
        var queue = new ExplosionAudioQueue<Integer>();
        for (int i = 0; i < 32; i++) assertTrue(queue.offer(i / 4, 20, true, i));
        assertFalse(queue.offer(8, 20, true, 99));
        assertEquals(32, queue.size());
        var played = new ArrayList<Integer>(); queue.drain(20, played::add);
        assertEquals(List.of(0, 1, 2, 3), played);
    }

    @Test void sustainedVolleysBoundPlaybackAndDropStaleRumbleBeforeItBecomesAnEcho() {
        var queue = new ExplosionAudioQueue<String>();
        for (int tick = 0; tick < 100; tick++) {
            for (int i = 0; i < 80; i++) if (queue.offer(tick, tick + 2, true, "impact"))
                queue.offer(tick, tick + 6, false, "tail");
            assertTrue(queue.size() <= 32);
            var played = new ArrayList<String>(); queue.drain(tick, played::add);
            assertTrue(played.size() <= 4);
            if (tick >= 2) assertEquals(List.of("impact", "impact", "impact", "impact"), played);
        }
        var afterStall = new ArrayList<String>(); queue.drain(200, afterStall::add);
        assertTrue(afterStall.isEmpty()); assertEquals(0, queue.size());
    }

    @Test void overdueVoicesExpireAndWorldResetRestoresBudgets() {
        var queue = new ExplosionAudioQueue<String>();
        queue.offer(0, 1, true, "impact"); queue.offer(0, 1, false, "tail");
        var played = new ArrayList<String>(); queue.drain(4, played::add);
        assertEquals(List.of("impact"), played);
        for (int i = 0; i < 4; i++) queue.offer(4, 4, true, "new");
        queue.clear(); assertEquals(0, queue.size());
        assertTrue(queue.offer(4, 4, true, "after reset"));
        played.clear(); queue.drain(4, played::add);
        assertEquals(List.of("after reset"), played);
    }
}
