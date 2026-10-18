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
 * Upstream {@code PacketWingmanPanelAction}: one of the panel's actions, on the aircraft
 * {@code uuid} where it names one. {@code extra} is the weapon type; the formation values only
 * matter to {@link #FORMATION}.
 */
public record WingmanPanelActionPayload(int action, Optional<UUID> uuid, String extra, double sideDist,
        double altOffset, double rearDist, int maxWings, double minAlt, double maxAlt) implements CustomPacketPayload {

    public static final int FOLLOW = 0;
    public static final int STOP = 1;
    public static final int STOP_ALL = 2;
    public static final int AUTO = 3;
    public static final int HOLD = 4;
    public static final int WEAPON = 5;
    public static final int FORMATION = 6;

    public static final Type<WingmanPanelActionPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "wingman_panel_action"));

    public static final StreamCodec<ByteBuf, WingmanPanelActionPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, WingmanPanelActionPayload::action,
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), WingmanPanelActionPayload::uuid,
            ByteBufCodecs.stringUtf8(32), WingmanPanelActionPayload::extra,
            ByteBufCodecs.DOUBLE, WingmanPanelActionPayload::sideDist,
            ByteBufCodecs.DOUBLE, WingmanPanelActionPayload::altOffset,
            ByteBufCodecs.DOUBLE, WingmanPanelActionPayload::rearDist,
            ByteBufCodecs.VAR_INT, WingmanPanelActionPayload::maxWings,
            ByteBufCodecs.DOUBLE, WingmanPanelActionPayload::minAlt,
            ByteBufCodecs.DOUBLE, WingmanPanelActionPayload::maxAlt,
            WingmanPanelActionPayload::new);

    /** An action on one aircraft. */
    public static WingmanPanelActionPayload of(int action, UUID uuid, String extra) {
        return new WingmanPanelActionPayload(action, Optional.of(uuid), extra, 0, 0, 0, 0, 0, 0);
    }

    @Override
    public Type<WingmanPanelActionPayload> type() {
        return TYPE;
    }
}
