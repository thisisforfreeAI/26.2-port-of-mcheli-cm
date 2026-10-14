package io.github.mcheliceport.net;

import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * Tells a shooter their projectile hit: the hit marker of their vehicle's HUD, or with no vehicle
 * (-1) the plain hit marker. Upstream {@code PacketNotifyHit}.
 */
public record NotifyHitPayload(int aircraftId) implements CustomPacketPayload {

    public static final Type<NotifyHitPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "notify_hit"));

    public static final StreamCodec<FriendlyByteBuf, NotifyHitPayload> CODEC = StreamCodec.of(
            (buf, p) -> buf.writeVarInt(p.aircraftId), buf -> new NotifyHitPayload(buf.readVarInt()));

    /** Upstream {@code PacketNotifyHit.send}: to a living rider, naming the vehicle unless it is gone. */
    public static void send(@Nullable AircraftEntity aircraft, ServerPlayer rider) {
        if (rider.isAlive() && rider.connection != null) {
            int id = aircraft != null && aircraft.isAlive() ? aircraft.getId() : -1;
            PacketDistributor.sendToPlayer(rider, new NotifyHitPayload(id));
        }
    }

    @Override
    public Type<NotifyHitPayload> type() {
        return TYPE;
    }
}
