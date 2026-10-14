package io.github.mcheliceport.net;

import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.config.ServerSettings;
import io.github.mcheliceport.entity.heli.HeliFlightModel;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * The server settings clients fly by (upstream {@code PacketSyncServerSettings}): the rotation
 * limits, as bytes like upstream, and the authority mode of decision D2. Sent on login and when
 * the server config changes. Upstream's other fields belong to later milestones.
 */
public record ServerSettingsPayload(boolean enableRotationLimit, byte pitchLimitMax, byte pitchLimitMin,
        byte rollLimit, boolean serverAuthoritativeMovement) implements CustomPacketPayload {

    public static final Type<ServerSettingsPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "server_settings"));

    public static final StreamCodec<FriendlyByteBuf, ServerSettingsPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBoolean(p.enableRotationLimit);
                buf.writeByte(p.pitchLimitMax);
                buf.writeByte(p.pitchLimitMin);
                buf.writeByte(p.rollLimit);
                buf.writeBoolean(p.serverAuthoritativeMovement);
            },
            buf -> new ServerSettingsPayload(buf.readBoolean(), buf.readByte(), buf.readByte(), buf.readByte(),
                    buf.readBoolean()));

    public static ServerSettingsPayload of(ServerSettings.Values values) {
        HeliFlightModel.RotationLimits limits = values.rotationLimits();
        return new ServerSettingsPayload(limits.enabled(), (byte) limits.pitchMax(), (byte) limits.pitchMin(),
                (byte) limits.rollLimit(), values.serverAuthoritativeMovement());
    }

    public ServerSettings.Values toValues() {
        return new ServerSettings.Values(this.serverAuthoritativeMovement,
                new HeliFlightModel.RotationLimits(this.enableRotationLimit, this.pitchLimitMax, this.pitchLimitMin,
                        this.rollLimit));
    }

    @Override
    public Type<ServerSettingsPayload> type() {
        return TYPE;
    }
}
