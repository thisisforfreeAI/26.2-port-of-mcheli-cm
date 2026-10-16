package io.github.mcheliceport.net;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

class RackControlPayloadTest {
    @Test void everyActionRoundTrips() {
        for (var action : RackControlPayload.Action.values()) {
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                var packet = new RackControlPayload(23000, action);
                RackControlPayload.CODEC.encode(buffer, packet);
                assertEquals(packet, RackControlPayload.CODEC.decode(buffer));
                assertEquals(0, buffer.readableBytes());
            } finally { buffer.release(); }
        }
    }
    @Test void unknownActionIsInert() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeVarInt(42); buffer.writeByte(255);
            assertEquals(new RackControlPayload(42, RackControlPayload.Action.NONE), RackControlPayload.CODEC.decode(buffer));
        } finally { buffer.release(); }
    }
}
