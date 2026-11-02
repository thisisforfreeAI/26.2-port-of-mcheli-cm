package io.github.mcheliceport.fabric;

import io.github.mcheliceport.platform.network.IPayloadContext;
import io.github.mcheliceport.platform.network.Network;
import io.github.mcheliceport.platform.network.PayloadHandler;
import io.github.mcheliceport.platform.network.PayloadRegistrar;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * Fabric networking. Payload types register on both sides; client handlers are kept until the
 * client entry point registers them, so this class never loads client classes on a server.
 */
public final class FabricNetwork implements Network {

    record ClientReceiver<T extends CustomPacketPayload>(CustomPacketPayload.Type<T> type, PayloadHandler<T> handler) {
    }

    static final List<ClientReceiver<?>> CLIENT_RECEIVERS = new ArrayList<>();
    static @Nullable Consumer<CustomPacketPayload> clientSender;

    @Override
    public void registerPayloads(String version, Consumer<PayloadRegistrar> registration) {
        registration.accept(new PayloadRegistrar() {
            @Override
            public PayloadRegistrar optional() {
                return this;
            }

            @Override
            public <T extends CustomPacketPayload> PayloadRegistrar playToServer(CustomPacketPayload.Type<T> type,
                    StreamCodec<? super RegistryFriendlyByteBuf, T> codec, PayloadHandler<T> handler) {
                PayloadTypeRegistry.playC2S().register(type, codec);
                ServerPlayNetworking.registerGlobalReceiver(type, (payload, context) ->
                        handler.handle(payload, context(context.player(), context.server()::execute)));
                return this;
            }

            @Override
            public <T extends CustomPacketPayload> PayloadRegistrar playToClient(CustomPacketPayload.Type<T> type,
                    StreamCodec<? super RegistryFriendlyByteBuf, T> codec, PayloadHandler<T> handler) {
                PayloadTypeRegistry.playS2C().register(type, codec);
                CLIENT_RECEIVERS.add(new ClientReceiver<>(type, handler));
                return this;
            }
        });
    }

    static IPayloadContext context(Player player, Consumer<Runnable> executor) {
        return new IPayloadContext() {
            @Override
            public Player player() {
                return player;
            }

            @Override
            public void enqueueWork(Runnable task) {
                executor.accept(task);
            }
        };
    }

    static void registerClientReceivers() {
        for (ClientReceiver<?> receiver : CLIENT_RECEIVERS) {
            FabricClientNetwork.register(receiver);
        }
    }

    @Override
    public boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        return ServerPlayNetworking.canSend(player, type);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }

    @Override
    public void sendToPlayersTrackingEntity(Entity entity, CustomPacketPayload payload) {
        for (ServerPlayer player : PlayerLookup.tracking(entity)) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    @Override
    public void sendToPlayersTrackingEntityAndSelf(Entity entity, CustomPacketPayload payload) {
        sendToPlayersTrackingEntity(entity, payload);
        if (entity instanceof ServerPlayer self) {
            ServerPlayNetworking.send(self, payload);
        }
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        if (clientSender != null) {
            clientSender.accept(payload);
        }
    }
}
