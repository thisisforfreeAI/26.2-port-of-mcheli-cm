package io.github.mcheliceport.net;

import io.github.mcheliceport.McheliCePort;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Upstream {@code PacketMissionAction}, from the mission planner: send an aircraft on a saved
 * route, or abort its planner mission (every planner mission when no aircraft is given).
 */
public record MissionActionPayload(int action, Optional<UUID> aircraft, String routeName)
        implements CustomPacketPayload {

    public static final int ASSIGN = 0;
    public static final int ABORT = 1;

    public static final Type<MissionActionPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "mission_action"));

    public static final StreamCodec<ByteBuf, MissionActionPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, MissionActionPayload::action,
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), MissionActionPayload::aircraft,
            ByteBufCodecs.stringUtf8(64), MissionActionPayload::routeName,
            MissionActionPayload::new);

    @Override
    public Type<MissionActionPayload> type() {
        return TYPE;
    }
}
