package io.github.mcheliceport.platform;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

/**
 * Where {@code ModNetwork} declares its play payloads, the loader-neutral form of NeoForge's
 * {@code PayloadRegistrar}. Handlers run on the game thread of the receiving side.
 */
public interface Payloads {

    /** What a handler learns about the payload's sender (server) or the local player (client). */
    interface Context {
        Player player();
    }

    @FunctionalInterface
    interface Handler<T extends CustomPacketPayload> {
        void handle(T payload, Context context);
    }

    <T extends CustomPacketPayload> void playToServer(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec, Handler<T> handler);

    <T extends CustomPacketPayload> void playToClient(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec, Handler<T> handler);

    /**
     * A client-bound payload that clients may lack: on NeoForge such a client still connects
     * and simply never receives it.
     */
    <T extends CustomPacketPayload> void optionalPlayToClient(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec, Handler<T> handler);
}
