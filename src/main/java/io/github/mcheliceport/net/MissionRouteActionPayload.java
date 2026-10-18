package io.github.mcheliceport.net;

import io.github.mcheliceport.McheliCePort;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Upstream {@code PacketRouteAction}, from the mission planner: save or delete a planner route. */
public record MissionRouteActionPayload(int action, String name, List<String> nodes) implements CustomPacketPayload {

    public static final int CREATE = 0;
    public static final int DELETE = 1;

    public static final Type<MissionRouteActionPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "mission_route_action"));

    public static final StreamCodec<ByteBuf, MissionRouteActionPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, MissionRouteActionPayload::action,
            ByteBufCodecs.stringUtf8(64), MissionRouteActionPayload::name,
            ByteBufCodecs.stringUtf8(160).apply(ByteBufCodecs.list(64)), MissionRouteActionPayload::nodes,
            MissionRouteActionPayload::new);

    @Override
    public Type<MissionRouteActionPayload> type() {
        return TYPE;
    }
}
