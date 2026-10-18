package io.github.mcheliceport.wingman.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.wingman.block.MarkerType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

/**
 * Upstream's {@code MarkerRegistry}: every wingman marker in a dimension, with its type, ID and
 * base, saved with the dimension so missions can find markers in unloaded chunks.
 *
 * <p>A base marker (ID {@code alpha}) is the parent; runway ends, parking spots, helipads and
 * waypoints name it as their base. A route node such as {@code takeoff:alpha} resolves to the base's
 * child of the wanted type, else to a marker of that type whose own ID is the name.
 */
public final class MarkerRegistry extends SavedData {

    /** One marker. {@code parkingHeading} is -1 (any), 0 (north), 1 (east), 2 (south) or 3 (west). */
    public record MarkerInfo(BlockPos pos, MarkerType type, String id, String baseId, int parkingHeading) {
        public static final Codec<MarkerInfo> CODEC = RecordCodecBuilder.create(b -> b.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(MarkerInfo::pos),
                Codec.STRING.xmap(MarkerType::byName, MarkerType::name).fieldOf("type").forGetter(MarkerInfo::type),
                Codec.STRING.fieldOf("id").forGetter(MarkerInfo::id),
                Codec.STRING.optionalFieldOf("baseId", "").forGetter(MarkerInfo::baseId),
                Codec.INT.optionalFieldOf("parkingHeading", -1).forGetter(MarkerInfo::parkingHeading))
                .apply(b, MarkerInfo::new));
    }

    public static final Codec<MarkerRegistry> CODEC =
            MarkerInfo.CODEC.listOf().xmap(MarkerRegistry::new, r -> List.copyOf(r.markers));
    public static final SavedDataType<MarkerRegistry> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "wingman_markers"), MarkerRegistry::new, CODEC);

    private final List<MarkerInfo> markers = new ArrayList<>();

    public MarkerRegistry() {
    }

    private MarkerRegistry(List<MarkerInfo> markers) {
        this.markers.addAll(markers);
    }

    public static MarkerRegistry get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    /** Records the marker at its position, replacing what was recorded there. */
    public static void register(ServerLevel level, MarkerInfo info) {
        MarkerRegistry registry = get(level);
        int index = registry.indexOf(info.pos());
        if (index >= 0 && registry.markers.get(index).equals(info)) {
            return;
        }
        if (index >= 0) {
            registry.markers.remove(index);
        }
        registry.markers.add(info);
        registry.setDirty();
    }

    public static void unregister(ServerLevel level, BlockPos pos) {
        MarkerRegistry registry = get(level);
        if (registry.markers.removeIf(m -> m.pos().equals(pos))) {
            registry.setDirty();
        }
    }

    private int indexOf(BlockPos pos) {
        for (int i = 0; i < this.markers.size(); i++) {
            if (this.markers.get(i).pos().equals(pos)) {
                return i;
            }
        }
        return -1;
    }

    public static List<MarkerInfo> snapshot(ServerLevel level) {
        return Collections.unmodifiableList(get(level).markers);
    }

    public static @Nullable MarkerInfo findById(ServerLevel level, MarkerType type, String id) {
        return findById(level, type, id, "");
    }

    /** A marker of the type and ID, preferring one of the given base, else the first of any base. */
    public static @Nullable MarkerInfo findById(ServerLevel level, MarkerType type, String id, String baseId) {
        MarkerInfo fallback = null;
        for (MarkerInfo m : get(level).markers) {
            if (m.type() != type || !id.equals(m.id())) {
                continue;
            }
            if (!baseId.isEmpty() && baseId.equals(m.baseId())) {
                return m;
            }
            if (fallback == null) {
                fallback = m;
            }
        }
        return fallback;
    }

    public static List<MarkerInfo> findByType(ServerLevel level, MarkerType type) {
        List<MarkerInfo> result = new ArrayList<>();
        for (MarkerInfo m : get(level).markers) {
            if (m.type() == type) {
                result.add(m);
            }
        }
        return result;
    }

    /** The base's first marker of the type. */
    public static @Nullable MarkerInfo findByBase(ServerLevel level, String baseId, MarkerType type) {
        for (MarkerInfo m : get(level).markers) {
            if (m.type() == type && baseId.equals(m.baseId())) {
                return m;
            }
        }
        return null;
    }

    public static List<MarkerInfo> findAllByBase(ServerLevel level, String baseId, MarkerType type) {
        List<MarkerInfo> result = new ArrayList<>();
        for (MarkerInfo m : get(level).markers) {
            if (m.type() == type && baseId.equals(m.baseId())) {
                result.add(m);
            }
        }
        return result;
    }

    /** Every marker naming the base. */
    public static List<MarkerInfo> findChildren(ServerLevel level, String baseId) {
        List<MarkerInfo> result = new ArrayList<>();
        for (MarkerInfo m : get(level).markers) {
            if (!m.baseId().isEmpty() && baseId.equals(m.baseId())) {
                result.add(m);
            }
        }
        return result;
    }

    /** A route node: the base's child of the type when a base has that ID, else a marker with the ID. */
    public static @Nullable MarkerInfo resolveMarker(ServerLevel level, String nodeId, MarkerType type) {
        if (findById(level, MarkerType.BASE, nodeId) != null) {
            MarkerInfo child = findByBase(level, nodeId, type);
            if (child != null) {
                return child;
            }
        }
        return findById(level, type, nodeId);
    }
}
