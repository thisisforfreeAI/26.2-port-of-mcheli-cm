package io.github.mcheliceport.net;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.serialization.JsonOps;
import io.github.mcheliceport.item.UavTabletData;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

class UavPairingCodecTest {
    @Test void tabletIdentityAndOrderedPairingsSurviveDiskAndNetwork() {
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        var data = new UavTabletData(UUID.randomUUID(), List.of(first, second, first));
        assertEquals(List.of(first, second), data.uavs());
        assertThrows(UnsupportedOperationException.class, () -> data.uavs().clear());
        var json = UavTabletData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
        assertEquals(data, UavTabletData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            UavTabletData.STREAM_CODEC.encode(buffer, data);
            assertEquals(data, UavTabletData.STREAM_CODEC.decode(buffer)); assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }
    @Test void untrustedListsHaveAnExplicitBound() {
        var ids = IntStream.range(0, 65).mapToObj(i -> new UUID(0, i)).toList();
        assertThrows(IllegalArgumentException.class, () -> new UavTabletData(UUID.randomUUID(), ids));
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeVarInt(1); buffer.writeVarInt(2); buffer.writeBoolean(false); buffer.writeVarInt(65);
            assertThrows(io.netty.handler.codec.DecoderException.class, () -> UavRosterPayload.CODEC.decode(buffer));
            buffer.clear(); buffer.writeUUID(UUID.randomUUID()); buffer.writeVarInt(65);
            assertThrows(io.netty.handler.codec.DecoderException.class, () -> UavTabletData.STREAM_CODEC.decode(buffer));
        } finally { buffer.release(); }
    }
    @Test void rosterRetainsUuidSelectionAndUnavailableRows() {
        var id = UUID.randomUUID();
        var rows = List.of(new UavRosterPayload.Row(id, "MQ-8B", "Ready", 100, 50, 100, 32),
                new UavRosterPayload.Row(UUID.randomUUID(), "offline", "Not loaded", 0, 0, 0, -1));
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            for (UUID selection : new UUID[]{null, id}) {
                var packet = new UavRosterPayload(22, 17000, rows, selection);
                UavRosterPayload.CODEC.encode(buffer, packet); assertEquals(packet, UavRosterPayload.CODEC.decode(buffer));
            }
        } finally { buffer.release(); }
    }
    @Test void actionsRetainMenuStationAndAircraftIdentity() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            for (int action : new int[]{0, 1, 2, 99}) {
                var packet = new UavRosterActionPayload(42, 10000, UUID.randomUUID(), action);
                UavRosterActionPayload.CODEC.encode(buffer, packet); assertEquals(packet, UavRosterActionPayload.CODEC.decode(buffer));
            }
        } finally { buffer.release(); }
    }
}
