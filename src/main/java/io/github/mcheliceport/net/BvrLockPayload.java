package io.github.mcheliceport.net;

import io.github.mcheliceport.McheliCePort;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Upstream {@code PacketLockTargetBVR}: the target the rider's BVR scope holds at its centre (0 for none). */
public record BvrLockPayload(int targetId) implements CustomPacketPayload {

    public static final Type<BvrLockPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "bvr_lock"));

    public static final StreamCodec<FriendlyByteBuf, BvrLockPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BvrLockPayload::targetId, BvrLockPayload::new);

    @Override
    public Type<BvrLockPayload> type() {
        return TYPE;
    }
}
