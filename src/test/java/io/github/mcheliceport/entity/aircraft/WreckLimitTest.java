package io.github.mcheliceport.entity.aircraft;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

class WreckLimitTest {
    /** A wreck made at {@code since}, {@code dist} blocks from the new one. */
    record W(String name, long since, double dist) {}

    private static List<String> pick(List<W> others, int maxTotal, int maxArea, double radius) {
        return WreckLimit.pick(others, maxTotal, maxArea, Comparator.comparingLong(W::since), w -> w.dist() * w.dist(), radius * radius)
                .stream().map(W::name).toList();
    }

    @Test
    void underTheCapsNothingGoes() {
        assertEquals(List.of(), pick(List.of(new W("a", 1, 10), new W("b", 2, 500)), 5, 3, 100));
    }

    @Test
    void capsOfZeroAreOff() {
        List<W> many = List.of(new W("a", 1, 1), new W("b", 2, 1), new W("c", 3, 1));
        assertEquals(List.of(), pick(many, 0, 0, 100));
    }

    @Test
    void serverWideCapRemovesOldestFirst() {
        List<W> others = List.of(new W("c", 30, 900), new W("a", 10, 900), new W("b", 20, 900));
        assertEquals(List.of("a", "b"), pick(others, 2, 0, 100));
    }

    @Test
    void areaCapOnlyCountsNearbyWrecks() {
        List<W> others = List.of(new W("far", 1, 500), new W("n1", 5, 20), new W("n2", 6, 50), new W("n3", 7, 90));
        assertEquals(List.of("n1", "n2"), pick(others, 0, 2, 100));
    }

    @Test
    void bothCapsDoNotRemoveTwice() {
        List<W> others = List.of(new W("far1", 1, 500), new W("n1", 2, 10), new W("n2", 3, 10), new W("far2", 4, 500));
        // Area cap 2 removes n1; then 3 others + new = 4 > total cap 3, so far1 goes too.
        assertEquals(List.of("n1", "far1"), pick(others, 3, 2, 100));
    }
}
