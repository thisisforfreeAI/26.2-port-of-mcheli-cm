package io.github.mcheliceport.radar;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Upstream's {@code MCH_EntityInfoClientTracker}: the client keeps the latest snapshot of each
 * tracked entity and forgets one that has not been sent for five seconds or 100 snapshots. Upstream
 * swept every ten client ticks; here the sweep runs when the HUDs read the list.
 */
public final class EntityInfoClientTracker {

    private static final long EXPIRATION_MS = 5_000L;
    private static final long MISSING_SEQ_THRESHOLD = 100L;
    private static final Map<Integer, Tracked> TRACKED = new ConcurrentHashMap<>();
    private static volatile long lastAppliedSeq = -1L;
    private static volatile long latestSeqObserved = -1L;

    private EntityInfoClientTracker() {
    }

    public static void update(List<EntityInfo> infos, long snapshotSeq) {
        if (snapshotSeq < lastAppliedSeq) {
            return;
        }
        long now = System.currentTimeMillis();
        for (EntityInfo info : infos) {
            TRACKED.put(info.entityId(), new Tracked(info, now, snapshotSeq));
        }
        latestSeqObserved = Math.max(latestSeqObserved, snapshotSeq);
        lastAppliedSeq = snapshotSeq;
    }

    public static List<EntityInfo> all() {
        long now = System.currentTimeMillis();
        List<EntityInfo> out = new ArrayList<>(TRACKED.size());
        for (Iterator<Tracked> it = TRACKED.values().iterator(); it.hasNext();) {
            Tracked t = it.next();
            if (now - t.lastSeenMillis > EXPIRATION_MS || latestSeqObserved - t.lastSeenSeq > MISSING_SEQ_THRESHOLD) {
                it.remove();
            } else {
                out.add(t.info);
            }
        }
        return out;
    }

    /** Forgets everything, as when the client leaves a world (upstream kept stale entries until they expired). */
    public static void clear() {
        TRACKED.clear();
        lastAppliedSeq = -1L;
        latestSeqObserved = -1L;
    }

    private record Tracked(EntityInfo info, long lastSeenMillis, long lastSeenSeq) {
    }
}
