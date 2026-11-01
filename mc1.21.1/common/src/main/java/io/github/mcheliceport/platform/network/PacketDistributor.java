package io.github.mcheliceport.platform.network;

import io.github.mcheliceport.platform.Services;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/** Sends payloads: NeoForge's {@code PacketDistributor}, on either loader. */
public final class PacketDistributor {

    private PacketDistributor() {
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        Services.NETWORK.sendToPlayer(player, payload);
    }

    public static void sendToPlayersTrackingEntity(Entity entity, CustomPacketPayload payload) {
        Services.NETWORK.sendToPlayersTrackingEntity(entity, payload);
    }

    public static void sendToPlayersTrackingEntityAndSelf(Entity entity, CustomPacketPayload payload) {
        Services.NETWORK.sendToPlayersTrackingEntityAndSelf(entity, payload);
    }
}
