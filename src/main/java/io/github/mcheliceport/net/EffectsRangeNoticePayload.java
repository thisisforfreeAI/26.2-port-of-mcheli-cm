package io.github.mcheliceport.net;

import io.github.mcheliceport.McheliCePort;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Ready/resetting/shot/options notification, scoped to the viewing player. */
public record EffectsRangeNoticePayload(int state,int weapon,int surface,int lighting,int cooldown) implements CustomPacketPayload {
    public static final Type<EffectsRangeNoticePayload> TYPE=new Type<>(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID,"effects_range_notice"));
    public static final StreamCodec<FriendlyByteBuf,EffectsRangeNoticePayload> CODEC=StreamCodec.of(
        (b,p)->{b.writeVarInt(p.state);b.writeVarInt(p.weapon);b.writeVarInt(p.surface);b.writeVarInt(p.lighting);b.writeVarInt(p.cooldown);},
        b->new EffectsRangeNoticePayload(b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt()));
    @Override public Type<EffectsRangeNoticePayload> type(){return TYPE;}
}
