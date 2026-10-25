package io.github.mcheliceport.net;

import io.github.mcheliceport.McheliCePort;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * A button of the multiplay manager (upstream {@code PacketHandleCommand}'s SHUFFLE_TEAM,
 * JUMP_SPAWNPOINT, SETPVP and DESTROY_AIRCRAFT), plus {@link Action#STATE}, which asks for the PVP
 * state upstream synced in its server settings, and the port's team buttons: {@link Action#JOIN_TEAM}
 * joins the team {@code team} names and {@link Action#LEAVE_TEAM} leaves the player's team.
 */
public record MultiplayActionPayload(Action action, String team) implements CustomPacketPayload {

    public enum Action {
        STATE,
        SHUFFLE_TEAM,
        JUMP_SPAWNPOINT,
        SET_PVP,
        DESTROY_AIRCRAFT,
        JOIN_TEAM,
        LEAVE_TEAM
    }

    /** Vanilla's longest team name. */
    public static final int MAX_TEAM_NAME = 64;

    public MultiplayActionPayload(Action action) {
        this(action, "");
    }

    public static final Type<MultiplayActionPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "multiplay_action"));

    public static final StreamCodec<FriendlyByteBuf, MultiplayActionPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeEnum(p.action);
                buf.writeUtf(p.team, MAX_TEAM_NAME);
            },
            buf -> new MultiplayActionPayload(buf.readEnum(Action.class), buf.readUtf(MAX_TEAM_NAME)));

    @Override
    public Type<MultiplayActionPayload> type() {
        return TYPE;
    }
}
