package io.github.mcheliceport.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Durable identity and ordered pairings belong to the tablet, never to its temporary seat. */
public record UavTabletData(UUID id, List<UUID> uavs) {
    public static final int MAX = 64;
    public UavTabletData {
        uavs = List.copyOf(new LinkedHashSet<>(uavs));
        if (uavs.size() > MAX) { throw new IllegalArgumentException("Too many UAV pairings"); }
    }
    public static final Codec<UavTabletData> CODEC = RecordCodecBuilder.create(b -> b.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(UavTabletData::id),
            UUIDUtil.CODEC.sizeLimitedListOf(MAX).fieldOf("uavs").forGetter(UavTabletData::uavs)).apply(b, UavTabletData::new));
    public static final StreamCodec<FriendlyByteBuf, UavTabletData> STREAM_CODEC = StreamCodec.of(
            (b, d) -> { b.writeUUID(d.id); b.writeCollection(d.uavs, (buf, id) -> buf.writeUUID(id)); },
            b -> new UavTabletData(b.readUUID(), b.readCollection(net.minecraft.network.FriendlyByteBuf.limitValue(java.util.ArrayList::new, MAX), buf -> buf.readUUID())));
}
