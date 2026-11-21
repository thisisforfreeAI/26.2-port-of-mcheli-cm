package io.github.mcheliceport.net;

import io.github.mcheliceport.McheliCePort;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Upstream {@code PacketNotifyLock} (client to server): the entity the sender's seeker is locking
 * or holds, 0 once it lets go. Seekers lock on the shooter's client, so this is how the server,
 * and through it the target's radar warning receiver, learns of a lock.
 */
public record RwrLockPayload(int targetId) implements CustomPacketPayload {

    public static final Type<RwrLockPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "rwr_lock"));

    public static final StreamCodec<FriendlyByteBuf, RwrLockPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RwrLockPayload::targetId, RwrLockPayload::new);

    @Override
    public Type<RwrLockPayload> type() {
        return TYPE;
    }
}
