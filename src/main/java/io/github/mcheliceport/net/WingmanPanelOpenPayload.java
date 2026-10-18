package io.github.mcheliceport.net;

import io.github.mcheliceport.McheliCePort;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Upstream {@code PacketWingmanPanelOpen}: asks the server for the wingman panel's data. */
public record WingmanPanelOpenPayload() implements CustomPacketPayload {

    public static final WingmanPanelOpenPayload INSTANCE = new WingmanPanelOpenPayload();
    public static final Type<WingmanPanelOpenPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "wingman_panel_open"));
    public static final StreamCodec<ByteBuf, WingmanPanelOpenPayload> CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<WingmanPanelOpenPayload> type() {
        return TYPE;
    }
}
