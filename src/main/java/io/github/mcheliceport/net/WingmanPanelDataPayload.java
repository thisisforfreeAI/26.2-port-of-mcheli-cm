package io.github.mcheliceport.net;

import io.github.mcheliceport.McheliCePort;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Upstream {@code PacketWingmanPanelData}: the leader's wingmen, the unassigned aircraft nearby
 * and the formation values, for the wingman panel.
 */
public record WingmanPanelDataPayload(List<Aircraft> nearby, List<Wingman> wingmen, double sideDist,
        double altOffset, double rearDist, int maxWings, double minAlt, double maxAlt) implements CustomPacketPayload {

    public static final int MAX_LIST = 64;

    public record Aircraft(UUID uuid, String name) {
        public static final StreamCodec<ByteBuf, Aircraft> CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Aircraft::uuid, ByteBufCodecs.stringUtf8(128), Aircraft::name, Aircraft::new);
    }

    /** {@code attackMode} is {@code WingmanEntry.ATK_*}; {@code weaponType} is empty for any. */
    public record Wingman(UUID uuid, String name, int slot, String state, int attackMode, String weaponType) {
        public static final StreamCodec<ByteBuf, Wingman> CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Wingman::uuid, ByteBufCodecs.stringUtf8(128), Wingman::name,
                ByteBufCodecs.VAR_INT, Wingman::slot, ByteBufCodecs.stringUtf8(32), Wingman::state,
                ByteBufCodecs.VAR_INT, Wingman::attackMode, ByteBufCodecs.stringUtf8(32), Wingman::weaponType,
                Wingman::new);
    }

    public static final Type<WingmanPanelDataPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "wingman_panel_data"));

    public static final StreamCodec<ByteBuf, WingmanPanelDataPayload> CODEC = StreamCodec.composite(
            Aircraft.CODEC.apply(ByteBufCodecs.list(MAX_LIST)), WingmanPanelDataPayload::nearby,
            Wingman.CODEC.apply(ByteBufCodecs.list(MAX_LIST)), WingmanPanelDataPayload::wingmen,
            ByteBufCodecs.DOUBLE, WingmanPanelDataPayload::sideDist,
            ByteBufCodecs.DOUBLE, WingmanPanelDataPayload::altOffset,
            ByteBufCodecs.DOUBLE, WingmanPanelDataPayload::rearDist,
            ByteBufCodecs.VAR_INT, WingmanPanelDataPayload::maxWings,
            ByteBufCodecs.DOUBLE, WingmanPanelDataPayload::minAlt,
            ByteBufCodecs.DOUBLE, WingmanPanelDataPayload::maxAlt,
            WingmanPanelDataPayload::new);

    @Override
    public Type<WingmanPanelDataPayload> type() {
        return TYPE;
    }
}
