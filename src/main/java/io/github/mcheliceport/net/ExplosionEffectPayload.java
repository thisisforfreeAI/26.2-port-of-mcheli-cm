package io.github.mcheliceport.net;

import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.effect.BlastStyle;
import io.github.mcheliceport.effect.BlastDirection;
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
        List<BlockPos> affectedBlocks, BlastStyle blastStyle, BlastDirection direction,
        List<BlockPos> terrainBlocks) implements CustomPacketPayload {

    public ExplosionEffectPayload {
        if (terrainBlocks.size() > io.github.mcheliceport.effect.BlastTerrainSample.LIMIT)
            throw new IllegalArgumentException("Too many cosmetic terrain samples");
        terrainBlocks = List.copyOf(terrainBlocks);
    }
    public ExplosionEffectPayload(double x, double y, double z, float size, boolean inWater,
            List<BlockPos> blocks, BlastStyle style, BlastDirection direction) {
        this(x, y, z, size, inWater, blocks, style, direction, List.of());
    }

    public ExplosionEffectPayload(double x, double y, double z, float size, boolean inWater,
            List<BlockPos> blocks, BlastStyle style) {
        this(x, y, z, size, inWater, blocks, style, BlastDirection.NONE);
    }

    public ExplosionEffectPayload(double x, double y, double z, float size, boolean inWater, List<BlockPos> blocks) {
        this(x, y, z, size, inWater, blocks, BlastStyle.BLAST);
    }

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
                buf.writeEnum(p.blastStyle);
                buf.writeByte(p.direction.x());
                buf.writeByte(p.direction.y());
                buf.writeByte(p.direction.z());
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
                List<BlockPos> terrain = p.terrainBlocks.stream().filter(pos -> fits(pos.getX()-origin.getX())
                        && fits(pos.getY()-origin.getY()) && fits(pos.getZ()-origin.getZ())).toList();
                buf.writeVarInt(terrain.size());
                for (BlockPos pos : terrain) {
                    buf.writeShort(pos.getX()-origin.getX());buf.writeShort(pos.getY()-origin.getY());buf.writeShort(pos.getZ()-origin.getZ());
                }
            },
            buf -> {
                double x = buf.readDouble();
                double y = buf.readDouble();
                double z = buf.readDouble();
                float size = buf.readFloat();
                boolean inWater = buf.readBoolean();
                BlastStyle style = buf.readEnum(BlastStyle.class);
                BlastDirection direction = new BlastDirection(buf.readByte(), buf.readByte(), buf.readByte());
                BlockPos origin = BlockPos.containing(x, y, z);
                int n = buf.readVarInt();
                if (n < 0 || n > buf.readableBytes() / 6) {
                    throw new IllegalArgumentException("Bad block count " + n);
                }
                List<BlockPos> blocks = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    blocks.add(origin.offset(buf.readShort(), buf.readShort(), buf.readShort()));
                }
                int count = buf.readVarInt();
                if (count < 0 || count > io.github.mcheliceport.effect.BlastTerrainSample.LIMIT || count > buf.readableBytes()/6)
                    throw new IllegalArgumentException("Bad terrain sample count " + count);
                List<BlockPos> terrain = new ArrayList<>(count);
                for (int i=0;i<count;i++) terrain.add(origin.offset(buf.readShort(),buf.readShort(),buf.readShort()));
                return new ExplosionEffectPayload(x, y, z, size, inWater, blocks, style, direction, terrain);
            });

    private static boolean fits(int offset) {
        return offset >= Short.MIN_VALUE && offset <= Short.MAX_VALUE;
    }

    @Override
    public Type<ExplosionEffectPayload> type() {
        return TYPE;
    }
}
