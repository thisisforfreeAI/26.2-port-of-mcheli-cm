package io.github.mcheliceport.platform.network;

import java.util.function.Consumer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/** The loader's networking, behind the shared code's {@link PacketDistributor}. */
public interface Network {

    /** Registers the payloads; called once while the mod starts. */
    void registerPayloads(String version, Consumer<PayloadRegistrar> registration);

    /** Whether {@code player}'s connection negotiated payloads of {@code type}. */
    boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type);

    void sendToPlayer(ServerPlayer player, CustomPacketPayload payload);

    void sendToPlayersTrackingEntity(Entity entity, CustomPacketPayload payload);

    void sendToPlayersTrackingEntityAndSelf(Entity entity, CustomPacketPayload payload);

    /** Client only. */
    void sendToServer(CustomPacketPayload payload);
}
