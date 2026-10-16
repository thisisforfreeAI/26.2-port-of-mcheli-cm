package io.github.mcheliceport.entity.uav;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

/** Last known positions permit a bounded reconnect after a paired UAV's chunk unloads. */
public final class UavPositionIndex extends SavedData {
    public record Entry(UUID id, BlockPos pos) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(b -> b.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(Entry::id), BlockPos.CODEC.fieldOf("pos").forGetter(Entry::pos)).apply(b, Entry::new));
    }
    public static final Codec<UavPositionIndex> CODEC = Entry.CODEC.listOf().xmap(UavPositionIndex::new, UavPositionIndex::entries);
    public static final SavedDataType<UavPositionIndex> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("mcheli_ce_port", "uav_positions"), UavPositionIndex::new, CODEC);
    private final Map<UUID, BlockPos> positions = new HashMap<>();
    public UavPositionIndex() { }
    private UavPositionIndex(List<Entry> entries) { for (var e : entries) { positions.put(e.id(), e.pos()); } }
    private List<Entry> entries() { return positions.entrySet().stream().map(e -> new Entry(e.getKey(), e.getValue())).toList(); }
    public static UavPositionIndex get(ServerLevel level) { return level.getDataStorage().computeIfAbsent(TYPE); }
    public @Nullable BlockPos position(UUID id) { return positions.get(id); }
    public void record(UUID id, BlockPos pos) {
        if (!pos.equals(positions.put(id, pos.immutable()))) { setDirty(); }
    }
    public void remove(UUID id) { if (positions.remove(id) != null) { setDirty(); } }
    public static void update(AircraftEntity ac) {
        if (ac.level() instanceof ServerLevel sl && ac.isUAV() && ac.pairedController() != null && !ac.isRemoved()) {
            get(sl).record(ac.getUUID(), ac.blockPosition());
        }
    }
}
