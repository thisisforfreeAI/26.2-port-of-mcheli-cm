package io.github.mcheliceport.platform.fabric;

import io.github.mcheliceport.net.ModNetwork;
import io.github.mcheliceport.platform.Payloads;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Hooks {@link ModNetwork} to Fabric's networking. Payload types and server receivers are
 * registered during common init; client receivers wait for {@link #registerClientReceivers}.
 */
public final class FabricNetwork {
    private static final List<Runnable> CLIENT_RECEIVERS = new ArrayList<>();

    private FabricNetwork() {
    }

    public static void register() {
        ModNetwork.registerPayloads(new Registrar());
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                ModNetwork.onPlayerLoggedIn(handler.player));
    }

    /** Client init: starts handling the client-bound payloads. */
    public static void registerClientReceivers() {
        CLIENT_RECEIVERS.forEach(Runnable::run);
        CLIENT_RECEIVERS.clear();
    }

    private static final class Registrar implements Payloads {
        @Override
        public <T extends CustomPacketPayload> void playToServer(CustomPacketPayload.Type<T> type,
                StreamCodec<? super RegistryFriendlyByteBuf, T> codec, Handler<T> handler) {
            PayloadTypeRegistry.serverboundPlay().register(type, codec);
            ServerPlayNetworking.registerGlobalReceiver(type,
                    (payload, context) -> handler.handle(payload, context::player));
        }

        @Override
        public <T extends CustomPacketPayload> void playToClient(CustomPacketPayload.Type<T> type,
                StreamCodec<? super RegistryFriendlyByteBuf, T> codec, Handler<T> handler) {
            PayloadTypeRegistry.clientboundPlay().register(type, codec);
            CLIENT_RECEIVERS.add(() -> ClientPlayNetworking.registerGlobalReceiver(type,
                    (payload, context) -> handler.handle(payload, context::player)));
        }

        @Override
        public <T extends CustomPacketPayload> void optionalPlayToClient(CustomPacketPayload.Type<T> type,
                StreamCodec<? super RegistryFriendlyByteBuf, T> codec, Handler<T> handler) {
            // Fabric has no channel negotiation to opt out of: ModNetwork.sendToPlayer checks canSend.
            playToClient(type, codec, handler);
        }
    }
}
