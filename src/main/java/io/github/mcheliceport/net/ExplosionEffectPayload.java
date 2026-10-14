package io.github.mcheliceport.net;

import io.github.mcheliceport.McheliCePort;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * The effects of a McHeli explosion (upstream {@code PacketParticleEffect} with
 * {@code DataExplosionParameters}), sent to players within {@link #RANGE} blocks. The affected
 * blocks travel as offsets from the block at the centre.
 */
public record ExplosionEffectPayload(double x, double y, double z, float size, boolean inWater,
        List<BlockPos> affectedBlocks) implements CustomPacketPayload {

    /** Upstream {@code PacketParticleEffect.RANGE}. */
    public static final double RANGE = 1000.0;

    public static final Type<ExplosionEffectPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "explosion_effect"));

    public static final StreamCodec<FriendlyByteBuf, ExplosionEffectPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeDouble(p.x);
                buf.writeDouble(p.y);
                buf.writeDouble(p.z);
                buf.writeFloat(p.size);
                buf.writeBoolean(p.inWater);
                BlockPos origin = BlockPos.containing(p.x, p.y, p.z);
                List<BlockPos> near = new ArrayList<>(p.affectedBlocks.size());
                for (BlockPos pos : p.affectedBlocks) {
                    if (fits(pos.getX() - origin.getX()) && fits(pos.getY() - origin.getY())
                            && fits(pos.getZ() - origin.getZ())) {
                        near.add(pos);
                    }
                }
                buf.writeVarInt(near.size());
                for (BlockPos pos : near) {
                    buf.writeShort(pos.getX() - origin.getX());
                    buf.writeShort(pos.getY() - origin.getY());
                    buf.writeShort(pos.getZ() - origin.getZ());
                }
            },
            buf -> {
                double x = buf.readDouble();
                double y = buf.readDouble();
                double z = buf.readDouble();
                float size = buf.readFloat();
                boolean inWater = buf.readBoolean();
                BlockPos origin = BlockPos.containing(x, y, z);
                int n = buf.readVarInt();
                if (n < 0 || n > buf.readableBytes() / 6) {
                    throw new IllegalArgumentException("Bad block count " + n);
                }
                List<BlockPos> blocks = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    blocks.add(origin.offset(buf.readShort(), buf.readShort(), buf.readShort()));
                }
                return new ExplosionEffectPayload(x, y, z, size, inWater, blocks);
            });

    private static boolean fits(int offset) {
        return offset >= Short.MIN_VALUE && offset <= Short.MAX_VALUE;
    }

    @Override
    public Type<ExplosionEffectPayload> type() {
        return TYPE;
    }
}
