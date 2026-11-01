package io.github.mcheliceport.net;

import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.platform.Services;
import io.github.mcheliceport.platform.network.IPayloadContext;
import io.github.mcheliceport.shim.ComplexSpawn;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/** A {@link ComplexSpawn} entity's spawn data, sent after its spawn packets (see {@code ServerEntityMixin}). */
public record SpawnDataPayload(int entityId, byte[] data) implements CustomPacketPayload {

    public static final Type<SpawnDataPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(McheliCePort.MOD_ID, "spawn_data"));
    public static final StreamCodec<FriendlyByteBuf, SpawnDataPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.entityId);
                buf.writeByteArray(p.data);
            },
            buf -> new SpawnDataPayload(buf.readVarInt(), buf.readByteArray()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void send(ServerPlayer player, Entity entity, ComplexSpawn spawn) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), entity.registryAccess());
        try {
            spawn.writeSpawnData(buf);
            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);
            Services.NETWORK.sendToPlayer(player, new SpawnDataPayload(entity.getId(), data));
        } finally {
            buf.release();
        }
    }

    static void handle(SpawnDataPayload payload, IPayloadContext context) {
        if (context.player().level().getEntity(payload.entityId) instanceof ComplexSpawn spawn) {
            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(payload.data),
                    context.player().registryAccess());
            try {
                spawn.readSpawnData(buf);
            } finally {
                buf.release();
            }
        }
    }
}
