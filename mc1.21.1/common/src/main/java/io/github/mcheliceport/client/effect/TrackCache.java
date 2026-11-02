package io.github.mcheliceport.client.effect;

import java.util.ArrayDeque;
import java.util.List;
import java.util.function.Predicate;

/** Weighted oldest-first cache; time resets, expiry and quality reductions cannot retain excess faces. */
final class TrackCache<T> {
    private record Entry<T>(long born, int cost, T value) {}
    private final ArrayDeque<Entry<T>> entries = new ArrayDeque<>();
    private int weight;
    int weight() { return this.weight; }
    void clear() { this.entries.clear(); this.weight = 0; }
    boolean add(long born, int cost, T value, int limit) {
        if (cost <= 0 || cost > limit) return false;
        while (this.weight + cost > limit) this.weight -= this.entries.removeFirst().cost;
        this.entries.addLast(new Entry<>(born, cost, value)); this.weight += cost;
        return true;
    }
    boolean trim(long now, int lifetime, int limit, Predicate<T> valid) {
        boolean changed = this.entries.removeIf(e -> {
            boolean remove = now < e.born || now - e.born >= lifetime || !valid.test(e.value);
            if (remove) this.weight -= e.cost;
            return remove;
        });
        while (this.weight > limit) { this.weight -= this.entries.removeFirst().cost; changed = true; }
        return changed;
    }
    List<T> snapshot() { return this.entries.stream().map(Entry::value).toList(); }
}
