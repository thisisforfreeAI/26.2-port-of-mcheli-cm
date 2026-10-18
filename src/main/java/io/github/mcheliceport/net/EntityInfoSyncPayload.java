package io.github.mcheliceport.net;

import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.radar.EntityInfo;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Upstream {@code PacketEntityInfoSync}: one player's snapshot of the tracked entities around them. */
public record EntityInfoSyncPayload(List<EntityInfo> entities, long snapshotSeq) implements CustomPacketPayload {

    public static final Type<EntityInfoSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "entity_info_sync"));

    public static final StreamCodec<FriendlyByteBuf, EntityInfoSyncPayload> CODEC = StreamCodec.composite(
            EntityInfo.CODEC.apply(ByteBufCodecs.list()), EntityInfoSyncPayload::entities,
            ByteBufCodecs.VAR_LONG, EntityInfoSyncPayload::snapshotSeq,
            EntityInfoSyncPayload::new);

    @Override
    public Type<EntityInfoSyncPayload> type() {
        return TYPE;
    }
}
