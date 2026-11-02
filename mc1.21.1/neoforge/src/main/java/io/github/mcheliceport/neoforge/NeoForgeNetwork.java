package io.github.mcheliceport.neoforge;

import io.github.mcheliceport.platform.network.IPayloadContext;
import io.github.mcheliceport.platform.network.Network;
import io.github.mcheliceport.platform.network.PayloadHandler;
import io.github.mcheliceport.platform.network.PayloadRegistrar;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadHandler;

public final class NeoForgeNetwork implements Network {

    @Override
    public void registerPayloads(String version, Consumer<PayloadRegistrar> registration) {
        McheliCePortNeoForge.modBus().addListener((RegisterPayloadHandlersEvent event) ->
                registration.accept(new Wrapped(event.registrar(version))));
    }

    private record Wrapped(net.neoforged.neoforge.network.registration.PayloadRegistrar registrar)
            implements PayloadRegistrar {
        @Override
        public PayloadRegistrar optional() {
            return new Wrapped(registrar.optional());
        }

        @Override
        public <T extends CustomPacketPayload> PayloadRegistrar playToServer(CustomPacketPayload.Type<T> type,
                StreamCodec<? super RegistryFriendlyByteBuf, T> codec, PayloadHandler<T> handler) {
            registrar.playToServer(type, codec, wrap(handler));
            return this;
        }

        @Override
        public <T extends CustomPacketPayload> PayloadRegistrar playToClient(CustomPacketPayload.Type<T> type,
                StreamCodec<? super RegistryFriendlyByteBuf, T> codec, PayloadHandler<T> handler) {
            registrar.playToClient(type, codec, wrap(handler));
            return this;
        }
    }

    private static <T extends CustomPacketPayload> IPayloadHandler<T> wrap(PayloadHandler<T> handler) {
        return (payload, context) -> handler.handle(payload, new IPayloadContext() {
            @Override
            public Player player() {
                return context.player();
            }

            @Override
            public void enqueueWork(Runnable task) {
                context.enqueueWork(task);
            }
        });
    }

    @Override
    public boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        return player.connection.hasChannel(type);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    @Override
    public void sendToPlayersTrackingEntity(Entity entity, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayersTrackingEntity(entity, payload);
    }

    @Override
    public void sendToPlayersTrackingEntityAndSelf(Entity entity, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, payload);
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }
}
