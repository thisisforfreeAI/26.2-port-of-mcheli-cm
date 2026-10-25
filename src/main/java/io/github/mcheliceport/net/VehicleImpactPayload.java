package io.github.mcheliceport.net;

import io.github.mcheliceport.McheliCePort;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Optional S2C cosmetic hit, in unanimated body coordinates; never accepted from clients. */
public record VehicleImpactPayload(int entityId, UUID vehicle, float x,float y,float z,
        float dx,float dy,float dz,float radius,boolean cannon,int seed) implements CustomPacketPayload {
    public static final Type<VehicleImpactPayload> TYPE=new Type<>(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID,"vehicle_impact"));
    public static final StreamCodec<FriendlyByteBuf,VehicleImpactPayload> CODEC=StreamCodec.of((b,p)->{
        b.writeVarInt(p.entityId);b.writeUUID(p.vehicle);b.writeFloat(p.x);b.writeFloat(p.y);b.writeFloat(p.z);
        b.writeFloat(p.dx);b.writeFloat(p.dy);b.writeFloat(p.dz);b.writeFloat(p.radius);b.writeBoolean(p.cannon);b.writeInt(p.seed);
    },b->new VehicleImpactPayload(b.readVarInt(),b.readUUID(),b.readFloat(),b.readFloat(),b.readFloat(),
            b.readFloat(),b.readFloat(),b.readFloat(),b.readFloat(),b.readBoolean(),b.readInt()));
    public boolean valid() {
        float length=dx*dx+dy*dy+dz*dz;
        return entityId>=0 && Float.isFinite(x)&&Float.isFinite(y)&&Float.isFinite(z)
                && Math.abs(x)<=256 && Math.abs(y)<=256 && Math.abs(z)<=256
                && Float.isFinite(length)&&length>.5F&&length<1.5F && Float.isFinite(radius)&&radius>=.04F&&radius<=.6F;
    }
    @Override public Type<VehicleImpactPayload> type(){return TYPE;}
}
