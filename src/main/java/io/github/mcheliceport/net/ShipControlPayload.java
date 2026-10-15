package io.github.mcheliceport.net;

import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.net.PlaneControlPayload.PartSwitch;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Shared aircraft controls with explicit ship nozzle and wing switches; heli encoding stays intact. */
public record ShipControlPayload(HeliControlPayload controls, PartSwitch vtol, PartSwitch wing)
        implements CustomPacketPayload {

    public static final Type<ShipControlPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "ship_control"));
    public static final StreamCodec<FriendlyByteBuf, ShipControlPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                HeliControlPayload.CODEC.encode(buf, p.controls);
                buf.writeByte(p.vtol.ordinal());
                buf.writeByte(p.wing.ordinal());
            },
            buf -> new ShipControlPayload(HeliControlPayload.CODEC.decode(buf), readSwitch(buf), readSwitch(buf)));

    private static PartSwitch readSwitch(FriendlyByteBuf buf) {
        int ordinal = buf.readUnsignedByte();
        return ordinal < PartSwitch.values().length ? PartSwitch.values()[ordinal] : PartSwitch.NONE;
    }

    @Override
    public Type<ShipControlPayload> type() { return TYPE; }
}
