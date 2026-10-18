package io.github.mcheliceport.net;

import io.github.mcheliceport.McheliCePort;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Entities a teammate spotted, to mark for {@code count} seconds (upstream {@code PacketSpotEnemy}).
 * At most 256 ids are sent, as upstream's byte-counted list could hold.
 */
public record SpotEnemyPayload(int count, int[] entityIds) implements CustomPacketPayload {

    public static final int MAX_IDS = 256;

    public static final Type<SpotEnemyPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "spot_enemy"));

    public static final StreamCodec<FriendlyByteBuf, SpotEnemyPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.count);
                int n = Math.min(p.entityIds.length, MAX_IDS);
                buf.writeVarInt(n);
                for (int i = 0; i < n; i++) {
                    buf.writeVarInt(p.entityIds[i]);
                }
            },
            buf -> {
                int count = buf.readVarInt();
                int n = buf.readVarInt();
                if (n < 0 || n > MAX_IDS) {
                    throw new IllegalArgumentException("spot_enemy: " + n + " ids");
                }
                int[] ids = new int[n];
                for (int i = 0; i < n; i++) {
                    ids[i] = buf.readVarInt();
                }
                return new SpotEnemyPayload(count, ids);
            });

    @Override
    public boolean equals(Object o) {
        return o instanceof SpotEnemyPayload p && p.count == this.count && java.util.Arrays.equals(p.entityIds, this.entityIds);
    }

    @Override
    public int hashCode() {
        return 31 * this.count + java.util.Arrays.hashCode(this.entityIds);
    }

    @Override
    public Type<SpotEnemyPayload> type() {
        return TYPE;
    }
}
