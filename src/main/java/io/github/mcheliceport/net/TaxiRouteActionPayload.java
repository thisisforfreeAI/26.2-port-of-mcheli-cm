package io.github.mcheliceport.net;

import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.wingman.mission.TaxiRoute;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Upstream {@code PacketBaseAction}'s {@code SAVE_ROUTE} and {@code DELETE_ROUTE}, from the base
 * screen of the base marker at {@code pos}. A delete only needs the route's ID.
 */
public record TaxiRouteActionPayload(BlockPos pos, boolean delete, TaxiRoute route) implements CustomPacketPayload {

    public static final Type<TaxiRouteActionPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "taxi_route_action"));

    public static final StreamCodec<ByteBuf, TaxiRouteActionPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, TaxiRouteActionPayload::pos, ByteBufCodecs.BOOL, TaxiRouteActionPayload::delete,
            TaxiRoute.STREAM_CODEC, TaxiRouteActionPayload::route, TaxiRouteActionPayload::new);

    @Override
    public Type<TaxiRouteActionPayload> type() {
        return TYPE;
    }
}
