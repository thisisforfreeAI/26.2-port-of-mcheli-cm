package io.github.mcheliceport.net;

import io.github.mcheliceport.McheliCePort;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Upstream {@code PacketBaseAction}'s {@code DISPATCH_ORDER}, from the Mission tab of the base
 * screen of the base marker at {@code pos}: an order for the aircraft. The base is the marker's.
 */
public record DispatchOrderPayload(BlockPos pos, UUID aircraft, String routeId, List<String> missionTypes,
        List<String> weapons, double targetX, double targetZ, double orbitRadius, double cruiseAlt, int strikePasses,
        int timeLimitSeconds, boolean orbitAttack, boolean useVstol, String arrivalRouteId)
        implements CustomPacketPayload {

    public static final Type<DispatchOrderPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "dispatch_order"));

    private static final StreamCodec<ByteBuf, String> ID = ByteBufCodecs.stringUtf8(64);
    private static final StreamCodec<ByteBuf, List<String>> NAMES = ByteBufCodecs.stringUtf8(32)
            .apply(ByteBufCodecs.list(16));

    public static final StreamCodec<ByteBuf, DispatchOrderPayload> CODEC = new StreamCodec<>() {
        @Override
        public DispatchOrderPayload decode(ByteBuf buf) {
            return new DispatchOrderPayload(BlockPos.STREAM_CODEC.decode(buf), UUIDUtil.STREAM_CODEC.decode(buf),
                    ID.decode(buf), NAMES.decode(buf), NAMES.decode(buf), buf.readDouble(), buf.readDouble(),
                    buf.readDouble(), buf.readDouble(), buf.readInt(), buf.readInt(), buf.readBoolean(),
                    buf.readBoolean(), ID.decode(buf));
        }

        @Override
        public void encode(ByteBuf buf, DispatchOrderPayload p) {
            BlockPos.STREAM_CODEC.encode(buf, p.pos());
            UUIDUtil.STREAM_CODEC.encode(buf, p.aircraft());
            ID.encode(buf, p.routeId());
            NAMES.encode(buf, p.missionTypes());
            NAMES.encode(buf, p.weapons());
            buf.writeDouble(p.targetX());
            buf.writeDouble(p.targetZ());
            buf.writeDouble(p.orbitRadius());
            buf.writeDouble(p.cruiseAlt());
            buf.writeInt(p.strikePasses());
            buf.writeInt(p.timeLimitSeconds());
            buf.writeBoolean(p.orbitAttack());
            buf.writeBoolean(p.useVstol());
            ID.encode(buf, p.arrivalRouteId());
        }
    };

    @Override
    public Type<DispatchOrderPayload> type() {
        return TYPE;
    }
}
