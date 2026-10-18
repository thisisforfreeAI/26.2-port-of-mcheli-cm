package io.github.mcheliceport.net;

import io.github.mcheliceport.McheliCePort;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * A button of the multiplay manager (upstream {@code PacketHandleCommand}'s SHUFFLE_TEAM,
 * JUMP_SPAWNPOINT, SETPVP and DESTROY_AIRCRAFT), plus {@link Action#STATE}, which asks for the PVP
 * state upstream synced in its server settings.
 */
public record MultiplayActionPayload(Action action) implements CustomPacketPayload {

    public enum Action {
        STATE,
        SHUFFLE_TEAM,
        JUMP_SPAWNPOINT,
        SET_PVP,
        DESTROY_AIRCRAFT
    }

    public static final Type<MultiplayActionPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "multiplay_action"));

    public static final StreamCodec<FriendlyByteBuf, MultiplayActionPayload> CODEC = StreamCodec.of(
            (buf, p) -> buf.writeEnum(p.action),
            buf -> new MultiplayActionPayload(buf.readEnum(Action.class)));

    @Override
    public Type<MultiplayActionPayload> type() {
        return TYPE;
    }
}
