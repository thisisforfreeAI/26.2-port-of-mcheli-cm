package io.github.mcheliceport.net;

import io.github.mcheliceport.McheliCePort;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Whether PVP is on (upstream {@code MCH_ServerSettings.enablePVP}), for the multiplay manager's
 * button, and whether this player may join and leave teams with its team buttons.
 */
public record MultiplayStatePayload(boolean pvp, boolean chooseTeam) implements CustomPacketPayload {

    public static final Type<MultiplayStatePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "multiplay_state"));

    public static final StreamCodec<FriendlyByteBuf, MultiplayStatePayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBoolean(p.pvp);
                buf.writeBoolean(p.chooseTeam);
            },
            buf -> new MultiplayStatePayload(buf.readBoolean(), buf.readBoolean()));

    @Override
    public Type<MultiplayStatePayload> type() {
        return TYPE;
    }
}
