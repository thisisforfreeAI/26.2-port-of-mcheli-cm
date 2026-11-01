package io.github.mcheliceport.platform.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

@FunctionalInterface
public interface PayloadHandler<T extends CustomPacketPayload> {
    void handle(T payload, IPayloadContext context);
}
