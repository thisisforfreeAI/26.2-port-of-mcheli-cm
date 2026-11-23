package io.github.mcheliceport.net;

import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.radar.RwrThreat;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Upstream {@code PacketRWRThreatSync}: the threat table of the vehicle a player rides.
 * {@code fitted} says whether the vehicle has a warning receiver (and so a scope); riders of one
 * without get only locks and launches, for the lock tone.
 */
public record RwrThreatPayload(int receiverId, boolean fitted, List<RwrThreat> threats) implements CustomPacketPayload {

    public static final Type<RwrThreatPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "rwr_threats"));

    public static final StreamCodec<FriendlyByteBuf, RwrThreatPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RwrThreatPayload::receiverId,
            ByteBufCodecs.BOOL, RwrThreatPayload::fitted,
            RwrThreat.CODEC.apply(ByteBufCodecs.list(32)), RwrThreatPayload::threats,
            RwrThreatPayload::new);

    @Override
    public Type<RwrThreatPayload> type() {
        return TYPE;
    }
}
