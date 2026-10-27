package io.github.mcheliceport.effect;

import io.github.mcheliceport.content.weapon.WeaponInfo;
import java.util.LinkedHashMap;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Cosmetic rules and bounded deduplication. No NTM classes or gameplay state. */
public final class VehicleBloodRules {
    public static final byte DIRECT = 0, BLAST = 1, RAM = 2;
    private VehicleBloodRules() {}

    public static boolean conventional(@Nullable WeaponInfo info) {
        return info != null && !info.useHBM && info.payloadNTM == WeaponInfo.Payload.NONE
                && !info.effectOnly && !"hbm".equals(info.requiredMod) && !"ntm".equals(info.requiredMod);
    }

    public static boolean allowedNamespace(String namespace) {
        return !namespace.equals("hbm") && !namespace.equals("ntm");
    }

    public static final class Gate {
        private record Entry(long tick, boolean admitted) {}
        private final LinkedHashMap<UUID, Entry> recent = new LinkedHashMap<>();
        private final int perTick;
        private long tick = Long.MIN_VALUE;
        private int births;
        public Gate(int perTick) { this.perTick = perTick; }
        public boolean admit(UUID id, long now) {
            if (now < tick) recent.clear();
            if (now != tick) { tick = now; births = 0; }
            while (!recent.isEmpty() && now - recent.firstEntry().getValue().tick >= 200) recent.pollFirstEntry();
            if (recent.containsKey(id)) return false;
            if (recent.size() >= 1024) recent.pollFirstEntry();
            recent.put(id, new Entry(now, births < perTick)); // Budget-rejected kills must not be retried later.
            if (births >= perTick) return false;
            births++;
            return true;
        }
        public boolean contains(UUID id) { var entry = recent.get(id); return entry != null && entry.admitted; }
        public int size() { return recent.size(); }
    }
}
