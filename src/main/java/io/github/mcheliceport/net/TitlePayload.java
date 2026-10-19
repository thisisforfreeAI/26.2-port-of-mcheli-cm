package io.github.mcheliceport.net;

import io.github.mcheliceport.McheliCePort;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Upstream {@code PacketTitle}: the message {@code /mcheli title} shows every player for
 * {@code showTime} ticks at one of upstream's screen positions.
 */
public record TitlePayload(Component message, int showTime, int position) implements CustomPacketPayload {

    public static final Type<TitlePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "title"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TitlePayload> CODEC = StreamCodec.composite(
            ComponentSerialization.TRUSTED_STREAM_CODEC, TitlePayload::message,
            ByteBufCodecs.VAR_INT, TitlePayload::showTime,
            ByteBufCodecs.VAR_INT, TitlePayload::position,
            TitlePayload::new);

    @Override
    public Type<TitlePayload> type() {
        return TYPE;
    }
}
