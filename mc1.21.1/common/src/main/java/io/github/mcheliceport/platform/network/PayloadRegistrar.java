package io.github.mcheliceport.platform.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Registers play payloads: the subset of NeoForge's {@code PayloadRegistrar} the port uses. */
public interface PayloadRegistrar {

    /** Payloads registered through the result may be missing on the other side. */
    PayloadRegistrar optional();

    <T extends CustomPacketPayload> PayloadRegistrar playToServer(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec, PayloadHandler<T> handler);

    <T extends CustomPacketPayload> PayloadRegistrar playToClient(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec, PayloadHandler<T> handler);
}
