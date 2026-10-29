package io.github.mcheliceport.net;

import io.github.mcheliceport.McheliCePort;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Fixed actions: enter, leave, reset, configure, fire. All values are revalidated by the server. */
public record EffectsRangePayload(int action,int weapon,int surface,int lighting) implements CustomPacketPayload {
    public static final Type<EffectsRangePayload> TYPE=new Type<>(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID,"effects_range"));
    public static final StreamCodec<FriendlyByteBuf,EffectsRangePayload> CODEC=StreamCodec.of(
        (b,p)->{b.writeVarInt(p.action);b.writeVarInt(p.weapon);b.writeVarInt(p.surface);b.writeVarInt(p.lighting);},
        b->new EffectsRangePayload(b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt()));
    @Override public Type<EffectsRangePayload> type(){return TYPE;}
}
