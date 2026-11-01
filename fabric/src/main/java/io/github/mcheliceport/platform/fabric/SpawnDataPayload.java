package io.github.mcheliceport.platform.fabric;

import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.platform.loader.SpawnDataEntity;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** A {@link SpawnDataEntity}'s extra spawn data, sent right after its add-entity packet (Fabric only). */
public record SpawnDataPayload(int entityId, byte[] data) implements CustomPacketPayload {
    public static final Type<SpawnDataPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "fabric_spawn_data"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SpawnDataPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SpawnDataPayload::entityId,
            ByteBufCodecs.BYTE_ARRAY, SpawnDataPayload::data,
            SpawnDataPayload::new);

    public static SpawnDataPayload of(SpawnDataEntity entity, RegistryAccess registries) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        try {
            entity.fabricWriteSpawnData(buf);
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            return new SpawnDataPayload(entity.getId(), bytes);
        } finally {
            buf.release();
        }
    }

    /** Client: hands the data to the entity, which the add-entity packet before it created. */
    void apply(Player player) {
        Entity entity = player.level().getEntity(this.entityId);
        if (entity instanceof SpawnDataEntity spawnData) {
            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(this.data),
                    player.registryAccess());
            spawnData.fabricReadSpawnData(buf);
        }
    }

    @Override
    public Type<SpawnDataPayload> type() {
        return TYPE;
    }
}
