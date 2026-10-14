package io.github.mcheliceport.net;

import io.github.mcheliceport.McheliCePort;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * A McHeli sound played at a position, loud (volume 100, or 50 silenced) so it carries as far as
 * the packet's range: projectile hit sounds. Upstream {@code PacketClientSound}.
 *
 * @param distort  random pitch between 1/1.2 and 1/0.8
 * @param silenced half volume, double pitch
 */
public record ClientSoundPayload(float x, float y, float z, String sound, boolean distort, boolean silenced)
        implements CustomPacketPayload {

    public static final Type<ClientSoundPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "client_sound"));

    public static final StreamCodec<FriendlyByteBuf, ClientSoundPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeFloat(p.x);
                buf.writeFloat(p.y);
                buf.writeFloat(p.z);
                buf.writeUtf(p.sound, 256);
                buf.writeBoolean(p.distort);
                buf.writeBoolean(p.silenced);
            },
            buf -> new ClientSoundPayload(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readUtf(256),
                    buf.readBoolean(), buf.readBoolean()));

    /** Upstream {@code sendSoundPacket}: to every player within {@code range}; nothing for an empty name. */
    public static void send(Level level, double x, double y, double z, double range, @Nullable String sound,
            boolean distort) {
        if (sound != null && !sound.isEmpty() && level instanceof ServerLevel server) {
            ModNetwork.sendToPlayersNear(server, null, x, y, z, range,
                    new ClientSoundPayload((float) x, (float) y, (float) z, sound, distort, false));
        }
    }

    @Override
    public Type<ClientSoundPayload> type() {
        return TYPE;
    }
}
