package io.github.mcheliceport.net;

import io.github.mcheliceport.McheliCePort;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** A bounded cosmetic request from the open aircraft menu; the server validates the livery list. */
public record AircraftAppearancePayload(int aircraftId,String texture,int appearance) implements CustomPacketPayload {
    public static final Type<AircraftAppearancePayload> TYPE=new Type<>(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID,"aircraft_appearance"));
    public static final StreamCodec<FriendlyByteBuf,AircraftAppearancePayload> CODEC=StreamCodec.of(
        (b,p)->{b.writeVarInt(p.aircraftId);b.writeUtf(p.texture,128);b.writeVarInt(p.appearance);},
        b->new AircraftAppearancePayload(b.readVarInt(),b.readUtf(128),b.readVarInt()));
    @Override public Type<AircraftAppearancePayload> type(){return TYPE;}
}
