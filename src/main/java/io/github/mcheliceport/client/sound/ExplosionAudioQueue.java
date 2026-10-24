package io.github.mcheliceport.client.sound;

import java.util.ArrayList;
import java.util.function.Consumer;

/** Small bounded scheduler: opening impacts win over optional rumble during a volley. */
public final class ExplosionAudioQueue<T> {
    private record Pending<T>(long due, boolean primary, T voice) {}
    private final ArrayList<Pending<T>> pending = new ArrayList<>(32);
    private long admissionTick = Long.MIN_VALUE, playbackTick = Long.MIN_VALUE;
    private int admitted, played;

    boolean offer(long now, long due, boolean primary, T voice) {
        if (admissionTick != now) { admissionTick = now; admitted = 0; }
        if (primary && admitted >= 4) return false;
        if (pending.size() >= 32) {
            if (!primary) return false;
            int tail = -1;
            for (int i = 0; i < pending.size(); i++) if (!pending.get(i).primary) { tail = i; break; }
            if (tail < 0) return false;
            pending.remove(tail);
        }
        pending.add(new Pending<>(due, primary, voice));
        if (primary) admitted++;
        return true;
    }

    void drain(long now, Consumer<T> play) {
        if (playbackTick != now) { playbackTick = now; played = 0; }
        // Do not play stale tails or a long backlog of impacts after a client stall.
        pending.removeIf(p -> now > p.due && now - p.due > (p.primary ? 4 : 2));
        while (played < 4) {
            int next = -1;
            for (int i = 0; i < pending.size(); i++) {
                var p = pending.get(i);
                if (p.due > now) continue;
                if (next < 0 || p.primary && !pending.get(next).primary
                        || p.primary == pending.get(next).primary && p.due < pending.get(next).due) next = i;
            }
            if (next < 0) return;
            T voice = pending.remove(next).voice;
            played++;
            play.accept(voice);
        }
    }

    public int size() { return pending.size(); }
    void clear() {
        pending.clear(); admissionTick = playbackTick = Long.MIN_VALUE; admitted = played = 0;
    }
}
